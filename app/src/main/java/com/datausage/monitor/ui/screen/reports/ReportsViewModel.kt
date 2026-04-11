package com.datausage.monitor.ui.screen.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.dao.AppUsageSummary
import com.datausage.monitor.data.local.db.entity.SessionEntity
import com.datausage.monitor.data.repository.CostRepository
import com.datausage.monitor.data.repository.SessionRepository
import com.datausage.monitor.data.repository.UsageRepository
import com.datausage.monitor.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReportPeriod { DAY, WEEK, MONTH }

data class ReportState(
    val period: ReportPeriod = ReportPeriod.DAY,
    val totalUsage: Long = 0,
    val totalRx: Long = 0,
    val totalTx: Long = 0,
    val totalWifi: Long = 0,
    val totalMobile: Long = 0,
    val cost: Double = 0.0,
    val currency: String = "USD",
    val sessions: List<SessionEntity> = emptyList(),
    val appUsages: List<AppUsageSummary> = emptyList()
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val usageRepository: UsageRepository,
    private val costRepository: CostRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state

    init {
        loadReport(ReportPeriod.DAY)
    }

    fun selectPeriod(period: ReportPeriod) {
        loadReport(period)
    }

    private fun loadReport(period: ReportPeriod) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (from, to) = when (period) {
                ReportPeriod.DAY -> Pair(FormatUtils.startOfDay(now), FormatUtils.endOfDay(now))
                ReportPeriod.WEEK -> Pair(FormatUtils.startOfWeek(now), FormatUtils.endOfWeek(now))
                ReportPeriod.MONTH -> Pair(FormatUtils.startOfMonth(now), FormatUtils.endOfMonth(now))
            }

            val totalRx = sessionRepository.getTotalRxInRange(profileId, from, to)
            val totalTx = sessionRepository.getTotalTxInRange(profileId, from, to)
            val totalWifi = sessionRepository.getWifiUsageInRange(profileId, from, to)
            val totalMobile = sessionRepository.getMobileUsageInRange(profileId, from, to)
            val sessions = sessionRepository.getSessionsInRange(profileId, from, to)
            val appUsages = usageRepository.getUsageByAppForPeriod(profileId, from, to)
            val costSummary = costRepository.calculateCostForPeriod(profileId, from, to)

            _state.value = ReportState(
                period = period,
                totalUsage = totalRx + totalTx,
                totalRx = totalRx,
                totalTx = totalTx,
                totalWifi = totalWifi,
                totalMobile = totalMobile,
                cost = costSummary?.cost ?: 0.0,
                currency = costSummary?.currency ?: "USD",
                sessions = sessions,
                appUsages = appUsages
            )
        }
    }
}
