package com.datausage.monitor.data.repository

import com.datausage.monitor.data.local.db.dao.DataLimitDao
import com.datausage.monitor.data.local.db.entity.DataLimitEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitRepository @Inject constructor(
    private val dataLimitDao: DataLimitDao
) {
    fun getLimitsForProfile(profileId: Long): Flow<List<DataLimitEntity>> =
        dataLimitDao.getLimitsForProfile(profileId)

    suspend fun getLimitsForProfileList(profileId: Long): List<DataLimitEntity> =
        dataLimitDao.getLimitsForProfileList(profileId)

    suspend fun getProfileWideLimits(profileId: Long): List<DataLimitEntity> =
        dataLimitDao.getProfileWideLimits(profileId)

    suspend fun getAppLimits(profileId: Long, packageName: String): List<DataLimitEntity> =
        dataLimitDao.getAppLimits(profileId, packageName)

    suspend fun getById(id: Long): DataLimitEntity? = dataLimitDao.getById(id)

    suspend fun create(
        profileId: Long,
        packageName: String? = null,
        limitBytes: Long,
        periodType: String,
        warningPercent: Int = 80,
        actionOnExceed: String = "notify"
    ): Long {
        return dataLimitDao.insert(
            DataLimitEntity(
                profileId = profileId,
                packageName = packageName,
                limitBytes = limitBytes,
                periodType = periodType,
                warningPercent = warningPercent,
                actionOnExceed = actionOnExceed
            )
        )
    }

    suspend fun update(limit: DataLimitEntity) = dataLimitDao.update(limit)

    suspend fun delete(limit: DataLimitEntity) = dataLimitDao.delete(limit)

    suspend fun deleteAllForProfile(profileId: Long) = dataLimitDao.deleteAllForProfile(profileId)

    suspend fun deleteAll() = dataLimitDao.deleteAll()
}
