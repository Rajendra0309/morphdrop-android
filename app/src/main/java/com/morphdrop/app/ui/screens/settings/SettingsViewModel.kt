package com.morphdrop.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.BuildConfig
import com.morphdrop.app.domain.model.ThemeMode
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.ui.widget.WidgetUpdateHelper
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultOutputDirectory: String = "Downloads/MorphDrop",
    val cacheSizeFormatted: String = "0 B",
    val appVersion: String = "v${BuildConfig.VERSION_NAME}"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
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
                val cacheDirs = mutableListOf<File>()
                context.cacheDir?.let { cacheDirs.add(it) }
                context.externalCacheDir?.let { cacheDirs.add(it) }
                
                cacheDirs.sumOf { dir ->
                    if (dir.exists()) {
                        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else 0L
                }
            }
            _uiState.update { it.copy(cacheSizeFormatted = FileHelper.formatFileSize(size)) }
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cacheDirs = mutableListOf<File>()
                context.cacheDir?.let { cacheDirs.add(it) }
                context.externalCacheDir?.let { cacheDirs.add(it) }
                
                cacheDirs.forEach { dir ->
                    if (dir.exists()) {
                        dir.listFiles()?.forEach { file ->
                            try {
                                file.deleteRecursively()
                            } catch (e: Exception) {
                                // Ignore files that cannot be deleted
                            }
                        }
                    }
                }
            }
            calculateCacheSize(context)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun updateOutputFolderName(name: String) {
        viewModelScope.launch {
            settingsRepository.setOutputFolderName(name)
        }
    }
}
