package com.datausage.monitor.ui.screen.dashboard

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.entity.ProfileEntity
import com.datausage.monitor.data.local.db.entity.SessionEntity
import com.datausage.monitor.data.repository.CostRepository
import com.datausage.monitor.data.repository.CostSummary
import com.datausage.monitor.data.repository.ProfileRepository
import com.datausage.monitor.data.repository.SessionRepository
import com.datausage.monitor.service.DataMonitorService
import com.datausage.monitor.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NetworkFilter { ALL, WIFI, MOBILE }

data class DashboardState(
    val profile: ProfileEntity? = null,
    val activeSession: SessionEntity? = null,
    val isMonitoring: Boolean = false,
    val networkFilter: NetworkFilter = NetworkFilter.ALL,
    val todayUsage: Long = 0,
    val weekUsage: Long = 0,
    val monthUsage: Long = 0,
    val todayWifi: Long = 0,
    val weekWifi: Long = 0,
    val monthWifi: Long = 0,
    val todayMobile: Long = 0,
    val weekMobile: Long = 0,
    val monthMobile: Long = 0,
    val todayCost: CostSummary? = null,
    val monthCost: CostSummary? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val profileRepository: ProfileRepository,
    private val sessionRepository: SessionRepository,
    private val costRepository: CostRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        viewModelScope.launch {
            refresh()
        }
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                refresh()
            }
        }
    }

    fun setNetworkFilter(filter: NetworkFilter) {
        _state.value = _state.value.copy(networkFilter = filter)
    }

    private suspend fun refresh() {
        val profile = profileRepository.getById(profileId)
        val activeSession = sessionRepository.getActiveSession(profileId)
        val now = System.currentTimeMillis()

        val dayStart = FormatUtils.startOfDay(now)
        val dayEnd = FormatUtils.endOfDay(now)
        val weekStart = FormatUtils.startOfWeek(now)
        val weekEnd = FormatUtils.endOfWeek(now)
        val monthStart = FormatUtils.startOfMonth(now)
        val monthEnd = FormatUtils.endOfMonth(now)

        val todayTotal = sessionRepository.getTotalUsageInRange(profileId, dayStart, dayEnd)
        val weekTotal = sessionRepository.getTotalUsageInRange(profileId, weekStart, weekEnd)
        val monthTotal = sessionRepository.getTotalUsageInRange(profileId, monthStart, monthEnd)

        val todayWifi = sessionRepository.getWifiUsageInRange(profileId, dayStart, dayEnd)
        val weekWifi = sessionRepository.getWifiUsageInRange(profileId, weekStart, weekEnd)
        val monthWifi = sessionRepository.getWifiUsageInRange(profileId, monthStart, monthEnd)

        val todayMobile = sessionRepository.getMobileUsageInRange(profileId, dayStart, dayEnd)
        val weekMobile = sessionRepository.getMobileUsageInRange(profileId, weekStart, weekEnd)
        val monthMobile = sessionRepository.getMobileUsageInRange(profileId, monthStart, monthEnd)

        val todayCost = costRepository.calculateCostForPeriod(profileId, dayStart, dayEnd)
        val monthCost = costRepository.calculateCostForPeriod(profileId, monthStart, monthEnd)

        _state.value = _state.value.copy(
            profile = profile,
            activeSession = activeSession,
            isMonitoring = activeSession != null,
            todayUsage = todayTotal,
            weekUsage = weekTotal,
            monthUsage = monthTotal,
            todayWifi = todayWifi,
            weekWifi = weekWifi,
            monthWifi = monthWifi,
            todayMobile = todayMobile,
            weekMobile = weekMobile,
            monthMobile = monthMobile,
            todayCost = todayCost,
            monthCost = monthCost
        )
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            if (_state.value.isMonitoring) {
                DataMonitorService.stop(application)
            } else {
                DataMonitorService.start(application, profileId)
            }
            delay(1500)
            refresh()
        }
    }
}
