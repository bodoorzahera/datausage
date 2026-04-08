package com.datausage.monitor.data.repository

import com.datausage.monitor.data.local.db.dao.SessionDao
import com.datausage.monitor.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {
    fun getSessionsForProfile(profileId: Long): Flow<List<SessionEntity>> =
        sessionDao.getSessionsForProfile(profileId)

    fun getActiveSessionFlow(profileId: Long): Flow<SessionEntity?> =
        sessionDao.getActiveSessionFlow(profileId)

    suspend fun getActiveSession(profileId: Long): SessionEntity? =
        sessionDao.getActiveSession(profileId)

    suspend fun getById(sessionId: Long): SessionEntity? =
        sessionDao.getById(sessionId)

    suspend fun getSessionsInRange(profileId: Long, from: Long, to: Long): List<SessionEntity> =
        sessionDao.getSessionsInRange(profileId, from, to)

    suspend fun getTotalUsageInRange(profileId: Long, from: Long, to: Long): Long =
        sessionDao.getTotalUsageInRange(profileId, from, to)

    suspend fun getTotalRxInRange(profileId: Long, from: Long, to: Long): Long =
        sessionDao.getTotalRxInRange(profileId, from, to)

    suspend fun getTotalTxInRange(profileId: Long, from: Long, to: Long): Long =
        sessionDao.getTotalTxInRange(profileId, from, to)

    suspend fun getTotalInternalUsageInRange(profileId: Long, from: Long, to: Long): Long =
        sessionDao.getTotalInternalUsageInRange(profileId, from, to)

    suspend fun startSession(profileId: Long): Long {
        return sessionDao.insert(
            SessionEntity(
                profileId = profileId,
                startTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun stopSession(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(
            session.copy(endTime = System.currentTimeMillis())
        )
    }

    suspend fun updateSessionUsage(
        sessionId: Long,
        externalRx: Long,
        externalTx: Long,
        internalRx: Long,
        internalTx: Long
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(
            session.copy(
                totalBytesRx = externalRx,
                totalBytesTx = externalTx,
                internalBytesRx = internalRx,
                internalBytesTx = internalTx
            )
        )
    }

    suspend fun deleteAllForProfile(profileId: Long) =
        sessionDao.deleteAllForProfile(profileId)

    suspend fun deleteAll() = sessionDao.deleteAll()

    suspend fun insert(session: SessionEntity): Long = sessionDao.insert(session)
}
