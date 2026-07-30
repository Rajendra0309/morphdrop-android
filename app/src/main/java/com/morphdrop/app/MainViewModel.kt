package com.morphdrop.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _showSearchFab = MutableStateFlow(false)
    val showSearchFab = _showSearchFab.asStateFlow()

    private val _onSearchFabClick = MutableStateFlow<(() -> Unit)?>(null)
    val onSearchFabClick = _onSearchFabClick.asStateFlow()

    private var currentOwner: String? = null

    fun setSearchFabVisibility(show: Boolean, owner: String) {
        if (show) {
            currentOwner = owner
            _showSearchFab.value = true
        } else if (currentOwner == owner) {
            _showSearchFab.value = false
        }
    }

    fun setOnSearchFabClick(onClick: (() -> Unit)?, owner: String) {
        if (onClick != null) {
            currentOwner = owner
            _onSearchFabClick.value = onClick
        } else if (currentOwner == owner) {
            _onSearchFabClick.value = null
        }
    }

    fun resetSearchFab() {
        _showSearchFab.value = false
        _onSearchFabClick.value = null
        currentOwner = null
    }
}
