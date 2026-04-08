package com.datausage.monitor.ui.screen.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L
    val isEditing = profileId > 0

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    init {
        if (isEditing) {
            viewModelScope.launch {
                profileRepository.getById(profileId)?.let { profile ->
                    _name.value = profile.name
                    _pin.value = profile.pin ?: ""
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
    }

    fun onPinChange(value: String) {
        _pin.value = value
    }

    fun save() {
        viewModelScope.launch {
            val name = _name.value.trim()
            if (name.isEmpty()) return@launch

            val pin = _pin.value.trim().ifEmpty { null }

            if (isEditing) {
                profileRepository.getById(profileId)?.let { existing ->
                    profileRepository.update(existing.copy(name = name, pin = pin))
                }
            } else {
                profileRepository.create(name, pin)
            }
            _saved.value = true
        }
    }
}
