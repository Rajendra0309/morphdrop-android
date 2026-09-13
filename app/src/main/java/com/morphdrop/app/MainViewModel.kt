package com.morphdrop.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.UpdateInfo
import com.morphdrop.app.domain.model.WhatsNewInfo
import com.morphdrop.app.BuildConfig
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.UpdateCheckUseCase
import com.morphdrop.app.data.updater.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateCheckUseCase: UpdateCheckUseCase,
    private val updateManager: UpdateManager,
    private val savedStateHandle: SavedStateHandle
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

    private val _whatsNewInfo = MutableStateFlow<WhatsNewInfo?>(savedStateHandle.get<WhatsNewInfo?>("whats_new_info"))
    val whatsNewInfo = _whatsNewInfo.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(savedStateHandle.get<UpdateInfo?>("update_info"))
    val updateInfo = _updateInfo.asStateFlow()

    private var pendingUpdateInfo: UpdateInfo?
        get() = savedStateHandle.get<UpdateInfo?>("pending_update_info")
        set(value) { savedStateHandle["pending_update_info"] = value }

    private fun setUpdateInfo(info: UpdateInfo?) {
        _updateInfo.value = info
        savedStateHandle["update_info"] = info
    }

    private fun setWhatsNewInfo(info: WhatsNewInfo?) {
        _whatsNewInfo.value = info
        savedStateHandle["whats_new_info"] = info
    }

    private val _updateEvents = MutableSharedFlow<UpdateEvent>()
    val updateEvents: SharedFlow<UpdateEvent> = _updateEvents.asSharedFlow()

    private val _downloadProgress = MutableStateFlow(com.morphdrop.app.data.updater.DownloadProgress())
    val downloadProgress = _downloadProgress.asStateFlow()

    sealed interface UpdateEvent {
        data class Error(val message: String) : UpdateEvent
        data object UpToDate : UpdateEvent
        data object Checking : UpdateEvent
        data class Generic(val message: String) : UpdateEvent
    }

    init {
        checkForUpdates(force = false)
        checkWhatsNew()
    }

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

    fun checkWhatsNew() {
        viewModelScope.launch {
            val hasSeenWelcome = settingsRepository.hasSeenWelcome.first()
            if (hasSeenWelcome) {
                // If an update is already available or pending, prioritize update dialog
                if (_updateInfo.value != null || pendingUpdateInfo != null) return@launch

                val lastSeenVersion = settingsRepository.lastSeenAppVersion.first()
                if (lastSeenVersion != BuildConfig.VERSION_NAME) {
                    loadWhatsNewFromGithub()
                }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasSeenWelcome(true)
            val pending = pendingUpdateInfo
            if (pending != null) {
                // Prioritize update dialog immediately upon reaching home screen
                setUpdateInfo(pending)
                pendingUpdateInfo = null
                setWhatsNewInfo(null)
            } else {
                val lastSeenVersion = settingsRepository.lastSeenAppVersion.first()
                if (lastSeenVersion != BuildConfig.VERSION_NAME) {
                    loadWhatsNewFromGithub()
                }
            }
        }
    }

    private fun loadWhatsNewFromGithub() {
        viewModelScope.launch {
            if (_updateInfo.value != null || pendingUpdateInfo != null) return@launch
            val notes = updateCheckUseCase.getReleaseNotes(BuildConfig.VERSION_NAME)
            if (notes.isNotBlank() && _updateInfo.value == null && pendingUpdateInfo == null) {
                setWhatsNewInfo(
                    WhatsNewInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        releaseNotes = notes
                    )
                )
            }
        }
    }

    fun dismissWhatsNewDialog() {
        viewModelScope.launch {
            setWhatsNewInfo(null)
            settingsRepository.setLastSeenAppVersion(BuildConfig.VERSION_NAME)
            // If an update was discovered while What's New was displaying, show it now
            pendingUpdateInfo?.let {
                setUpdateInfo(it)
                pendingUpdateInfo = null
            }
        }
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
                    val hasSeenWelcome = settingsRepository.hasSeenWelcome.first()
                    if (hasSeenWelcome) {
                        setUpdateInfo(info)
                        // Suppress what's new for the older version so update dialog shows first
                        setWhatsNewInfo(null)
                    } else {
                        // Queue update to show right after onboarding
                        pendingUpdateInfo = info
                    }
                } else if (force) {
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
        viewModelScope.launch {
            val downloadId = updateManager.downloadApk(info.downloadUrl, info.versionName)
            updateManager.pollDownloadProgress(downloadId).collect { progress ->
                _downloadProgress.value = progress
                if (progress.status == com.morphdrop.app.data.updater.DownloadStatus.SUCCESSFUL) {
                    kotlinx.coroutines.delay(1000)
                    setUpdateInfo(null)
                    _downloadProgress.value = com.morphdrop.app.data.updater.DownloadProgress()
                }
            }
        }
    }

    fun skipVersion(versionName: String) {
        viewModelScope.launch {
            settingsRepository.setSkippedUpdateVersion(versionName)
            setUpdateInfo(null)
            _downloadProgress.value = com.morphdrop.app.data.updater.DownloadProgress()
        }
    }

    fun dismissUpdateDialog() {
        setUpdateInfo(null)
        _downloadProgress.value = com.morphdrop.app.data.updater.DownloadProgress()
    }
}
