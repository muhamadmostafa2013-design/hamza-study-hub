package com.hamza.studyhub.agents

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SchoolFeedProjectorTest {
    private val today = LocalDate.of(2026, 9, 16)
    private val now = today.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun oldOverdueHomeworkIsKeptInHistoryButExcludedFromCurrentFeed() {
        val old = homework("old", 20260910, now - 6L * 24 * 60 * 60 * 1000)

        val result = SchoolFeedProjector.current(listOf(old), today, now)

        assertTrue(result.isEmpty())
    }

    @Test fun recentOverdueHomeworkStillAppearsDuringGraceWindow() {
        val recent = homework("recent", 20260915, now - 24L * 60 * 60 * 1000)

        val result = SchoolFeedProjector.current(listOf(recent), today, now)

        assertEquals("recent", result.single().optString("externalId"))
    }

    @Test fun oldHomeworkChangedNowReturnsToCurrentFeed() {
        val changed = homework("changed", 20260910, now - 60L * 60 * 1000).apply {
            put("syncChange", "updated")
        }

        val result = SchoolFeedProjector.current(listOf(changed), today, now)

        assertEquals("changed", result.single().optString("externalId"))
    }

    @Test fun duplicateAssignmentUsesNewestSavedState() {
        val older = homework("same", 20260917, now - 2L * 60 * 60 * 1000)
        val newer = homework("same", 20260918, now - 60L * 60 * 1000)

        val result = SchoolFeedProjector.current(listOf(older, newer), today, now)

        assertEquals(1, result.size)
        assertEquals(20260918, result.single().optInt("dueDate"))
    }

    @Test fun undatedOldNotificationDoesNotStayNewForever() {
        val old = homework("undated", 0, now - 10L * 24 * 60 * 60 * 1000)

        val result = SchoolFeedProjector.current(listOf(old), today, now)

        assertTrue(result.isEmpty())
    }

    private fun homework(id: String, dueDate: Int, timestamp: Long) = JSONObject().apply {
        put("externalId", id)
        put("title", "Hausaufgabe")
        put("dueDate", dueDate)
        put("timestamp", timestamp)
        put("completed", false)
        put("isNew", true)
        put("syncChange", "unchanged")
    }
}
