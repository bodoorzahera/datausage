package com.datausage.monitor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.datausage.monitor.data.local.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

data class AppUsageSummary(
    val packageName: String,
    val totalRx: Long,       // external download
    val totalTx: Long,       // external upload
    val totalInternalRx: Long,  // internal download
    val totalInternalTx: Long   // internal upload
)

@Dao
interface AppUsageDao {

    @Query("""
        SELECT packageName,
            SUM(bytesRx) as totalRx, SUM(bytesTx) as totalTx,
            SUM(internalRx) as totalInternalRx, SUM(internalTx) as totalInternalTx
        FROM app_usage WHERE sessionId = :sessionId
        GROUP BY packageName
        ORDER BY (SUM(bytesRx) + SUM(bytesTx)) DESC
    """)
    suspend fun getUsageByAppForSession(sessionId: Long): List<AppUsageSummary>

    @Query("""
        SELECT packageName,
            SUM(bytesRx) as totalRx, SUM(bytesTx) as totalTx,
            SUM(internalRx) as totalInternalRx, SUM(internalTx) as totalInternalTx
        FROM app_usage
        WHERE sessionId IN (
            SELECT sessionId FROM sessions
            WHERE profileId = :profileId AND startTime >= :from AND startTime <= :to
        )
        GROUP BY packageName
        ORDER BY (SUM(bytesRx) + SUM(bytesTx)) DESC
    """)
    suspend fun getUsageByAppForPeriod(profileId: Long, from: Long, to: Long): List<AppUsageSummary>

    @Query("""
        SELECT packageName,
            SUM(bytesRx) as totalRx, SUM(bytesTx) as totalTx,
            SUM(internalRx) as totalInternalRx, SUM(internalTx) as totalInternalTx
        FROM app_usage
        WHERE sessionId IN (
            SELECT sessionId FROM sessions WHERE profileId = :profileId
        )
        AND packageName = :packageName
        AND timestamp >= :from AND timestamp <= :to
        GROUP BY packageName
    """)
    suspend fun getUsageForApp(profileId: Long, packageName: String, from: Long, to: Long): AppUsageSummary?

    @Query("SELECT * FROM app_usage WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getAppUsageForSession(sessionId: Long): Flow<List<AppUsageEntity>>

    @Insert
    suspend fun insert(usage: AppUsageEntity): Long

    @Insert
    suspend fun insertAll(usages: List<AppUsageEntity>)

    @Query("DELETE FROM app_usage WHERE sessionId IN (SELECT sessionId FROM sessions WHERE profileId = :profileId)")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("DELETE FROM app_usage")
    suspend fun deleteAll()
}
