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
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class WebUntisSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val config = WebUntisConfigStore.load(applicationContext) ?: return Result.success()

        return try {
            val today = LocalDate.now()
            val homeworks = WebUntisClient(config).fetchHomeworks(
                start = today.minusDays(14),
                end = today.plusDays(45)
            )

            mergeIntoFeed(homeworks)
            WebUntisConfigStore.saveSyncSuccess(applicationContext)
            Result.success()
        } catch (e: Exception) {
            WebUntisConfigStore.saveSyncError(
                applicationContext,
                e.message ?: e.javaClass.simpleName
            )
            Result.retry()
        }
    }

    private fun mergeIntoFeed(homeworks: List<WebUntisClient.Homework>) {
        val file = File(applicationContext.filesDir, "school_notifications.jsonl")
        val existing = if (file.exists()) {
            file.readLines().mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }.toMutableList()
        } else {
            mutableListOf()
        }

        val byExternalId = existing
            .mapIndexedNotNull { index, item ->
                item.optString("externalId").takeIf { it.isNotBlank() }?.let { it to index }
            }
            .toMap()
            .toMutableMap()

        homeworks.forEach { hw ->
            val externalId = "untis-homework-${hw.id}"
            val subject = hw.subject.ifBlank { "Untis" }
            val combinedText = buildString {
                if (hw.text.isNotBlank()) append(hw.text)
                if (hw.remark.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(hw.remark)
                }
            }.ifBlank { "واجب بدون وصف نصي في WebUntis" }

            val previousIndex = byExternalId[externalId]
            val previous = previousIndex?.let { existing.getOrNull(it) }
            val previousSignature = previous?.optString("syncSignature").orEmpty()
            val signature = listOf(
                hw.dueDate,
                hw.completed,
                hw.text,
                hw.remark,
                subject,
                hw.teacher
            ).joinToString("|")

            val isChanged = previous == null || previousSignature != signature
            val item = JSONObject().apply {
                put("source", "Untis")
                put("packageName", "com.grupet.web.app")
                put("title", "Hausaufgabe • $subject")
                put("text", combinedText)
                put("bigText", combinedText)
                put("timestamp", if (isChanged) System.currentTimeMillis() else previous?.optLong("timestamp") ?: System.currentTimeMillis())
                put("isNew", if (isChanged) true else previous?.optBoolean("isNew", false) ?: false)
                put("imported", true)
                put("importMethod", "WebUntis Sync")
                put("externalId", externalId)
                put("homeworkId", hw.id)
                put("lessonId", hw.lessonId)
                put("assignedDate", hw.assignedDate)
                put("dueDate", hw.dueDate)
                put("completed", hw.completed)
                put("subject", subject)
                put("teacher", hw.teacher)
                put("syncSignature", signature)
                put("syncChange", if (previous == null) "new" else if (isChanged) "updated" else "unchanged")
            }

            if (previousIndex == null) {
                existing.add(item)
                byExternalId[externalId] = existing.lastIndex
            } else {
                existing[previousIndex] = item
            }
        }

        file.writeText(
            existing.joinToString("\n") { it.toString() } + if (existing.isNotEmpty()) "\n" else ""
        )
    }

    companion object {
        private const val PERIODIC_NAME = "webuntis-periodic-sync"
        private const val IMMEDIATE_NAME = "webuntis-immediate-sync"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<WebUntisSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )

            syncNow(context)
        }

        fun syncNow(context: Context) {
            val immediate = OneTimeWorkRequestBuilder<WebUntisSyncWorker>()
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.REPLACE,
                immediate
            )
        }
    }
}
