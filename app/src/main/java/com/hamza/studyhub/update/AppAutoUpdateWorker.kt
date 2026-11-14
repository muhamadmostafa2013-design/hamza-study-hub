package com.hamza.studyhub.update

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
import java.util.concurrent.TimeUnit

/**
 * Automatically checks and pre-downloads new signed versions on Wi-Fi.
 * Android's install permission/confirmation is never bypassed.
 */
class AppAutoUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result = try {
        if (AppUpdateManager.stageLatest(applicationContext)) {
            AppUpdateManager.notifyReady(applicationContext)
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "hamza-verified-auto-update"
        private const val UNIQUE_IMMEDIATE = "hamza-initial-auto-update"
        private val wifiOnly = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AppAutoUpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(wifiOnly)
                    .build()
            )
            wm.enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AppAutoUpdateWorker>()
                    .setConstraints(wifiOnly)
                    .build()
            )
        }
    }
}
