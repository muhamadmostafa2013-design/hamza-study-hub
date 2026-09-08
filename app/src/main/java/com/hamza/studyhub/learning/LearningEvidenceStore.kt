package com.hamza.studyhub.learning

import android.content.Context
import org.json.JSONObject
import java.io.File

class LearningEvidenceStore(private val context: Context) {
    private val attemptsFile: File get() = File(context.filesDir, "student_attempts.jsonl")

    fun appendAttempt(attempt: StudentAttempt) {
        attemptsFile.appendText(attempt.toJson().toString() + "\n")
    }

    fun attempts(): List<StudentAttempt> {
        if (!attemptsFile.exists()) return emptyList()
        return attemptsFile.readLines().mapNotNull { line -> runCatching { JSONObject(line).toAttempt() }.getOrNull() }
    }

    fun nextAttemptNumber(assignmentFingerprint: String): Int =
        attempts().count { it.assignmentFingerprint == assignmentFingerprint } + 1

    private fun StudentAttempt.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("assignmentFingerprint", assignmentFingerprint); put("subject", subject)
        put("bookId", bookId.orEmpty()); put("page", page ?: 0); put("exercise", exercise.orEmpty())
        put("capturedAt", capturedAt); put("imageUri", imageUri.orEmpty()); put("extractedText", extractedText.orEmpty())
        put("attemptNumber", attemptNumber)
    }

    private fun JSONObject.toAttempt(): StudentAttempt = StudentAttempt(
        id = getString("id"),
        assignmentFingerprint = getString("assignmentFingerprint"),
        subject = optString("subject"),
        bookId = optString("bookId").takeIf { it.isNotBlank() },
        page = optInt("page", 0).takeIf { it > 0 },
        exercise = optString("exercise").takeIf { it.isNotBlank() },
        capturedAt = optLong("capturedAt"),
        imageUri = optString("imageUri").takeIf { it.isNotBlank() },
        extractedText = optString("extractedText").takeIf { it.isNotBlank() },
        attemptNumber = optInt("attemptNumber", 1)
    )
}
