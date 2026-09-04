package com.morphdrop.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.UpdateInfo
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.UpdateCheckUseCase
import com.morphdrop.app.data.updater.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateCheckUseCase: UpdateCheckUseCase,
    private val updateManager: UpdateManager
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

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _updateEvents = MutableSharedFlow<UpdateEvent>()
    val updateEvents: SharedFlow<UpdateEvent> = _updateEvents.asSharedFlow()

    sealed interface UpdateEvent {
        data class Error(val message: String) : UpdateEvent
        data object UpToDate : UpdateEvent
        data object Checking : UpdateEvent
        data class Generic(val message: String) : UpdateEvent
    }


    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            if (force) _updateEvents.emit(UpdateEvent.Checking)
            
            val startTime = System.currentTimeMillis()
            updateCheckUseCase(force).onSuccess { info ->
                // Ensure "Checking" state is visible for at least 800ms for smoothness
                if (force) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 800) kotlinx.coroutines.delay(800 - elapsedTime)
                }

                if (info.isUpdateAvailable) {
                    _updateInfo.value = info
                } else if (force || info.versionName.isNotEmpty()) {
                    _updateEvents.emit(UpdateEvent.UpToDate)
                }
            }.onFailure {
                if (force) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < 800) kotlinx.coroutines.delay(800 - elapsedTime)
                    _updateEvents.emit(UpdateEvent.Error("Unable to check for updates"))
                }
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        updateManager.downloadApk(info.downloadUrl, info.versionName)
        _updateInfo.value = null
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }
}
