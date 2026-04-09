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

    // Values stored are cumulative since session start — use MAX() to get the latest (correct) total.
    @Query("""
        SELECT packageName,
            MAX(bytesRx) as totalRx, MAX(bytesTx) as totalTx,
            MAX(internalRx) as totalInternalRx, MAX(internalTx) as totalInternalTx
        FROM app_usage WHERE sessionId = :sessionId
        GROUP BY packageName
        ORDER BY (MAX(bytesRx) + MAX(bytesTx)) DESC
    """)
    suspend fun getUsageByAppForSession(sessionId: Long): List<AppUsageSummary>

    // Per-session MAX first, then SUM across sessions to avoid multiplying cumulative snapshots.
    @Query("""
        SELECT packageName,
            SUM(maxRx) as totalRx, SUM(maxTx) as totalTx,
            SUM(maxInternalRx) as totalInternalRx, SUM(maxInternalTx) as totalInternalTx
        FROM (
            SELECT packageName,
                MAX(bytesRx) as maxRx, MAX(bytesTx) as maxTx,
                MAX(internalRx) as maxInternalRx, MAX(internalTx) as maxInternalTx
            FROM app_usage
            WHERE sessionId IN (
                SELECT sessionId FROM sessions
                WHERE profileId = :profileId AND startTime >= :from AND startTime <= :to
            )
            GROUP BY sessionId, packageName
        )
        GROUP BY packageName
        ORDER BY (SUM(maxRx) + SUM(maxTx)) DESC
    """)
    suspend fun getUsageByAppForPeriod(profileId: Long, from: Long, to: Long): List<AppUsageSummary>

    @Query("""
        SELECT packageName,
            SUM(maxRx) as totalRx, SUM(maxTx) as totalTx,
            SUM(maxInternalRx) as totalInternalRx, SUM(maxInternalTx) as totalInternalTx
        FROM (
            SELECT packageName,
                MAX(bytesRx) as maxRx, MAX(bytesTx) as maxTx,
                MAX(internalRx) as maxInternalRx, MAX(internalTx) as maxInternalTx
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

    /** Replace the current snapshot for a session with fresh data (keeps only latest per app). */
    @Query("DELETE FROM app_usage WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM app_usage WHERE sessionId IN (SELECT sessionId FROM sessions WHERE profileId = :profileId)")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("DELETE FROM app_usage")
    suspend fun deleteAll()
}
