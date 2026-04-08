package com.datausage.monitor.data.repository

import com.datausage.monitor.data.local.db.dao.AppUsageDao
import com.datausage.monitor.data.local.db.dao.AppUsageSummary
import com.datausage.monitor.data.local.db.dao.MonitoredAppDao
import com.datausage.monitor.data.local.db.entity.AppUsageEntity
import com.datausage.monitor.data.local.db.entity.MonitoredAppEntity
import com.datausage.monitor.util.NetworkUsageHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    private val appUsageDao: AppUsageDao,
    private val monitoredAppDao: MonitoredAppDao,
    private val networkUsageHelper: NetworkUsageHelper
) {
    fun getMonitoredApps(profileId: Long): Flow<List<MonitoredAppEntity>> =
        monitoredAppDao.getMonitoredApps(profileId)

    suspend fun getMonitoredAppsList(profileId: Long): List<MonitoredAppEntity> =
        monitoredAppDao.getMonitoredAppsList(profileId)

    suspend fun addMonitoredApp(profileId: Long, packageName: String, appName: String, uid: Int) {
        monitoredAppDao.insert(
            MonitoredAppEntity(
                profileId = profileId,
                packageName = packageName,
                appName = appName,
                uid = uid
            )
        )
    }

    suspend fun removeMonitoredApp(profileId: Long, packageName: String) {
        monitoredAppDao.deleteByPackage(profileId, packageName)
    }

    suspend fun setMonitoredApps(profileId: Long, apps: List<MonitoredAppEntity>) {
        monitoredAppDao.deleteAllForProfile(profileId)
        monitoredAppDao.insertAll(apps)
    }

    suspend fun pollUsage(sessionId: Long, profileId: Long, sessionStartTime: Long) {
        val monitoredApps = monitoredAppDao.getMonitoredAppsList(profileId)
        if (monitoredApps.isEmpty()) return

        val uids = monitoredApps.map { it.uid }
        val usageMap = networkUsageHelper.getAllAppsUsageSince(uids, sessionStartTime)

        val now = System.currentTimeMillis()
        val usageEntities = monitoredApps.mapNotNull { app ->
            val usage = usageMap[app.uid] ?: return@mapNotNull null
            AppUsageEntity(
                sessionId = sessionId,
                monitoredAppId = app.id,
                packageName = app.packageName,
                timestamp = now,
                bytesRx = usage.rxBytes,
                bytesTx = usage.txBytes
            )
        }

        if (usageEntities.isNotEmpty()) {
            appUsageDao.insertAll(usageEntities)
        }
    }

    suspend fun getUsageByAppForSession(sessionId: Long): List<AppUsageSummary> =
        appUsageDao.getUsageByAppForSession(sessionId)

    suspend fun getUsageByAppForPeriod(profileId: Long, from: Long, to: Long): List<AppUsageSummary> =
        appUsageDao.getUsageByAppForPeriod(profileId, from, to)

    suspend fun getUsageForApp(profileId: Long, packageName: String, from: Long, to: Long): AppUsageSummary? =
        appUsageDao.getUsageForApp(profileId, packageName, from, to)

    fun getInstalledApps() = networkUsageHelper.getInstalledApps()

    suspend fun deleteAllForProfile(profileId: Long) =
        appUsageDao.deleteAllForProfile(profileId)

    suspend fun deleteAll() = appUsageDao.deleteAll()
}
