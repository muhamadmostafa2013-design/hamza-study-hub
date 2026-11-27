package com.hamza.studyhub.homework

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FamilyAgendaTest {
    private val today = LocalDate.of(2026, 11, 27)
    private fun work(due: Int) = JSONObject().put("updateType", "HOMEWORK").put("dueDate", due)

    @Test fun countsOnlyOpenHomeworkAndPreservesNextOrdering() {
        val recentOverdue = work(20261126)
        val undatedManual = work(0).put("evidenceTrust", "COMMUNITY")
        val input = listOf(
            work(20261128), work(20261127), recentOverdue, work(20260101),
            undatedManual, work(20260230), work(20261127).put("completed", true),
            work(20261127).put("progressPercent", 100),
            work(20261127).put("updateType", "SCHOOL_MESSAGE")
        )
        val result = HomeworkScreenPolicy.familyAgenda(input, today)
        assertSame(recentOverdue, result.next)
        assertEquals(1, result.dueToday)
        assertEquals(2, result.overdue)
        assertEquals(2, result.undated)
        assertEquals("COMMUNITY", undatedManual.getString("evidenceTrust"))
        assertEquals(0, undatedManual.getInt("dueDate"))
    }

    @Test fun emptyAndCompletedFeedsHaveNoNextAssignment() {
        val result = HomeworkScreenPolicy.familyAgenda(
            listOf(work(20261127).put("completed", true)), today
        )
        assertNull(result.next)
        assertEquals(listOf(0, 0, 0), listOf(result.dueToday, result.overdue, result.undated))
    }

    @Test fun usesOneLocalDayAtTheMonthBoundary() {
        val late = work(20261130)
        val first = work(20261201)
        val result = HomeworkScreenPolicy.familyAgenda(listOf(first, late), LocalDate.of(2026, 12, 1))
        assertEquals(1, result.dueToday)
        assertEquals(1, result.overdue)
        assertEquals(0, result.undated)
    }
}
