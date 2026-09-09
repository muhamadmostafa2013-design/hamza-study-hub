package com.hamza.studyhub.teams

import android.content.Context
import com.hamza.studyhub.agents.AgentOrchestrator
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Deep Teams assignment synchronization independent from Android notifications.
 *
 * NotificationListener remains the fast signal path. This engine is the reliable
 * catch-up path: it queries Microsoft Graph, imports old/current assignments,
 * re-fetches changed assignment details/resources, and deduplicates by Graph IDs.
 */
class TeamsDeepSyncEngine(
    private val context: Context,
    private val client: TeamsGraphClient
) {
    data class SyncSummary(
        val discovered: Int,
        val updated: Int,
        val unchanged: Int,
        val attention: Int
    )

    fun sync(): SyncSummary {
        val dataFile = File(context.filesDir, "school_notifications.jsonl")
        val existing = if (dataFile.exists()) {
            dataFile.readLines().mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }.toMutableList()
        } else {
            mutableListOf()
        }

        val byExternalId = existing.mapIndexedNotNull { index, item ->
            item.optString("externalId").takeIf { it.isNotBlank() }?.let { it to index }
        }.toMap().toMutableMap()

        var discovered = 0
        var updated = 0
        var unchanged = 0
        var attention = 0
        val today = LocalDate.now()

        client.listMyAssignments().forEach { summary ->
            val externalId = "teams-assignment-${summary.classId}-${summary.id}"
            val oldIndex = byExternalId[externalId]
            val old = oldIndex?.let(existing::getOrNull)
            val oldModified = old?.optString("graphLastModified").orEmpty()
            val currentModified = summary.lastModifiedDateTime.orEmpty()
            val changed = old == null || currentModified.isBlank() || currentModified != oldModified

            if (!changed && old != null) {
                unchanged++
                if (old.optBoolean("needsAttention", false) && !old.optBoolean("attentionResolved", false)) {
                    attention++
                }
                return@forEach
            }

            val detail = client.getAssignment(summary.classId, summary.id)
            val body = buildBody(detail)
            val dueDate = toLocalDate(detail.dueDateTime)
            val daysUntilDue = dueDate?.let { ChronoUnit.DAYS.between(today, it) }
            val missingInstructions = detail.instructionsHtml.isNullOrBlank() && detail.resourceLabels.isEmpty()

            // New/updated is represented by syncChange + isNew. "Needs attention" is reserved
            // for something a parent should actually act on, so the dashboard stays meaningful.
            val reason = when {
                missingInstructions -> "Teams سجّل الواجب لكن التعليمات/المرفقات غير ظاهرة في Graph؛ يحتاج فتح المصدر."
                daysUntilDue != null && daysUntilDue < 0 -> "الواجب متأخر عن موعد التسليم."
                daysUntilDue != null && daysUntilDue <= 1 -> "موعد تسليم واجب Teams قريب جدًا."
                else -> ""
            }
            val needsAttention = reason.isNotBlank()
            val previousResolved = old?.optBoolean("attentionResolved", false) ?: false
            val attentionResolved = if (changed && needsAttention) false else previousResolved

            val raw = JSONObject().apply {
                put("source", "Teams")
                put("packageName", "com.microsoft.teams")
                put("title", detail.displayName)
                put("text", body)
                put("bigText", body)
                put("timestamp", System.currentTimeMillis())
                put("isNew", true)
                put("imported", true)
                put("importMethod", "Microsoft Graph Deep Sync")
                put("externalId", externalId)
                put("teamsAssignmentId", detail.id)
                put("teamsClassId", detail.classId)
                put("dueDate", dueDate?.let(::toBasicDateInt) ?: 0)
                put("dueDateTime", detail.dueDateTime.orEmpty())
                put("createdDateTime", detail.createdDateTime.orEmpty())
                put("graphLastModified", detail.lastModifiedDateTime.orEmpty())
                put("assignmentStatus", detail.status.orEmpty())
                put("sourceUrl", detail.webUrl.orEmpty())
                put("syncChange", if (old == null) "new" else "updated")
                put("needsAttention", needsAttention)
                put("attentionReason", reason)
                put("attentionResolved", attentionResolved)
            }

            val enriched = AgentOrchestrator.enrich(raw)
            if (enriched.optBoolean("needsAttention", false) && !attentionResolved) attention++

            if (oldIndex == null) {
                existing.add(enriched)
                byExternalId[externalId] = existing.lastIndex
                discovered++
            } else {
                existing[oldIndex] = enriched
                updated++
            }
        }

        dataFile.writeText(
            existing.joinToString("\n") { it.toString() } + if (existing.isNotEmpty()) "\n" else ""
        )
        return SyncSummary(discovered, updated, unchanged, attention)
    }

    private fun buildBody(detail: TeamsGraphClient.AssignmentDetail): String {
        val instructions = stripHtml(detail.instructionsHtml.orEmpty()).trim()
        return buildString {
            if (instructions.isNotBlank()) append(instructions)
            if (detail.resourceLabels.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("المرفقات/المصادر:\n")
                detail.resourceLabels.forEach { append("• ").append(it).append('\n') }
            }
        }.trim().ifBlank { "واجب موجود في Teams بدون تفاصيل نصية متاحة حاليًا." }
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")

    private fun toLocalDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
    }

    private fun toBasicDateInt(date: LocalDate): Int =
        date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth
}
