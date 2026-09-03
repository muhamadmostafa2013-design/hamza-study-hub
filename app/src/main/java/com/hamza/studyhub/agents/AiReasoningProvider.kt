package com.hamza.studyhub.agents

/**
 * Boundary for higher-level AI reasoning.
 *
 * School facts must always come from evidence first. An AI provider may simplify,
 * translate, explain or prioritize those facts, but must never silently invent a
 * deadline, assignment or completion state.
 *
 * The production implementation should call a family-owned backend/edge function
 * so model API credentials are never embedded in the Android APK. On-device
 * reasoning can implement the same contract when supported by the phone.
 */
interface AiReasoningProvider {
    suspend fun reason(request: ReasoningRequest): ReasoningResponse
}

data class ReasoningRequest(
    val purpose: Purpose,
    val evidence: String,
    val language: String = "ar-EG",
    val constraints: List<String> = listOf(
        "Do not invent facts that are absent from the evidence",
        "Mark uncertainty explicitly",
        "Keep school terminology and dates faithful to the source"
    )
)

data class ReasoningResponse(
    val answer: String,
    val confidence: Double,
    val unsupportedClaimsDetected: Boolean = false
)

enum class Purpose {
    EXPLAIN_HOMEWORK,
    CREATE_STUDY_PLAN,
    PRIORITIZE_TASKS,
    SUMMARIZE_PARENT_BRIEF,
    DETECT_REPEATED_ERRORS,
    EXPLAIN_PROGRESS
}
