package com.datausage.monitor.ui.screen.limits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.entity.DataLimitEntity
import com.datausage.monitor.data.repository.LimitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LimitsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val limitRepository: LimitRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    val limits = limitRepository.getLimitsForProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    fun addLimit(
        packageName: String?,
        limitBytes: Long,
        periodType: String,
        warningPercent: Int,
        actionOnExceed: String
    ) {
        viewModelScope.launch {
            limitRepository.create(
                profileId = profileId,
                packageName = packageName?.ifBlank { null },
                limitBytes = limitBytes,
                periodType = periodType,
                warningPercent = warningPercent,
                actionOnExceed = actionOnExceed
            )
            _showAddDialog.value = false
        }
    }

    fun deleteLimit(limit: DataLimitEntity) {
        viewModelScope.launch {
            limitRepository.delete(limit)
        }
    }
}
