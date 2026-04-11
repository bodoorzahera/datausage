package com.datausage.monitor.data.repository

import com.datausage.monitor.data.local.db.dao.AppUsageDao
import com.datausage.monitor.data.local.db.dao.CostConfigDao
import com.datausage.monitor.data.local.db.dao.DataLimitDao
import com.datausage.monitor.data.local.db.dao.MonitoredAppDao
import com.datausage.monitor.data.local.db.dao.ProfileDao
import com.datausage.monitor.data.local.db.dao.SessionDao
import com.datausage.monitor.data.local.db.entity.AppUsageEntity
import com.datausage.monitor.data.local.db.entity.CostConfigEntity
import com.datausage.monitor.data.local.db.entity.DataLimitEntity
import com.datausage.monitor.data.local.db.entity.MonitoredAppEntity
import com.datausage.monitor.data.local.db.entity.ProfileEntity
import com.datausage.monitor.data.local.db.entity.SessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportData(
    val version: Int = 2,
    val exportDate: String,
    val profiles: List<ExportProfile>
)

@Serializable
data class ExportProfile(
    val name: String,
    val pin: String? = null,
    val costConfig: ExportCostConfig? = null,
    val sessions: List<ExportSession> = emptyList(),
    val limits: List<ExportLimit> = emptyList(),
    val monitoredApps: List<ExportMonitoredApp> = emptyList()
)

@Serializable
data class ExportCostConfig(
    val costPerUnit: Double,
    val unitBytes: Long,
    val currency: String
)

@Serializable
data class ExportSession(
    val startTime: Long,
    val endTime: Long? = null,
    val wifiRx: Long = 0,
    val wifiTx: Long = 0,
    val mobileRx: Long = 0,
    val mobileTx: Long = 0,
    // Legacy fields for backwards compatibility on import
    val totalBytesRx: Long = 0,
    val totalBytesTx: Long = 0,
    val internalBytesRx: Long = 0,
    val internalBytesTx: Long = 0,
    val networkType: Int = 0,
    val appUsage: List<ExportAppUsage> = emptyList()
)

@Serializable
data class ExportAppUsage(
    val packageName: String,
    val timestamp: Long,
    val wifiRx: Long = 0,
    val wifiTx: Long = 0,
    val mobileRx: Long = 0,
    val mobileTx: Long = 0,
    // Legacy fields for backwards compatibility on import
    val bytesRx: Long = 0,
    val bytesTx: Long = 0,
    val internalRx: Long = 0,
    val internalTx: Long = 0
)

@Serializable
data class ExportLimit(
    val packageName: String? = null,
    val limitBytes: Long,
    val periodType: String,
    val warningPercent: Int,
    val actionOnExceed: String
)

@Serializable
data class ExportMonitoredApp(
    val packageName: String,
    val appName: String,
    val uid: Int
)

