package com.hamza.studyhub.teams

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
 * Reliable catch-up monitor for Teams Assignments. Notifications stay as the fast path;
 * this worker is the safety net for assignments that never generated a notification.
 */
class TeamsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        if (!TeamsAuthStore.isConfigured(applicationContext)) return Result.success()

        return try {
            val token = TeamsAuthManager.acquireTokenSilently(applicationContext)
                ?: return Result.success()

            TeamsDeepSyncEngine(
                applicationContext,
                TeamsGraphClient(token)
            ).sync()

            TeamsAuthStore.saveSyncSuccess(applicationContext)
            Result.success()
        } catch (e: Exception) {
            TeamsAuthStore.saveSyncError(
                applicationContext,
                e.message ?: e.javaClass.simpleName
            )
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "teams-deep-periodic-sync"
        private const val IMMEDIATE_NAME = "teams-deep-immediate-sync"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TeamsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<TeamsSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
