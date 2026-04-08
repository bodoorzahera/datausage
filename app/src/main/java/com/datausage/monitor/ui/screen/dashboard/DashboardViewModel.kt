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

data class DashboardState(
    val profile: ProfileEntity? = null,
    val activeSession: SessionEntity? = null,
    val isMonitoring: Boolean = false,
    val todayUsage: Long = 0,
    val weekUsage: Long = 0,
    val monthUsage: Long = 0,
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

    private suspend fun refresh() {
        val profile = profileRepository.getById(profileId)
        val activeSession = sessionRepository.getActiveSession(profileId)
        val now = System.currentTimeMillis()

        val todayUsage = sessionRepository.getTotalUsageInRange(
            profileId, FormatUtils.startOfDay(now), FormatUtils.endOfDay(now)
        )
        val weekUsage = sessionRepository.getTotalUsageInRange(
            profileId, FormatUtils.startOfWeek(now), FormatUtils.endOfWeek(now)
        )
        val monthUsage = sessionRepository.getTotalUsageInRange(
            profileId, FormatUtils.startOfMonth(now), FormatUtils.endOfMonth(now)
        )

        val todayCost = costRepository.calculateCostForPeriod(
            profileId, FormatUtils.startOfDay(now), FormatUtils.endOfDay(now)
        )
        val monthCost = costRepository.calculateCostForPeriod(
            profileId, FormatUtils.startOfMonth(now), FormatUtils.endOfMonth(now)
        )

        _state.value = DashboardState(
            profile = profile,
            activeSession = activeSession,
            isMonitoring = activeSession != null,
            todayUsage = todayUsage,
            weekUsage = weekUsage,
            monthUsage = monthUsage,
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
