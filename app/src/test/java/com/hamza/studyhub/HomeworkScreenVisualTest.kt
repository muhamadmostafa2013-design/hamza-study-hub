package com.hamza.studyhub

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Visual hierarchy / progressive-disclosure smoke checks.
 * A real device screenshot and Arabic RTL manual review are still release gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class HomeworkScreenVisualTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val feed: File get() = File(context.filesDir, "school_notifications.jsonl")
    private val today: LocalDate get() = LocalDate.now()
    private fun date(offsetDays: Long): Int =
        today.plusDays(offsetDays).format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    @Before fun reset() {
        listOf("school_notifications.jsonl", "school_notifications.jsonl.bak",
            "school_notifications.jsonl.new").forEach { File(context.filesDir, it).delete() }
    }
    @After fun cleanup() = reset()

    @Test fun homeworkCardShowsPriorityAndOnlyFirstActionsBeforeExpansion() {
        val instructions = "Read your homework carefully, answer all questions in the workbook, " +
            "and use the attached book page. Make sure you upload your written answers when complete. " +
            "Please also revise the new vocabulary words from class."
        write(homework(101L, date(1), instructions = instructions, steps = 4)
            .put("needsAttention", true).put("attentionReason", "Confirm the attached worksheet"))
        val root = launch().window.decorView

        assertEquals("Deutsch", tagged<TextView>(root, "assignment_title_101").text.toString())
        assertNotNull(tagged<View>(root, "assignment_due_101"))
        assertTrue(tagged<TextView>(root, "assignment_summary_101").text.length < instructions.length)
        assertTrue(tagged<Button>(root, "assignment_source_101").isShown)
        assertEquals(View.GONE, tagged<View>(root, "assignment_details_panel_101").visibility)
        assertEquals(2, descendants(root).filterIsInstance<CheckBox>()
            .count { it.isShown && it.tag?.toString()?.startsWith("assignment_task_101") == true })
        assertFalse(tagged<Button>(root, "assignment_resolve_101").isShown)

        assertTrue(tagged<Button>(root, "assignment_details_101").performClick())
        assertEquals(View.VISIBLE, tagged<View>(root, "assignment_details_panel_101").visibility)
        assertEquals(4, descendants(root).filterIsInstance<CheckBox>()
            .count { it.isShown && it.tag?.toString()?.startsWith("assignment_task_101") == true })
        assertTrue(tagged<Button>(root, "assignment_resolve_101").isShown)
    }

    @Test fun defaultScreenOnlyShowsOpenHomeworkAndTabsRevealHistory() {
        val due = homework(201L, date(1))
        val completed = homework(202L, date(0)).put("progressPercent", 100)
        val notice = homework(203L, 0, steps = 0)
            .put("title", "Reminder: parents meeting").put("updateType", "SCHOOL_MESSAGE")
        write(due, completed, notice)
        val root = launch().window.decorView

        assertEquals(1, cards(root).size)
        assertNotNull(tagged<View>(root, "homework_card_201"))
        assertNull(root.findViewWithTag<View>("homework_card_202"))
        assertNull(root.findViewWithTag<View>("homework_card_203"))
        assertTrue(tagged<TextView>(root, "homework_open_count").text.toString().startsWith("1"))

        assertTrue(tagged<Button>(root, "feed_all").performClick())
        assertEquals(3, cards(root).size)
    }

    @Test fun displayRanksUrgentHomeworkAheadOfFarFutureAndOldHistory() {
        write(
            homework(301L, date(17)),
            homework(302L, date(0)),
            homework(303L, date(-80))
        )
        val root = launch().window.decorView
        // Urgent appears first even though its data was not the newest.
        assertEquals("homework_card_302", cards(root).first().tag.toString())
        assertEquals("homework_card_303", cards(root).last().tag.toString())
    }

    @Test fun compactPhoneWidthKeepsPriorityBadgeAndTitleWithinTheCard() {
        write(homework(351L, date(0)))
        val root = launch().window.decorView
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (760 * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)

        val card = tagged<View>(root, "homework_card_351")
        val badge = tagged<View>(root, "assignment_due_351")
        val title = tagged<TextView>(root, "assignment_title_351")
        assertTrue("Card should fit mobile width", card.width > 0 && card.width <= width)
        assertTrue("Date badge must be laid out", badge.width > 0)
        assertTrue("Assignment title must retain space", title.width > 0)
        val cardLocation = IntArray(2)
        val badgeLocation = IntArray(2)
        card.getLocationOnScreen(cardLocation)
        badge.getLocationOnScreen(badgeLocation)
        assertTrue("Badge should stay within card bounds",
            badgeLocation[0] >= cardLocation[0] &&
            badgeLocation[0] + badge.width <= cardLocation[0] + card.width)
    }

    @Test fun schoolSourceUtilitiesAreHiddenUntilRequested() {
        write(homework(401L, date(1)))
        val root = launch().window.decorView
        assertFalse(tagged<Button>(root, "feed_sources").isShown)
        assertFalse(tagged<Button>(root, "feed_books").isShown)
        assertTrue(tagged<Button>(root, "feed_manage_sources").performClick())
        assertTrue(tagged<Button>(root, "feed_sources").isShown)
        assertTrue(tagged<Button>(root, "feed_books").isShown)
    }

    @Test fun filtersAndChecklistMeetMinimumTouchTargetGuidance() {
        write(homework(501L, date(1), steps = 2))
        val activity = launch()
        val root = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        val controls = listOf("feed_todo", "feed_attention", "feed_all",
            "feed_manage_sources", "assignment_source_501", "assignment_details_501")
        controls.forEach { tag ->
            assertTrue("Button $tag should be >=48dp",
                tagged<Button>(root, tag).minimumHeight >= (48 * density).toInt())
        }
        val checklist = tagged<CheckBox>(root, "assignment_task_501_0")
        assertTrue("Checkbox touch target ${checklist.minimumHeight}px must be at least ${(48 * density).toInt()}px",
            checklist.minimumHeight >= (48 * density).toInt())
    }

    private fun write(vararg items: JSONObject) {
        feed.writeText(items.joinToString("\n") { it.toString() } + "\n")
    }

    private fun homework(id: Long, due: Int, instructions: String =
        "Arbeitsbuch Seite 20 Aufgabe 3 bearbeiten", steps: Int = 1): JSONObject {
        val tasks = JSONArray()
        repeat(steps) { index ->
            tasks.put(JSONObject().put("id", "step-$index")
                .put("title", "Complete step ${index + 1}")
                .put("done", false))
        }
        return JSONObject()
            .put("agentPipelineVersion", "3.1")
            .put("externalId", "visual-test-$id")
            .put("timestamp", id)
            .put("source", "Untis")
            .put("title", "Hausaufgabe • Deutsch")
            .put("subject", "Deutsch")
            .put("text", instructions)
            .put("bigText", instructions)
            .put("updateType", "HOMEWORK")
            .put("taskChecklist", tasks)
            .put("progressPercent", 0)
            .put("dueDate", due)
            .put("importMethod", "WebUntis Auto Sync")
            .put("needsAttention", false)
            .put("attentionResolved", false)
            .put("isNew", true)
    }

    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun cards(root: View): List<View> =
        descendants(root).filter { it.tag?.toString()?.startsWith("homework_card_") == true }

    private inline fun <reified T : View> tagged(root: View, tag: String): T {
        val found = root.findViewWithTag<View>(tag)
        assertNotNull("Missing $tag", found)
        return found as T
    }

    private fun descendants(view: View): List<View> {
        val result = mutableListOf<View>()
        fun visit(v: View) {
            result += v
            if (v is ViewGroup) repeat(v.childCount) { visit(v.getChildAt(it)) }
        }
        visit(view)
        return result
    }
}
