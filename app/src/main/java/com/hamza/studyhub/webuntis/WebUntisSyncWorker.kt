package com.hamza.studyhub.webuntis

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hamza.studyhub.agents.AgentOrchestrator
import com.hamza.studyhub.monitor.BackgroundAlert
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class WebUntisSyncWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val config = WebUntisConfigStore.load(applicationContext) ?: return Result.success()
        val today = LocalDate.now()
        val client = WebUntisClient(config)

        // Homework and timetable are deliberately isolated. A timetable API failure must
        // never stop valid homework from reaching the feed (and vice versa).
        val homeworkResult = runCatching {
            client.fetchHomeworks(today.minusDays(14), today.plusDays(45))
        }
        val timetableResult = runCatching {
            client.fetchOwnTimetable(today, today.plusDays(14))
        }

        var summary: SyncSummary? = null
        homeworkResult.onSuccess { homeworks ->
            summary = mergeIntoFeed(homeworks, today)
            WebUntisConfigStore.saveCounts(applicationContext, homeworkCount = homeworks.size)
        }
        timetableResult.onSuccess { timetable ->
            saveTimetable(timetable)
            WebUntisConfigStore.saveCounts(applicationContext, timetableCount = timetable.size)
        }

        val anySuccess = homeworkResult.isSuccess || timetableResult.isSuccess
        val failures = buildList {
            homeworkResult.exceptionOrNull()?.let {
                add("الواجبات: ${it.message ?: it.javaClass.simpleName}")
            }
            timetableResult.exceptionOrNull()?.let {
                add("الجدول: ${it.message ?: it.javaClass.simpleName}")
            }
        }

        if (anySuccess) {
            WebUntisConfigStore.saveSyncSuccess(applicationContext)
            if (failures.isNotEmpty()) {
                WebUntisConfigStore.saveSyncError(applicationContext, failures.joinToString(" • "))
            }
        } else {
            WebUntisConfigStore.saveSyncError(
                applicationContext,
                failures.joinToString(" • ").ifBlank { "تعذر مزامنة WebUntis" }
            )
        }

        summary?.let { s ->
            if (s.newCount > 0 || s.updatedCount > 0) {
                val parts = buildList {
                    if (s.newCount > 0) add("${s.newCount} واجب جديد")
                    if (s.updatedCount > 0) add("${s.updatedCount} تعديل")
                    if (s.attentionCount > 0) add("${s.attentionCount} يحتاج انتباه")
                }
                BackgroundAlert.notify(
                    applicationContext,
                    "Hamza Study Hub • تحديث من Untis",
                    parts.joinToString(" • "),
                    4101
                )
            }
        }

        return if (anySuccess) Result.success() else Result.retry()
    }

    private fun saveTimetable(lessons: List<WebUntisClient.TimetableEntry>) {
        val rows = JSONArray()
        lessons.forEach { lesson ->
            rows.put(JSONObject().apply {
                put("id", lesson.id); put("date", lesson.date); put("startTime", lesson.startTime); put("endTime", lesson.endTime)
                put("subject", lesson.subject); put("room", lesson.room); put("teacher", lesson.teacher); put("cancelled", lesson.cancelled)
                put("substitutionText", lesson.substitutionText)
            })
        }
        File(applicationContext.filesDir, "webuntis_timetable.json").writeText(JSONObject().apply {
            put("syncedAt", System.currentTimeMillis()); put("lessons", rows)
        }.toString())
    }

    private data class SyncSummary(val newCount: Int, val updatedCount: Int, val attentionCount: Int)

    private fun mergeIntoFeed(homeworks: List<WebUntisClient.Homework>, today: LocalDate): SyncSummary {
        val file = File(applicationContext.filesDir, "school_notifications.jsonl")
        val existing = if (file.exists()) file.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toMutableList() else mutableListOf()
        val byExternalId = existing.mapIndexedNotNull { index, item -> item.optString("externalId").takeIf { it.isNotBlank() }?.let { it to index } }.toMap().toMutableMap()
        var newCount = 0; var updatedCount = 0; var attentionCount = 0
        homeworks.forEach { hw ->
            val externalId = "untis-homework-${hw.id}"; val subject = hw.subject.ifBlank { "Untis" }
            val combinedText = buildString { if (hw.text.isNotBlank()) append(hw.text); if (hw.remark.isNotBlank()) { if (isNotEmpty()) append("\n"); append(hw.remark) } }.ifBlank { "واجب موجود في WebUntis بدون وصف نصي" }
            val previousIndex = byExternalId[externalId]; val previous = previousIndex?.let { existing.getOrNull(it) }
            val signature = listOf(hw.dueDate, hw.completed, hw.text, hw.remark, subject, hw.teacher).joinToString("|")
            val isChanged = previous == null || previous.optString("syncSignature") != signature
            val syncChange = when { previous == null -> "new"; isChanged -> "updated"; else -> "unchanged" }
            if (syncChange == "new") newCount++; if (syncChange == "updated") updatedCount++
            val dueDate = parseUntisDate(hw.dueDate); val daysUntilDue = dueDate?.let { ChronoUnit.DAYS.between(today, it) }; val missingDetails = hw.text.isBlank() && hw.remark.isBlank()
            val attentionReason = when { hw.completed -> ""; missingDetails -> "تفاصيل الواجب غير موجودة في WebUntis؛ افتح المصدر لو احتجنا المطلوب كاملًا."; daysUntilDue != null && daysUntilDue < 0 -> "الواجب متأخر عن موعد التسليم."; daysUntilDue != null && daysUntilDue <= 1 -> "موعد التسليم قريب جدًا."; else -> "" }
            val needsAttention = attentionReason.isNotBlank(); val previousResolved = previous?.optBoolean("attentionResolved", false) ?: false; val attentionResolved = if (isChanged && needsAttention) false else previousResolved
            val rawItem = JSONObject().apply {
                put("source", "Untis"); put("packageName", "com.grupet.web.app"); put("title", "Hausaufgabe • $subject"); put("text", combinedText); put("bigText", combinedText)
                put("timestamp", if (isChanged) System.currentTimeMillis() else previous?.optLong("timestamp") ?: System.currentTimeMillis()); put("isNew", if (isChanged) true else previous?.optBoolean("isNew", false) ?: false)
                put("imported", true); put("importMethod", "WebUntis Auto Sync"); put("externalId", externalId); put("homeworkId", hw.id); put("lessonId", hw.lessonId); put("assignedDate", hw.assignedDate); put("dueDate", hw.dueDate)
                put("completed", hw.completed); put("subject", subject); put("teacher", hw.teacher); put("syncSignature", signature); put("syncChange", syncChange); put("needsAttention", needsAttention); put("attentionReason", attentionReason); put("attentionResolved", attentionResolved)
            }
            val item = AgentOrchestrator.enrich(rawItem); if (item.optBoolean("needsAttention", false) && !attentionResolved) attentionCount++
            if (previousIndex == null) { existing.add(item); byExternalId[externalId] = existing.lastIndex } else existing[previousIndex] = item
        }
        file.writeText(existing.joinToString("\n") { it.toString() } + if (existing.isNotEmpty()) "\n" else "")
        return SyncSummary(newCount, updatedCount, attentionCount)
    }

    private fun parseUntisDate(value: Int): LocalDate? = if (value <= 0) null else runCatching { LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

    companion object {
        private const val PERIODIC_NAME = "webuntis-periodic-sync"; private const val IMMEDIATE_NAME = "webuntis-immediate-sync"
        private val networkConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun schedule(context: Context) { val periodic = PeriodicWorkRequestBuilder<WebUntisSyncWorker>(15, TimeUnit.MINUTES).setConstraints(networkConstraints).build(); WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic); syncNow(context) }
        fun syncNow(context: Context) { val immediate = OneTimeWorkRequestBuilder<WebUntisSyncWorker>().setConstraints(networkConstraints).build(); WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, immediate) }
    }
}
