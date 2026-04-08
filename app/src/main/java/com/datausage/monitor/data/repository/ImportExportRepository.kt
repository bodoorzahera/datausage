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
    val version: Int = 1,
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
    val totalBytesRx: Long,
    val totalBytesTx: Long,
    val networkType: Int = 0,
    val appUsage: List<ExportAppUsage> = emptyList()
)

@Serializable
data class ExportAppUsage(
    val packageName: String,
    val timestamp: Long,
    val bytesRx: Long,
    val bytesTx: Long
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

        // Header
        writer.write("Profile,Session ID,Start Time,End Time,App Package,Bytes Rx,Bytes Tx,Total Bytes\n")

        val sessions = sessionDao.getSessionsInRange(profileId, 0, Long.MAX_VALUE)
        for (session in sessions) {
            val appUsages = appUsageDao.getUsageByAppForSession(session.sessionId)
            if (appUsages.isEmpty()) {
                writer.write("${profile.name},${session.sessionId},${session.startTime},${session.endTime ?: ""},,,${session.totalBytesRx + session.totalBytesTx}\n")
            } else {
                for (usage in appUsages) {
                    writer.write("${profile.name},${session.sessionId},${session.startTime},${session.endTime ?: ""},${usage.packageName},${usage.totalRx},${usage.totalTx},${usage.totalRx + usage.totalTx}\n")
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
        val sessionMap = mutableMapOf<String, Long>() // csv sessionId -> db sessionId

        for (line in lines) {
            val parts = line.split(",")
            if (parts.size < 6) continue

            val csvSessionId = parts[1].trim()
            val startTime = parts[2].trim().toLongOrNull() ?: continue
            val endTime = parts[3].trim().toLongOrNull()
            val packageName = parts[4].trim()
            val bytesRx = parts[5].trim().toLongOrNull() ?: 0
            val bytesTx = parts[6].trim().toLongOrNull() ?: 0

            val dbSessionId = sessionMap.getOrPut(csvSessionId) {
                sessionDao.insert(
                    SessionEntity(
                        profileId = profileId,
                        startTime = startTime,
                        endTime = endTime,
                        totalBytesRx = bytesRx,
                        totalBytesTx = bytesTx
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
                        bytesRx = bytesRx,
                        bytesTx = bytesTx
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
                    totalBytesRx = session.totalBytesRx,
                    totalBytesTx = session.totalBytesTx,
                    networkType = session.networkType,
                    appUsage = appUsages.map { usage ->
                        ExportAppUsage(
                            packageName = usage.packageName,
                            timestamp = session.startTime,
                            bytesRx = usage.totalRx,
                            bytesTx = usage.totalTx
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

        // Import cost config
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

        // Import monitored apps
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

        // Import sessions and app usage
        for (exportSession in exportProfile.sessions) {
            val sessionId = sessionDao.insert(
                SessionEntity(
                    profileId = profileId,
                    startTime = exportSession.startTime,
                    endTime = exportSession.endTime,
                    totalBytesRx = exportSession.totalBytesRx,
                    totalBytesTx = exportSession.totalBytesTx,
                    networkType = exportSession.networkType
                )
            )

            val appUsages = exportSession.appUsage.map { usage ->
                AppUsageEntity(
                    sessionId = sessionId,
                    monitoredAppId = monitoredAppIds[usage.packageName] ?: 0,
                    packageName = usage.packageName,
                    timestamp = usage.timestamp,
                    bytesRx = usage.bytesRx,
                    bytesTx = usage.bytesTx
                )
            }
            if (appUsages.isNotEmpty()) {
                appUsageDao.insertAll(appUsages)
            }
        }

        // Import limits
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
