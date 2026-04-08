package com.datausage.monitor.ui.screen.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datausage.monitor.data.local.db.entity.ProfileEntity
import com.datausage.monitor.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val profiles = profileRepository.getAllActiveProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            profileRepository.delete(profile)
        }
    }
}
