package com.datausage.monitor.data.repository

import com.datausage.monitor.data.local.db.dao.CostConfigDao
import com.datausage.monitor.data.local.db.entity.CostConfigEntity
import com.datausage.monitor.util.FormatUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class CostSummary(
    val totalBytes: Long,
    val cost: Double,
    val currency: String
)

@Singleton
class CostRepository @Inject constructor(
    private val costConfigDao: CostConfigDao,
    private val sessionRepository: SessionRepository
) {
    fun getCostConfig(profileId: Long): Flow<CostConfigEntity?> =
        costConfigDao.getCostConfig(profileId)

    suspend fun getCostConfigSync(profileId: Long): CostConfigEntity? =
        costConfigDao.getCostConfigSync(profileId)

    suspend fun saveCostConfig(
        profileId: Long,
        costPerUnit: Double,
        unitBytes: Long,
        currency: String = "USD"
    ) {
        val existing = costConfigDao.getCostConfigSync(profileId)
        if (existing != null) {
            costConfigDao.update(
                existing.copy(
                    costPerUnit = costPerUnit,
                    unitBytes = unitBytes,
                    currency = currency
                )
            )
        } else {
            costConfigDao.insert(
                CostConfigEntity(
                    profileId = profileId,
                    costPerUnit = costPerUnit,
                    unitBytes = unitBytes,
                    currency = currency
                )
            )
        }
    }

    suspend fun calculateCostForPeriod(profileId: Long, from: Long, to: Long): CostSummary? {
        val config = costConfigDao.getCostConfigSync(profileId) ?: return null
        val totalBytes = sessionRepository.getTotalUsageInRange(profileId, from, to)
        val cost = FormatUtils.calculateCost(totalBytes, config.costPerUnit, config.unitBytes)
        return CostSummary(totalBytes, cost, config.currency)
    }

    suspend fun deleteForProfile(profileId: Long) = costConfigDao.deleteForProfile(profileId)

    suspend fun deleteAll() = costConfigDao.deleteAll()
}
