package com.hamza.studyhub.homework

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class HomeworkScreenPolicyTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun item(id: String, due: Int, title: String = "Hausaufgabe • Deutsch") =
        JSONObject().put("externalId", id).put("title", title)
            .put("bigText", "Arbeitsbuch Seite 15 Aufgabe 3 bearbeiten")
            .put("updateType", "HOMEWORK").put("dueDate", due).put("timestamp", 10L)

    @Test fun urgentAssignmentsAppearBeforeUndatedAndOldHistory() {
        val future = item("future", 20261120)
        val overdue = item("overdue", 20261113)
        val todayWork = item("today", 20261114)
        val unknown = item("unknown", 0)
        val old = item("old", 20260910)
        val visible = HomeworkScreenPolicy.visible(
            listOf(unknown, old, future, todayWork, overdue),
            HomeworkScreenPolicy.Filter.TO_DO, today
        )
        assertEquals(listOf("overdue", "today", "future", "old", "unknown"),
            visible.map { it.optString("externalId") })
    }

    @Test fun finishedHomeworkAndSchoolMessagesDoNotClutterToDo() {
        val done = item("done", 20261114).put("progressPercent", 100)
        val notice = item("notice", 20261114, "Parent meeting")
            .put("updateType", "SCHOOL_MESSAGE")
        val active = item("active", 20261115)
        assertEquals(listOf("active"), HomeworkScreenPolicy.visible(
            listOf(done, notice, active), HomeworkScreenPolicy.Filter.TO_DO, today
        ).map { it.optString("externalId") })
        assertEquals(3, HomeworkScreenPolicy.visible(
            listOf(done, notice, active), HomeworkScreenPolicy.Filter.ALL, today
        ).size)
    }

    @Test fun reviewShowsOnlyUnresolvedItemsWithoutTreatingAsCompleted() {
        val flagged = item("flag", 20261115).put("needsAttention", true)
        val resolved = item("resolved", 20261115).put("needsAttention", true)
            .put("attentionResolved", true)
        val done = item("done", 20261115).put("needsAttention", true)
            .put("progressPercent", 100)
        val selected = HomeworkScreenPolicy.visible(
            listOf(flagged, resolved, done), HomeworkScreenPolicy.Filter.REVIEW, today
        )
        assertEquals(1, selected.size)
        assertEquals("flag", selected.single().optString("externalId"))
        assertFalse(HomeworkScreenPolicy.completed(flagged))
    }

    @Test fun dueLabelsAreGroundedAndNoMissingDateIsInvented() {
        assertEquals("التسليم اليوم", HomeworkScreenPolicy.dueStatus(20261114, today).text)
        assertEquals("التسليم غدًا", HomeworkScreenPolicy.dueStatus(20261115, today).text)
        assertEquals("الموعد غير محدد", HomeworkScreenPolicy.dueStatus(0, today).text)
        assertEquals("موعد قديم • تأكد", HomeworkScreenPolicy.dueStatus(20260910, today).text)
        assertTrue(HomeworkScreenPolicy.dueStatus(20261114, today).date != null)
    }

    @Test fun previewIsShortWithoutChangingOriginalInstructions() {
        val original = "Read workbook page 30 and answer questions 1, 2 and 3. " +
            "Then upload the written answers to your class teacher."
        val short = HomeworkScreenPolicy.preview(original, 45)
        assertTrue(short.endsWith("…"))
        assertTrue(short.length <= 46)
        assertTrue(original.contains("upload"))
    }
}
