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
    val themeMode: StateFlow<com.morphdrop.app.domain.model.ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.morphdrop.app.domain.model.ThemeMode.SYSTEM
        )

    val hasSeenWelcome: StateFlow<Boolean?> = settingsRepository.hasSeenWelcome
        .map { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null // Use null to indicate "loading" state
        )

    private val _showSearchFab = MutableStateFlow(false)
    val showSearchFab = _showSearchFab.asStateFlow()

    private val _onSearchFabClick = MutableStateFlow<(() -> Unit)?>(null)
    val onSearchFabClick = _onSearchFabClick.asStateFlow()

    // Persistent state for scroll-based visibility to prevent jitter on navigation
    private val _isSearchFabVisibleByScroll = MutableStateFlow(false)
    val isSearchFabVisibleByScroll = _isSearchFabVisibleByScroll.asStateFlow()

    fun setSearchFabVisibility(show: Boolean) {
        _isSearchFabVisibleByScroll.value = show
        _showSearchFab.value = show
    }

    fun setOnSearchFabClick(onClick: (() -> Unit)?) {
        _onSearchFabClick.value = onClick
    }

    fun resetSearchFab() {
        _showSearchFab.value = false
        _isSearchFabVisibleByScroll.value = false
        _onSearchFabClick.value = null
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasSeenWelcome(true)
        }
    }

    private var lastKnownSystemDark: Boolean? = null

    fun onSystemThemeChanged(isSystemDark: Boolean) {
        if (lastKnownSystemDark != null && lastKnownSystemDark != isSystemDark) {
            // System theme changed! Reset manual override so app follows system again.
            viewModelScope.launch {
                settingsRepository.setThemeMode(com.morphdrop.app.domain.model.ThemeMode.SYSTEM)
            }
        }
        lastKnownSystemDark = isSystemDark
    }
}
