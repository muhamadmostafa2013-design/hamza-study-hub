package com.hamza.studyhub.agents

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StudySupervisorAgentTest {
    private val engine = StudySupervisorEngine { LocalDate.of(2026, 9, 16) }

    @Test fun officialDueTodayRanksAheadOfLaterHomework() {
        val later = homework(
            id = "later",
            title = "Mathe",
            dueDate = 20260920,
            trust = "OFFICIAL"
        )
        val today = homework(
            id = "today",
            title = "Deutsch",
            dueDate = 20260916,
            trust = "OFFICIAL"
        )

        val report = engine.build(listOf(later, today), emptyList())

        assertEquals(SupervisorStatus.READY, report.status)
        assertEquals("Deutsch", report.actions.first().title)
        assertFalse(report.actions.first().requiresParentConfirmation)
    }

    @Test fun communityEvidenceNeverBecomesUnverifiedFact() {
        val whatsapp = homework(
            id = "wa-1",
            title = "HSU",
            dueDate = 20260917,
            trust = "COMMUNITY",
            source = "WhatsApp"
        )

        val report = engine.build(listOf(whatsapp), emptyList())

        assertEquals(SupervisorStatus.NEEDS_PARENT_CONFIRMATION, report.status)
        assertTrue(report.actions.single().requiresParentConfirmation)
        assertTrue(report.confirmations.isNotEmpty())
    }

    @Test fun completedHomeworkIsExcludedFromPlan() {
        val done = homework(
            id = "done",
            title = "Biologie",
            dueDate = 20260916,
            trust = "OFFICIAL"
        ).apply { put("completed", true) }

        val report = engine.build(listOf(done), emptyList())

        assertEquals(SupervisorStatus.CLEAR, report.status)
        assertTrue(report.actions.isEmpty())
    }

    @Test fun missingDeadlineRequiresParentConfirmationInsteadOfInventingDate() {
        val item = homework(
            id = "no-date",
            title = "Musik",
            dueDate = 0,
            trust = "OFFICIAL"
        )

        val report = engine.build(listOf(item), emptyList())

        assertTrue(report.actions.single().requiresParentConfirmation)
        assertEquals(null, report.actions.single().dueDate)
    }

    private fun homework(
        id: String,
        title: String,
        dueDate: Int,
        trust: String,
        source: String = "Untis"
    ) = JSONObject().apply {
        put("externalId", id)
        put("contentFingerprint", "fp-$id")
        put("title", title)
        put("subject", title)
        put("source", source)
        put("text", "Read page 10 and answer the questions")
        put("bigText", "Read page 10 and answer the questions")
        put("dueDate", dueDate)
        put("completed", false)
        put("isNew", true)
        put("needsAttention", false)
        put("attentionResolved", false)
        put("needsFullInstructions", false)
        put("evidenceTrust", trust)
    }
}
