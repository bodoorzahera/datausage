package com.datausage.monitor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.datausage.monitor.data.local.db.dao.AppUsageSummary
import com.datausage.monitor.data.repository.LimitRepository
import com.datausage.monitor.data.repository.SessionRepository
import com.datausage.monitor.data.repository.UsageRepository
import com.datausage.monitor.util.FormatUtils
import com.datausage.monitor.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DataMonitorService : Service() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var limitRepository: LimitRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var profileId: Long = -1
    private var sessionId: Long = -1

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
        }

        profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1) ?: -1
        if (profileId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationHelper.buildMonitoringNotification(
            this, "Profile", "Starting..."
        ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_MONITORING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
        }

        serviceScope.launch {
            startMonitoring()
        }

        return START_STICKY
    }

    private suspend fun startMonitoring() {
        val existingSession = sessionRepository.getActiveSession(profileId)
        sessionId = if (existingSession != null) {
            existingSession.sessionId
        } else {
            sessionRepository.startSession(profileId)
        }

        prefs.edit()
            .putLong(KEY_ACTIVE_PROFILE_ID, profileId)
            .putLong(KEY_ACTIVE_SESSION_ID, sessionId)
            .apply()

        // Capture TrafficStats baseline for fallback
        val monitoredApps = usageRepository.getMonitoredAppsList(profileId)
        val uids = monitoredApps.map { it.uid }
        usageRepository.captureBaseline(profileId, uids)

        pollingJob = serviceScope.launch {
            val session = sessionRepository.getById(sessionId) ?: return@launch
            while (isActive) {
                try {
                    usageRepository.pollUsage(sessionId, profileId, session.startTime)

                    val appUsages = usageRepository.getUsageByAppForSession(sessionId)
                    val wifiRx = appUsages.sumOf { it.totalWifiRx }
                    val wifiTx = appUsages.sumOf { it.totalWifiTx }
                    val mobileRx = appUsages.sumOf { it.totalMobileRx }
                    val mobileTx = appUsages.sumOf { it.totalMobileTx }

                    sessionRepository.updateSessionUsage(
                        sessionId, wifiRx, wifiTx, mobileRx, mobileTx
                    )

                    updateNotification(wifiRx + wifiTx, mobileRx + mobileTx)

                    checkLimits(profileId, appUsages)

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(wifiBytes: Long, mobileBytes: Long) {
        val total = wifiBytes + mobileBytes
        val parts = mutableListOf<String>()
        parts.add("Total: ${FormatUtils.formatBytes(total)}")
        if (wifiBytes > 0) parts.add("WiFi: ${FormatUtils.formatBytes(wifiBytes)}")
        if (mobileBytes > 0) parts.add("Mobile: ${FormatUtils.formatBytes(mobileBytes)}")

        val notification = NotificationHelper.buildMonitoringNotification(
            this, "Monitoring", parts.joinToString(" | ")
        ).build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
    }

    private suspend fun checkLimits(profileId: Long, appUsages: List<AppUsageSummary>) {
        val limits = limitRepository.getLimitsForProfileList(profileId)
        val now = System.currentTimeMillis()

        for (limit in limits) {
            val usage = when {
                limit.packageName != null -> {
                    val appUsage = appUsages.find { it.packageName == limit.packageName }
                    appUsage?.totalBytes ?: 0
                }
                else -> {
                    val (from, to) = getPeriodRange(limit.periodType, now)
                    sessionRepository.getTotalUsageInRange(profileId, from, to)
                }
            }

            val percentUsed = if (limit.limitBytes > 0) {
                (usage.toDouble() / limit.limitBytes * 100).toInt()
            } else 0

            if (percentUsed >= 100) {
                NotificationHelper.postExceededNotification(
                    this,
                    "Data Limit Exceeded!",
                    "${limit.packageName ?: "Total"}: ${FormatUtils.formatBytes(usage)} / ${FormatUtils.formatBytes(limit.limitBytes)}",
                    NotificationHelper.NOTIFICATION_ID_EXCEEDED + limit.id.toInt()
                )
            } else if (percentUsed >= limit.warningPercent) {
                NotificationHelper.postWarningNotification(
                    this,
                    "Data Usage Warning",
                    "${limit.packageName ?: "Total"}: ${percentUsed}% of ${FormatUtils.formatBytes(limit.limitBytes)} used",
                    NotificationHelper.NOTIFICATION_ID_WARNING + limit.id.toInt()
                )
            }
        }
    }

    private fun getPeriodRange(periodType: String, now: Long): Pair<Long, Long> {
        return when (periodType) {
            "daily" -> Pair(FormatUtils.startOfDay(now), FormatUtils.endOfDay(now))
            "weekly" -> Pair(FormatUtils.startOfWeek(now), FormatUtils.endOfWeek(now))
            "monthly" -> Pair(FormatUtils.startOfMonth(now), FormatUtils.endOfMonth(now))
            else -> Pair(0, now)
        }
    }

    private fun stopMonitoring() {
        pollingJob?.cancel()
        serviceScope.launch {
            if (sessionId > 0) {
                sessionRepository.stopSession(sessionId)
            }
            prefs.edit()
                .remove(KEY_ACTIVE_PROFILE_ID)
                .remove(KEY_ACTIVE_SESSION_ID)
                .apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.datausage.monitor.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val PREFS_NAME = "monitor_state"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        const val POLL_INTERVAL_MS = 60_000L

        fun start(context: Context, profileId: Long) {
            val intent = Intent(context, DataMonitorService::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_ACTIVE_PROFILE_ID, -1) != -1L
        }

        fun getActiveProfileId(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_ACTIVE_PROFILE_ID, -1)
        }
    }
}
