package com.morphdrop.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hasSeenWelcome: StateFlow<Boolean?> = settingsRepository.hasSeenWelcome
        .map { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null // Use null to indicate "loading" state
        )

    private val _showSearchFab = MutableStateFlow(false)
    val showSearchFab = _showSearchFab.asStateFlow()

    private val _onSearchFabClick = MutableStateFlow<(() -> Unit)?>(null)
    val onSearchFabClick = _onSearchFabClick.asStateFlow()

    fun setSearchFabVisibility(show: Boolean) {
        _showSearchFab.value = show
    }

    fun setOnSearchFabClick(onClick: (() -> Unit)?) {
        _onSearchFabClick.value = onClick
    }

    fun resetSearchFab() {
        _showSearchFab.value = false
        _onSearchFabClick.value = null
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasSeenWelcome(true)
        }
    }
}
