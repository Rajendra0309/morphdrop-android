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

    fun setSearchFabVisibility(show: Boolean) {
        _showSearchFab.value = show
    }

    fun setOnSearchFabClick(onClick: (() -> Unit)?) {
        _onSearchFabClick.value = onClick
    }
}
