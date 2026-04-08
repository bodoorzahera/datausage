package com.datausage.monitor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.datausage.monitor.data.repository.LimitRepository
import com.datausage.monitor.data.repository.SessionRepository
import com.datausage.monitor.data.repository.UsageRepository
import com.datausage.monitor.util.FormatUtils
import com.datausage.monitor.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LimitCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val limitRepository: LimitRepository,
    private val sessionRepository: SessionRepository,
    private val usageRepository: UsageRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(
            DataMonitorService.PREFS_NAME, Context.MODE_PRIVATE
        )
        val activeProfileId = prefs.getLong(DataMonitorService.KEY_ACTIVE_PROFILE_ID, -1)
        if (activeProfileId == -1L) return Result.success()

        val limits = limitRepository.getLimitsForProfileList(activeProfileId)
        val now = System.currentTimeMillis()

        for (limit in limits) {
            val usage = when {
                limit.packageName != null -> {
                    val (from, to) = getPeriodRange(limit.periodType, now)
                    val appUsage = usageRepository.getUsageForApp(
                        activeProfileId, limit.packageName, from, to
                    )
                    (appUsage?.totalRx ?: 0) + (appUsage?.totalTx ?: 0)
                }
                else -> {
                    val (from, to) = getPeriodRange(limit.periodType, now)
                    sessionRepository.getTotalUsageInRange(activeProfileId, from, to)
                }
            }

            val percentUsed = if (limit.limitBytes > 0) {
                (usage.toDouble() / limit.limitBytes * 100).toInt()
            } else 0

            if (percentUsed >= 100) {
                NotificationHelper.postExceededNotification(
                    applicationContext,
                    "Data Limit Exceeded!",
                    "${limit.packageName ?: "Total"}: ${FormatUtils.formatBytes(usage)} / ${FormatUtils.formatBytes(limit.limitBytes)}",
                    NotificationHelper.NOTIFICATION_ID_EXCEEDED + limit.id.toInt()
                )
            } else if (percentUsed >= limit.warningPercent) {
                NotificationHelper.postWarningNotification(
                    applicationContext,
                    "Data Usage Warning",
                    "${limit.packageName ?: "Total"}: ${percentUsed}% of limit used",
                    NotificationHelper.NOTIFICATION_ID_WARNING + limit.id.toInt()
                )
            }
        }

        return Result.success()
    }

    private fun getPeriodRange(periodType: String, now: Long): Pair<Long, Long> {
        return when (periodType) {
            "daily" -> Pair(FormatUtils.startOfDay(now), FormatUtils.endOfDay(now))
            "weekly" -> Pair(FormatUtils.startOfWeek(now), FormatUtils.endOfWeek(now))
            "monthly" -> Pair(FormatUtils.startOfMonth(now), FormatUtils.endOfMonth(now))
            else -> Pair(0, now)
        }
    }

    companion object {
        private const val WORK_NAME = "limit_check"

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<LimitCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
