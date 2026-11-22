package com.hamza.studyhub.imports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hamza.studyhub.sources.SchoolFeedStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ManualHomeworkImportTest {
    @Test fun rejectsBlankAndLongInputAndTrimsSnippet() {
        assertNull(ManualHomeworkImport.validatedSnippet(" \n "))
        assertNull(ManualHomeworkImport.validatedSnippet("x".repeat(2001)))
        assertEquals(2000, ManualHomeworkImport.validatedSnippet("x".repeat(2000))?.length)
        assertEquals("حل صفحة ١٣", ManualHomeworkImport.validatedSnippet("  حل صفحة ١٣\n"))
    }

    @Test fun savesSelectedTextLocallyWithoutACalculatedDueDateOrVerifiedClaim() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snippet = "حل صفحة ١٣"
        val item = ManualHomeworkImport.item(snippet, 123L)
        SchoolFeedStore.append(context, item)
        val saved = SchoolFeedStore.read(context).single { it.optString("id") == item.getString("id") }
        assertEquals(snippet, saved.optString("text"))
        assertEquals("HOMEWORK", saved.optString("updateType"))
        assertEquals("WhatsApp", saved.optString("source"))
        assertEquals("Manual WhatsApp copy/paste", saved.optString("importMethod"))
        assertEquals("COMMUNITY", saved.optString("evidenceTrust"))
        assertEquals("NEEDS_CONFIRMATION", saved.optString("verifierStatus"))
        assertFalse(saved.optBoolean("verified"))
        assertTrue(saved.optBoolean("needsAttention"))
        assertFalse(saved.has("dueDate"))
    }
}
