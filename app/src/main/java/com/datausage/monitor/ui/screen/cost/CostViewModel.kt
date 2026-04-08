package com.datausage.monitor.ui.screen.cost

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.entity.CostConfigEntity
import com.datausage.monitor.data.repository.CostRepository
import com.datausage.monitor.data.repository.CostSummary
import com.datausage.monitor.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CostState(
    val config: CostConfigEntity? = null,
    val todayCost: CostSummary? = null,
    val weekCost: CostSummary? = null,
    val monthCost: CostSummary? = null,
    val saved: Boolean = false
)

@HiltViewModel
class CostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val costRepository: CostRepository
) : ViewModel() {

    val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _state = MutableStateFlow(CostState())
    val state: StateFlow<CostState> = _state

    private val _costPerUnit = MutableStateFlow("")
    val costPerUnit: StateFlow<String> = _costPerUnit

    private val _unitType = MutableStateFlow("GB") // GB or MB
    val unitType: StateFlow<String> = _unitType

    private val _currency = MutableStateFlow("USD")
    val currency: StateFlow<String> = _currency

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            costRepository.getCostConfig(profileId).collect { config ->
                _state.value = _state.value.copy(config = config)
                if (config != null) {
                    _costPerUnit.value = config.costPerUnit.toString()
                    _unitType.value = if (config.unitBytes >= 1_073_741_824L) "GB" else "MB"
                    _currency.value = config.currency
                }
            }
        }
        refreshCosts()
    }

    private fun refreshCosts() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val todayCost = costRepository.calculateCostForPeriod(
                profileId, FormatUtils.startOfDay(now), FormatUtils.endOfDay(now)
            )
            val weekCost = costRepository.calculateCostForPeriod(
                profileId, FormatUtils.startOfWeek(now), FormatUtils.endOfWeek(now)
            )
            val monthCost = costRepository.calculateCostForPeriod(
                profileId, FormatUtils.startOfMonth(now), FormatUtils.endOfMonth(now)
            )
            _state.value = _state.value.copy(
                todayCost = todayCost,
                weekCost = weekCost,
                monthCost = monthCost
            )
        }
    }

    fun onCostPerUnitChange(value: String) { _costPerUnit.value = value }
    fun onUnitTypeChange(value: String) { _unitType.value = value }
    fun onCurrencyChange(value: String) { _currency.value = value }

    fun save() {
        viewModelScope.launch {
            val cost = _costPerUnit.value.toDoubleOrNull() ?: return@launch
            val unitBytes = if (_unitType.value == "GB") 1_073_741_824L else 1_048_576L

            costRepository.saveCostConfig(
                profileId = profileId,
                costPerUnit = cost,
                unitBytes = unitBytes,
                currency = _currency.value
            )
            _state.value = _state.value.copy(saved = true)
            refreshCosts()
        }
    }
}
