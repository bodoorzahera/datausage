package com.datausage.monitor.ui.screen.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import com.datausage.monitor.util.PermissionHelper
import com.datausage.monitor.util.PermissionStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SettingsState(
    val hasUsageStats: Boolean = false,
    val hasNotifications: Boolean = false,
    val hasReadPhoneState: Boolean = false,
    val isBatteryOptimized: Boolean = false,
    val nextPermission: PermissionStep? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val context = application
        _state.value = SettingsState(
            hasUsageStats = PermissionHelper.hasUsageStatsPermission(context),
            hasNotifications = PermissionHelper.hasNotificationPermission(context),
            hasReadPhoneState = PermissionHelper.hasReadPhoneStatePermission(context),
            isBatteryOptimized = PermissionHelper.isBatteryOptimizationExempt(context),
            nextPermission = PermissionHelper.getNextRequiredPermission(context)
        )
    }
}
