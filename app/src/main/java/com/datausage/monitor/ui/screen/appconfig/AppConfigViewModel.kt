package com.datausage.monitor.ui.screen.appconfig

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.entity.MonitoredAppEntity
import com.datausage.monitor.data.repository.UsageRepository
import com.datausage.monitor.util.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppConfigItem(
    val appInfo: AppInfo,
    val isMonitored: Boolean
)

@HiltViewModel
class AppConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val usageRepository: UsageRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _apps = MutableStateFlow<List<AppConfigItem>>(emptyList())
    val apps: StateFlow<List<AppConfigItem>> = _apps

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var allApps: List<AppConfigItem> = emptyList()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val installedApps = usageRepository.getInstalledApps()
            val monitoredApps = usageRepository.getMonitoredAppsList(profileId)
            val monitoredPackages = monitoredApps.map { it.packageName }.toSet()

            allApps = installedApps.map { app ->
                AppConfigItem(
                    appInfo = app,
                    isMonitored = app.packageName in monitoredPackages
                )
            }
            filterApps()
            _isLoading.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterApps()
    }

    private fun filterApps() {
        val query = _searchQuery.value.lowercase()
        _apps.value = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.appInfo.appName.lowercase().contains(query) ||
                    it.appInfo.packageName.lowercase().contains(query)
            }
        }
    }

    fun toggleApp(appInfo: AppInfo) {
        viewModelScope.launch {
            val current = allApps.find { it.appInfo.packageName == appInfo.packageName }
            if (current?.isMonitored == true) {
                usageRepository.removeMonitoredApp(profileId, appInfo.packageName)
            } else {
                usageRepository.addMonitoredApp(
                    profileId, appInfo.packageName, appInfo.appName, appInfo.uid
                )
            }

            allApps = allApps.map {
                if (it.appInfo.packageName == appInfo.packageName) {
                    it.copy(isMonitored = !it.isMonitored)
                } else it
            }
            filterApps()
        }
    }
}
