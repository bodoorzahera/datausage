package com.datausage.monitor.ui.screen.session

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.dao.AppUsageSummary
import com.datausage.monitor.data.local.db.entity.SessionEntity
import com.datausage.monitor.data.repository.SessionRepository
import com.datausage.monitor.data.repository.UsageRepository
import com.datausage.monitor.service.DataMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val sessionRepository: SessionRepository,
    private val usageRepository: UsageRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _activeSession = MutableStateFlow<SessionEntity?>(null)
    val activeSession: StateFlow<SessionEntity?> = _activeSession

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _appUsages = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    val appUsages: StateFlow<List<AppUsageSummary>> = _appUsages

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions

    init {
        viewModelScope.launch {
            sessionRepository.getSessionsForProfile(profileId).collect {
                _sessions.value = it
            }
        }
        viewModelScope.launch {
            refreshState()
        }
        // Periodic refresh
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                refreshState()
            }
        }
    }

    private suspend fun refreshState() {
        val session = sessionRepository.getActiveSession(profileId)
        _activeSession.value = session
        _isRunning.value = session != null

        if (session != null) {
            _appUsages.value = usageRepository.getUsageByAppForSession(session.sessionId)
        }
    }

    fun startSession() {
        viewModelScope.launch {
            DataMonitorService.start(application, profileId)
            delay(1000)
            refreshState()
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            DataMonitorService.stop(application)
            delay(1000)
            refreshState()
        }
    }
}
