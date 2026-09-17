package com.hamza.studyhub

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.hamza.studyhub.books.BookLibraryActivity
import com.hamza.studyhub.learning.StudentWorkCaptureActivity
import com.hamza.studyhub.teams.TeamsConnectionActivity
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class UiButtonSmokeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val feedFile: File get() = File(context.filesDir, "school_notifications.jsonl")

    @Before fun cleanState() {
        listOf("school_notifications.jsonl", "book_library.json", "student_attempts.jsonl").forEach {
            File(context.filesDir, it).delete()
        }
    }

    @After fun cleanup() = cleanState()

    @Test fun everyVisibleButtonAcrossCoreScreensHasClickHandler() {
        val activities = listOf(
            launch(LaunchActivity::class.java),
            launch(HomeActivity::class.java),
            launch(SourceHubActivity::class.java),
            launch(TeamsConnectionActivity::class.java),
            launch(BookLibraryActivity::class.java),
            launch(StudentWorkCaptureActivity::class.java)
        )
        activities.forEach { activity -> assertAllButtonsWired(activity) }
    }

    @Test fun homeworkFeedDynamicButtonsAreAllWired() {
        seedAttentionHomework()
        val activity = launch(MainActivity::class.java)
        val buttons = allButtons(activity.window.decorView)
        assertTrue("Expected dynamic homework actions", buttons.size >= 6)
        buttons.forEach { button ->
            assertTrue("Unwired button: ${button.text} tag=${button.tag}", button.hasOnClickListeners())
        }
        assertTrue(buttons.any { it.tag?.toString()?.startsWith("assignment_capture_") == true })
        assertTrue(buttons.any { it.tag?.toString()?.startsWith("assignment_resolve_") == true })
        assertTrue(buttons.any { it.tag?.toString()?.startsWith("assignment_source_") == true })
    }

    @Test fun resolveAttentionButtonPersistsResolvedState() {
        seedAttentionHomework()
        val activity = launch(MainActivity::class.java)
        val resolve = allButtons(activity.window.decorView)
            .first { it.tag?.toString()?.startsWith("assignment_resolve_") == true }
        assertTrue(resolve.performClick())
        val saved = feedFile.readLines().map { JSONObject(it) }.first()
        assertTrue(saved.optBoolean("attentionResolved", false))
        assertFalse(saved.optBoolean("isNew", true))
    }

    @Test fun homeDashboardNavigationShortcutsCanBeClickedWithoutCrash() {
        val activity = launch(HomeActivity::class.java)
        val root = activity.window.decorView
        listOf("Schultasche", "Hausaufgaben", "Bücher", "Schulquellen").forEach { label ->
            val clickable = clickableAncestorForText(root, label)
            assertNotNull("Expected clickable home shortcut for $label", clickable)
            assertTrue("Shortcut $label has no click handler", clickable!!.hasOnClickListeners())
            assertTrue("Shortcut $label did not accept click", clickable.performClick())
        }
    }

    private fun seedAttentionHomework() {
        feedFile.writeText(JSONObject().apply {
            put("source", "Untis")
            put("title", "Hausaufgabe • Deutsch")
            put("text", "Arbeitsbuch S. 37 Nr. 3")
            put("bigText", "Arbeitsbuch S. 37 Nr. 3")
            put("timestamp", 1_725_800_000_000L)
            put("isNew", true)
            put("imported", true)
            put("importMethod", "WebUntis Auto Sync")
            put("externalId", "test-homework-1")
            put("subject", "Deutsch")
            put("dueDate", 20260910)
            put("needsAttention", true)
            put("attentionReason", "اختبار: يحتاج مراجعة")
            put("attentionResolved", false)
        }.toString() + "\n")
    }

    private fun <T : Activity> launch(clazz: Class<T>): T = Robolectric.buildActivity(clazz).setup().get()

    private fun assertAllButtonsWired(activity: Activity) {
        val buttons = allButtons(activity.window.decorView)
        assertFalse("No buttons found in ${activity.javaClass.simpleName}", buttons.isEmpty())
        buttons.forEach { button ->
            assertTrue("Unwired button in ${activity.javaClass.simpleName}: ${button.text}", button.hasOnClickListeners())
        }
    }

    private fun clickableAncestorForText(root: View, text: String): View? {
        var target: TextView? = null
        fun walk(view: View) {
            if (target != null) return
            if (view is TextView && view.visibility == View.VISIBLE && view.text.toString() == text) {
                target = view
                return
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        var current: View? = target
        while (current != null) {
            if (current.isClickable && current.hasOnClickListeners()) return current
            current = current.parent as? View
        }
        return null
    }

    private fun allButtons(root: View): List<Button> {
        val result = mutableListOf<Button>()
        fun walk(view: View) {
            if (view is Button && view.visibility == View.VISIBLE) result += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return result
    }
}
