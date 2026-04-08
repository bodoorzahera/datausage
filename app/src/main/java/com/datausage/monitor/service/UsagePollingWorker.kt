package com.datausage.monitor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UsagePollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Safety net: if the service should be running but isn't, restart it
        val prefs = applicationContext.getSharedPreferences(
            DataMonitorService.PREFS_NAME, Context.MODE_PRIVATE
        )
        val activeProfileId = prefs.getLong(DataMonitorService.KEY_ACTIVE_PROFILE_ID, -1)

        if (activeProfileId != -1L) {
            try {
                DataMonitorService.start(applicationContext, activeProfileId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "usage_polling_safety_net"

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsagePollingWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
