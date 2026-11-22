package com.hamza.studyhub.learning

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

class LearningEvidenceStore(private val context: Context) {
    private val attemptsFile get() = AtomicFile(File(context.filesDir, "student_attempts.jsonl"))

    fun saveNewAttempt(attempt: StudentAttempt): StudentAttempt = synchronized(lock) {
        val rows = readRows()
        val ordinal = rows.count { it.optString("assignmentFingerprint") == attempt.assignmentFingerprint } + 1
        val numbered = attempt.copy(attemptNumber = ordinal)
        rows += numbered.toJson()
        writeRows(rows)
        numbered
    }

    fun updateExtractedText(attemptId: String, text: String): Boolean = synchronized(lock) {
        val rows = readRows()
        val attempt = rows.firstOrNull { it.optString("id") == attemptId } ?: return@synchronized false
        attempt.put("extractedText", text)
        writeRows(rows)
        true
    }

    fun attempts(): List<StudentAttempt> = synchronized(lock) {
        readRows().mapNotNull { row -> runCatching { row.toAttempt() }.getOrNull() }
    }

    private fun readRows(): MutableList<JSONObject> = try {
        attemptsFile.openRead().bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }.toMutableList()
        }
    } catch (_: FileNotFoundException) {
        mutableListOf()
    }

    private fun writeRows(rows: List<JSONObject>) {
        val file = attemptsFile
        val output = file.startWrite()
        try {
            rows.forEach { row -> output.write((row.toString() + "\n").toByteArray(Charsets.UTF_8)) }
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private companion object { val lock = Any() }

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