enum class ImportMode {
    ADD, REPLACE
}

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Singleton
class ImportExportRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val sessionDao: SessionDao,
    private val appUsageDao: AppUsageDao,
    private val costConfigDao: CostConfigDao,
    private val dataLimitDao: DataLimitDao,
    private val monitoredAppDao: MonitoredAppDao
) {

    suspend fun exportToJson(outputStream: OutputStream, profileIds: List<Long>? = null) {
        val profiles = if (profileIds != null) {
            profileIds.mapNotNull { profileDao.getById(it) }
        } else {
            profileDao.getAllProfilesList()
        }

        val exportData = ExportData(
            exportDate = java.time.Instant.now().toString(),
            profiles = profiles.map { profile -> buildExportProfile(profile) }
        )

        outputStream.write(json.encodeToString(exportData).toByteArray())
    }

    suspend fun exportToCsv(outputStream: OutputStream, profileId: Long) {
        val profile = profileDao.getById(profileId) ?: return
        val writer = outputStream.bufferedWriter()

        writer.write("Profile,Session ID,Start Time,End Time,App Package,WiFi Rx,WiFi Tx,Mobile Rx,Mobile Tx,Total\n")

        val sessions = sessionDao.getSessionsInRange(profileId, 0, Long.MAX_VALUE)
        for (session in sessions) {
            val appUsages = appUsageDao.getUsageByAppForSession(session.sessionId)
            if (appUsages.isEmpty()) {
                writer.write("${profile.name},${session.sessionId},${session.startTime},${session.endTime ?: ""},,,${session.wifiRx},${session.wifiTx},${session.mobileRx},${session.mobileTx},${session.totalBytes}\n")
            } else {
                for (usage in appUsages) {
                    writer.write("${profile.name},${session.sessionId},${session.startTime},${session.endTime ?: ""},${usage.packageName},${usage.totalWifiRx},${usage.totalWifiTx},${usage.totalMobileRx},${usage.totalMobileTx},${usage.totalBytes}\n")
                }
            }
        }

        writer.flush()
    }

    suspend fun importFromJson(inputStream: InputStream, mode: ImportMode): Int {
        val content = inputStream.bufferedReader().readText()
        val exportData = json.decodeFromString<ExportData>(content)

        if (mode == ImportMode.REPLACE) {
            clearAllData()
        }

        var importedProfiles = 0
        for (exportProfile in exportData.profiles) {
            importProfile(exportProfile)
            importedProfiles++
        }

        return importedProfiles
    }

    suspend fun importFromCsv(inputStream: InputStream, mode: ImportMode, profileId: Long): Int {
        val reader = inputStream.bufferedReader()
        val lines = reader.readLines().drop(1) // skip header

        if (mode == ImportMode.REPLACE) {
            sessionDao.deleteAllForProfile(profileId)
            appUsageDao.deleteAllForProfile(profileId)
        }

        var importedRows = 0
        val sessionMap = mutableMapOf<String, Long>()

        for (line in lines) {
            val parts = line.split(",")
            if (parts.size < 6) continue

            val csvSessionId = parts[1].trim()
            val startTime = parts[2].trim().toLongOrNull() ?: continue
            val endTime = parts[3].trim().toLongOrNull()
            val packageName = parts[4].trim()
            val wifiRx = parts.getOrNull(5)?.trim()?.toLongOrNull() ?: 0
            val wifiTx = parts.getOrNull(6)?.trim()?.toLongOrNull() ?: 0
            val mobileRx = parts.getOrNull(7)?.trim()?.toLongOrNull() ?: 0
            val mobileTx = parts.getOrNull(8)?.trim()?.toLongOrNull() ?: 0

            val dbSessionId = sessionMap.getOrPut(csvSessionId) {
                sessionDao.insert(
                    SessionEntity(
                        profileId = profileId,
                        startTime = startTime,
                        endTime = endTime,
                        wifiRx = wifiRx,
                        wifiTx = wifiTx,
                        mobileRx = mobileRx,
                        mobileTx = mobileTx
                    )
                )
            }

            if (packageName.isNotEmpty()) {
                appUsageDao.insert(
                    AppUsageEntity(
                        sessionId = dbSessionId,
                        monitoredAppId = 0,
                        packageName = packageName,
                        timestamp = startTime,
                        wifiRx = wifiRx,
                        wifiTx = wifiTx,
                        mobileRx = mobileRx,
                        mobileTx = mobileTx
                    )
                )
            }

            importedRows++
        }

        return importedRows
    }

    private suspend fun buildExportProfile(profile: ProfileEntity): ExportProfile {
        val costConfig = costConfigDao.getCostConfigSync(profile.profileId)
        val sessions = sessionDao.getSessionsInRange(profile.profileId, 0, Long.MAX_VALUE)
        val limits = dataLimitDao.getLimitsForProfileList(profile.profileId)
        val monitoredApps = monitoredAppDao.getMonitoredAppsList(profile.profileId)

        return ExportProfile(
            name = profile.name,
            pin = profile.pin,
            costConfig = costConfig?.let {
                ExportCostConfig(it.costPerUnit, it.unitBytes, it.currency)
            },
            sessions = sessions.map { session ->
                val appUsages = appUsageDao.getUsageByAppForSession(session.sessionId)
                ExportSession(
                    startTime = session.startTime,
                    endTime = session.endTime,
                    wifiRx = session.wifiRx,
                    wifiTx = session.wifiTx,
                    mobileRx = session.mobileRx,
                    mobileTx = session.mobileTx,
                    appUsage = appUsages.map { usage ->
                        ExportAppUsage(
                            packageName = usage.packageName,
                            timestamp = session.startTime,
                            wifiRx = usage.totalWifiRx,
                            wifiTx = usage.totalWifiTx,
                            mobileRx = usage.totalMobileRx,
                            mobileTx = usage.totalMobileTx
                        )
                    }
                )
            },
            limits = limits.map { limit ->
                ExportLimit(
                    packageName = limit.packageName,
                    limitBytes = limit.limitBytes,
                    periodType = limit.periodType,
                    warningPercent = limit.warningPercent,
                    actionOnExceed = limit.actionOnExceed
                )
            },
            monitoredApps = monitoredApps.map { app ->
                ExportMonitoredApp(
                    packageName = app.packageName,
                    appName = app.appName,
                    uid = app.uid
                )
            }
        )
    }

    private suspend fun importProfile(exportProfile: ExportProfile) {
        val profileId = profileDao.insert(
            ProfileEntity(name = exportProfile.name, pin = exportProfile.pin)
        )

        exportProfile.costConfig?.let {
            costConfigDao.insert(
                CostConfigEntity(
                    profileId = profileId,
                    costPerUnit = it.costPerUnit,
                    unitBytes = it.unitBytes,
                    currency = it.currency
                )
            )
        }

        val monitoredAppIds = mutableMapOf<String, Long>()
        for (app in exportProfile.monitoredApps) {
            val id = monitoredAppDao.insert(
                MonitoredAppEntity(
                    profileId = profileId,
                    packageName = app.packageName,
                    appName = app.appName,
                    uid = app.uid
                )
            )
            monitoredAppIds[app.packageName] = id
        }

        for (exportSession in exportProfile.sessions) {
            // Support legacy format: if wifiRx/mobileTx are 0 but old fields have data
            val sWifiRx = if (exportSession.wifiRx > 0) exportSession.wifiRx else exportSession.totalBytesRx
            val sWifiTx = if (exportSession.wifiTx > 0) exportSession.wifiTx else exportSession.totalBytesTx

            val sessionId = sessionDao.insert(
                SessionEntity(
                    profileId = profileId,
                    startTime = exportSession.startTime,
                    endTime = exportSession.endTime,
                    wifiRx = sWifiRx,
                    wifiTx = sWifiTx,
                    mobileRx = exportSession.mobileRx,
                    mobileTx = exportSession.mobileTx
                )
            )

            val appUsages = exportSession.appUsage.map { usage ->
                val uWifiRx = if (usage.wifiRx > 0) usage.wifiRx else usage.bytesRx
                val uWifiTx = if (usage.wifiTx > 0) usage.wifiTx else usage.bytesTx
                AppUsageEntity(
                    sessionId = sessionId,
                    monitoredAppId = monitoredAppIds[usage.packageName] ?: 0,
                    packageName = usage.packageName,
                    timestamp = usage.timestamp,
                    wifiRx = uWifiRx,
                    wifiTx = uWifiTx,
                    mobileRx = usage.mobileRx,
                    mobileTx = usage.mobileTx
                )
            }
            if (appUsages.isNotEmpty()) {
                appUsageDao.insertAll(appUsages)
            }
        }

        for (limit in exportProfile.limits) {
            dataLimitDao.insert(
                DataLimitEntity(
                    profileId = profileId,
                    packageName = limit.packageName,
                    limitBytes = limit.limitBytes,
                    periodType = limit.periodType,
                    warningPercent = limit.warningPercent,
                    actionOnExceed = limit.actionOnExceed
                )
            )
        }
    }

    private suspend fun clearAllData() {
        appUsageDao.deleteAll()
        sessionDao.deleteAll()
        dataLimitDao.deleteAll()
        costConfigDao.deleteAll()
        profileDao.deleteAll()
    }
}
