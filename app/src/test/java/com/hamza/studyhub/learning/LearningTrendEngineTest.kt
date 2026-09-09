package com.hamza.studyhub.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningTrendEngineTest {
    @Test fun marksRepeatedWeaknessOnlyAfterRepeatedEvidence() {
        val signals = (1..3).map { index ->
            LearningErrorSignal("a$index", "Deutsch", "Verbendung", ErrorCategory.GRAMMAR,
                "evidence-$index", 0.9, index.toLong(), resolvedLater = false)
        }
        val trend = LearningTrendEngine.summarize(signals).single()
        assertEquals(TrendStatus.REPEATED_WEAKNESS, trend.status)
        assertEquals(3, trend.observations)
        assertEquals(100, trend.recurrenceRate)
    }

    @Test fun improvingWhenMostObservationsResolvedLater() {
        val signals = listOf(
            LearningErrorSignal("a1", "Mathe", "Division", ErrorCategory.PROCEDURE_GAP, "x", 0.9, 1, true),
            LearningErrorSignal("a2", "Mathe", "Division", ErrorCategory.PROCEDURE_GAP, "x", 0.9, 2, true),
            LearningErrorSignal("a3", "Mathe", "Division", ErrorCategory.CARELESSNESS, "x", 0.8, 3, false)
        )
        val trend = LearningTrendEngine.summarize(signals).single()
        assertEquals(TrendStatus.IMPROVING, trend.status)
        assertEquals(33, trend.recurrenceRate)
    }

    @Test fun oneObservationIsNeverCalledAWeakness() {
        val trend = LearningTrendEngine.summarize(listOf(
            LearningErrorSignal("a1", "HSU", "Begriffe", ErrorCategory.CONCEPT_GAP, "x", 0.8, 1, false)
        )).single()
        assertEquals(TrendStatus.INSUFFICIENT_DATA, trend.status)
    }
}
