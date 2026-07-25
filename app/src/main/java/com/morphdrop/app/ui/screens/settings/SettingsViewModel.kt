package com.morphdrop.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val defaultOutputDirectory: String = "Downloads/MorphDrop",
    val cacheSizeFormatted: String = "0 B",
    val appVersion: String = "v1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
        viewModelScope.launch {
            settingsRepository.outputFolderName.collect { folderName ->
                _uiState.update { it.copy(defaultOutputDirectory = folderName) }
            }
        }
    }

    fun calculateCacheSize(context: Context) {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                var totalBytes = 0L
                context.cacheDir?.listFiles()?.forEach { file ->
                    totalBytes += file.length()
                }
                totalBytes
            }
            _uiState.update { it.copy(cacheSizeFormatted = FileHelper.formatFileSize(size)) }
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir?.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }
            calculateCacheSize(context)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(enabled)
        }
    }

    fun updateOutputFolderName(name: String) {
        viewModelScope.launch {
            settingsRepository.setOutputFolderName(name)
        }
    }
}
