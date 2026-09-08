package com.hamza.studyhub.learning

import kotlin.math.roundToInt

data class StudentAttempt(
    val id: String,
    val assignmentFingerprint: String,
    val subject: String,
    val bookId: String? = null,
    val page: Int? = null,
    val exercise: String? = null,
    val capturedAt: Long,
    val imageUri: String? = null,
    val extractedText: String? = null,
    val attemptNumber: Int = 1
)

enum class ErrorCategory {
    MISREAD_INSTRUCTION, CONCEPT_GAP, PROCEDURE_GAP, SPELLING, GRAMMAR,
    CALCULATION, OMISSION, CARELESSNESS, PRESENTATION, UNKNOWN
}

data class LearningErrorSignal(
    val attemptId: String,
    val subject: String,
    val skill: String,
    val category: ErrorCategory,
    val evidence: String,
    val confidence: Double,
    val detectedAt: Long,
    val resolvedLater: Boolean = false
)

data class SkillTrend(
    val subject: String,
    val skill: String,
    val observations: Int,
    val unresolved: Int,
    val recurrenceRate: Int,
    val status: TrendStatus,
    val categories: Map<ErrorCategory, Int>
)

enum class TrendStatus { IMPROVING, WATCH, REPEATED_WEAKNESS, INSUFFICIENT_DATA }

object LearningTrendEngine {
    fun summarize(signals: List<LearningErrorSignal>): List<SkillTrend> = signals
        .groupBy { normalize(it.subject) to normalize(it.skill) }
        .map { (_, group) ->
            val latest = group.sortedByDescending { it.detectedAt }
            val unresolved = group.count { !it.resolvedLater }
            val observations = group.size
            val recurrenceRate = if (observations == 0) 0 else ((unresolved.toDouble() / observations) * 100).roundToInt()
            val status = when {
                observations < 2 -> TrendStatus.INSUFFICIENT_DATA
                observations >= 3 && recurrenceRate >= 67 -> TrendStatus.REPEATED_WEAKNESS
                observations >= 2 && recurrenceRate <= 40 -> TrendStatus.IMPROVING
                else -> TrendStatus.WATCH
            }
            SkillTrend(latest.first().subject, latest.first().skill, observations, unresolved,
                recurrenceRate, status, group.groupingBy { it.category }.eachCount())
        }
        .sortedWith(compareByDescending<SkillTrend> { it.status == TrendStatus.REPEATED_WEAKNESS }
            .thenByDescending { it.observations })

    private fun normalize(value: String) = value.lowercase().replace(Regex("\\s+"), " ").trim()
}

interface StudentWorkAnalyzer { suspend fun analyze(input: StudentWorkInput): StudentWorkAnalysis }

data class StudentWorkInput(
    val attempt: StudentAttempt,
    val assignmentInstruction: String,
    val expectedSkill: String?,
    val privateReferenceText: String? = null
)

data class StudentWorkAnalysis(
    val extractedAnswer: String?,
    val signals: List<LearningErrorSignal>,
    val confidence: Double,
    val needsHumanReview: Boolean,
    val noteForParent: String? = null
)
