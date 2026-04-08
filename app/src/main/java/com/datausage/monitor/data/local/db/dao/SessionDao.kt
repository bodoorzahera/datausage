package com.datausage.monitor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.datausage.monitor.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTime DESC")
    fun getSessionsForProfile(profileId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND endTime IS NULL LIMIT 1")
    suspend fun getActiveSession(profileId: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND endTime IS NULL LIMIT 1")
    fun getActiveSessionFlow(profileId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: Long): SessionEntity?

    @Query("""
        SELECT * FROM sessions
        WHERE profileId = :profileId
        AND startTime >= :from AND startTime <= :to
        ORDER BY startTime DESC
    """)
    suspend fun getSessionsInRange(profileId: Long, from: Long, to: Long): List<SessionEntity>

    @Query("""
        SELECT COALESCE(SUM(totalBytesRx + totalBytesTx), 0) FROM sessions
        WHERE profileId = :profileId
        AND startTime >= :from AND (endTime <= :to OR endTime IS NULL)
    """)
    suspend fun getTotalUsageInRange(profileId: Long, from: Long, to: Long): Long

    @Query("""
        SELECT COALESCE(SUM(totalBytesRx), 0) FROM sessions
        WHERE profileId = :profileId
        AND startTime >= :from AND (endTime <= :to OR endTime IS NULL)
    """)
    suspend fun getTotalRxInRange(profileId: Long, from: Long, to: Long): Long

    @Query("""
        SELECT COALESCE(SUM(totalBytesTx), 0) FROM sessions
        WHERE profileId = :profileId
        AND startTime >= :from AND (endTime <= :to OR endTime IS NULL)
    """)
    suspend fun getTotalTxInRange(profileId: Long, from: Long, to: Long): Long

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
