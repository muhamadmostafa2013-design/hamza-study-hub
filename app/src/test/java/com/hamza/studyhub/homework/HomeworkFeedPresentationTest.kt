package com.hamza.studyhub.homework

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class HomeworkFeedPresentationTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val now = today.atTime(13, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun homework(id: String, due: Int, title: String = "Hausaufgabe • Deutsch"): JSONObject =
        JSONObject()
            .put("externalId", id)
            .put("source", "Untis")
            .put("title", title)
            .put("bigText", "Bitte Seite 12 lesen und Aufgaben beantworten.")
            .put("updateType", "HOMEWORK")
            .put("dueDate", due)
            .put("timestamp", now)

    @Test fun urgentHomeworkComesBeforeLaterAndUndatedItems() {
        val later = homework("later", 20261120)
        val undated = homework("unknown", 0)
        val todayAssignment = homework("today", 20261114)
        val overdue = homework("overdue", 20261113)
        val ordered = HomeworkFeedPresentation.currentHomework(
            listOf(undated, later, todayAssignment, overdue), today, now
        )
        assertEquals(listOf("overdue", "today", "later", "unknown"),
            ordered.map { it.getString("externalId") })
        assertEquals(2, ordered.count { HomeworkFeedPresentation.isUrgent(it, today) })
    }

    @Test fun schoolNoticesAndFinishedHomeworkAreNotInFocus() {
        val notice = homework("notice", 0).put("updateType", "SCHOOL_MESSAGE")
        val finished = homework("done", 20261114).put("progressPercent", 100)
        val active = homework("active", 20261115)
        val visible = HomeworkFeedPresentation.currentHomework(listOf(notice, finished, active), today, now)
        assertEquals("active", visible.single().getString("externalId"))
    }

    @Test fun unknownDueDateIsExplicitlyUnknown() {
        val unknown = homework("no-date", 0)
        assertNull(HomeworkFeedPresentation.dueDate(unknown))
        assertEquals("موعد غير محدد", HomeworkFeedPresentation.deadlineLabel(unknown, today))
        assertFalse(HomeworkFeedPresentation.isUrgent(unknown, today))
        assertEquals("التسليم بكرة", HomeworkFeedPresentation.deadlineLabel(homework("tomorrow", 20261115), today))
    }

    @Test fun previewIsShortWhileFullSchoolTextRemainsIntact() {
        val long = ("Read and answer. ").repeat(30)
        val homework = homework("long", 20261115).put("bigText", long)
        assertTrue(HomeworkFeedPresentation.preview(homework).length <= 140)
        assertEquals(long, homework.getString("bigText"))
    }

    @Test fun picksFirstIncompleteStepNotAnAlreadyFinishedOne() {
        val item = homework("steps", 20261115).put("taskChecklist", JSONArray()
            .put(JSONObject().put("id", "one").put("title", "Finished").put("done", true))
            .put(JSONObject().put("id", "two").put("title", "Next").put("done", false)))
        assertEquals("two", HomeworkFeedPresentation.nextStep(item)?.getString("id"))
    }
}
