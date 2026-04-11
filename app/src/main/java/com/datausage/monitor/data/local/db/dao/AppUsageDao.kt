package com.datausage.monitor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.datausage.monitor.data.local.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

data class AppUsageSummary(
    val packageName: String,
    val totalWifiRx: Long,
    val totalWifiTx: Long,
    val totalMobileRx: Long,
    val totalMobileTx: Long
) {
    val wifiTotal: Long get() = totalWifiRx + totalWifiTx
    val mobileTotal: Long get() = totalMobileRx + totalMobileTx
    val totalRx: Long get() = totalWifiRx + totalMobileRx
    val totalTx: Long get() = totalWifiTx + totalMobileTx
    val totalBytes: Long get() = totalRx + totalTx
}

@Dao
interface AppUsageDao {

    @Query("""
        SELECT packageName,
            MAX(wifiRx) as totalWifiRx, MAX(wifiTx) as totalWifiTx,
            MAX(mobileRx) as totalMobileRx, MAX(mobileTx) as totalMobileTx
        FROM app_usage WHERE sessionId = :sessionId
        GROUP BY packageName
        ORDER BY (MAX(wifiRx) + MAX(wifiTx) + MAX(mobileRx) + MAX(mobileTx)) DESC
    """)
    suspend fun getUsageByAppForSession(sessionId: Long): List<AppUsageSummary>

    @Query("""
        SELECT packageName,
            SUM(maxWifiRx) as totalWifiRx, SUM(maxWifiTx) as totalWifiTx,
            SUM(maxMobileRx) as totalMobileRx, SUM(maxMobileTx) as totalMobileTx
        FROM (
            SELECT packageName,
                MAX(wifiRx) as maxWifiRx, MAX(wifiTx) as maxWifiTx,
                MAX(mobileRx) as maxMobileRx, MAX(mobileTx) as maxMobileTx
            FROM app_usage
            WHERE sessionId IN (
                SELECT sessionId FROM sessions
                WHERE profileId = :profileId AND startTime >= :from AND startTime <= :to
            )
            GROUP BY sessionId, packageName
        )
        GROUP BY packageName
        ORDER BY (SUM(maxWifiRx) + SUM(maxWifiTx) + SUM(maxMobileRx) + SUM(maxMobileTx)) DESC
    """)
    suspend fun getUsageByAppForPeriod(profileId: Long, from: Long, to: Long): List<AppUsageSummary>

    @Query("""
        SELECT packageName,
            SUM(maxWifiRx) as totalWifiRx, SUM(maxWifiTx) as totalWifiTx,
            SUM(maxMobileRx) as totalMobileRx, SUM(maxMobileTx) as totalMobileTx
        FROM (
            SELECT packageName,
                MAX(wifiRx) as maxWifiRx, MAX(wifiTx) as maxWifiTx,
                MAX(mobileRx) as maxMobileRx, MAX(mobileTx) as maxMobileTx
            FROM app_usage
            WHERE sessionId IN (
                SELECT sessionId FROM sessions WHERE profileId = :profileId
            )
            AND packageName = :packageName
            AND timestamp >= :from AND timestamp <= :to
            GROUP BY sessionId, packageName
        )
        GROUP BY packageName
    """)
    suspend fun getUsageForApp(profileId: Long, packageName: String, from: Long, to: Long): AppUsageSummary?

    @Query("SELECT * FROM app_usage WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getAppUsageForSession(sessionId: Long): Flow<List<AppUsageEntity>>

    @Insert
    suspend fun insert(usage: AppUsageEntity): Long

    @Insert
    suspend fun insertAll(usages: List<AppUsageEntity>)

    @Query("DELETE FROM app_usage WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM app_usage WHERE sessionId IN (SELECT sessionId FROM sessions WHERE profileId = :profileId)")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("DELETE FROM app_usage")
    suspend fun deleteAll()
}
