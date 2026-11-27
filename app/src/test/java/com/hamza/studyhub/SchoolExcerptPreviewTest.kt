package com.hamza.studyhub

import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.hamza.studyhub.imports.SchoolExcerptActivity
import com.hamza.studyhub.sources.SchoolFeedStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class SchoolExcerptPreviewTest {
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    fun arabicReviewKeepsOnlyTheConfirmedEditedExcerpt() = review("ar")

    @Test
    @Config(qualifiers = "de-rDE-ldltr")
    fun germanReviewKeepsOnlyTheConfirmedEditedExcerpt() = review("de")

    private fun review(language: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SchoolFeedStore.edit(context) { it.clear() }
        val source = Robolectric.buildActivity(SourceHubActivity::class.java).create().get()
        source.window.decorView.findViewWithTag<View>("source_school_excerpt").performClick()
        val launch = checkNotNull(shadowOf(source).nextStartedActivity)
        assertEquals(SchoolExcerptActivity::class.java.name, launch.component?.className)
        val screen = Robolectric.buildActivity(SchoolExcerptActivity::class.java, launch).setup().get()
        assertEquals(language, screen.resources.configuration.locales[0].language)
        val content = screen.window.decorView
        content.findViewWithTag<EditText>("school_excerpt_task").setText("Brüche")
        val excerpt = content.findViewWithTag<EditText>("school_excerpt_text")
        excerpt.setText("Aufgabe drei bis Freitag.")
        val preview = content.findViewWithTag<View>("school_excerpt_preview")
        preview.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(SchoolFeedStore.read(context).isEmpty())
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(SchoolFeedStore.read(context).isEmpty())
        excerpt.setText("Aufgabe vier bis Freitag.")
        preview.performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val saved = SchoolFeedStore.read(context).single()
        assertEquals("Aufgabe vier bis Freitag.", saved.getString("text"))
        assertEquals("caregiver_snapshot", saved.getString("sourceMode"))
        assertTrue(saved.getBoolean("needsAttention"))
        assertFalse(saved.has("dueDate"))
        assertTrue(checkNotNull(shadowOf(screen).nextStartedActivity).getBooleanExtra("open_attention", false))
    }
}
