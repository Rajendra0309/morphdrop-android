package com.morphdrop.app.domain.repository

import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val outputFolderName: Flow<String>
    val hasSeenWelcome: Flow<Boolean>
    val lastUpdateCheck: Flow<Long>
    val readingMode: Flow<ReadingMode>
    val sepiaIntensity: Flow<Float>
    val markdownTextSize: Flow<Float>
    val lastImageFormat: Flow<String>
    val lastImageQuality: Flow<Int>
    val lastImageResizeOption: Flow<String>
    val lastStripMetadata: Flow<Boolean>
    val hasSeenOcrDisclaimer: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOutputFolderName(name: String)
    suspend fun setHasSeenWelcome(hasSeen: Boolean)
    suspend fun setLastUpdateCheck(timestamp: Long)
    suspend fun setReadingMode(mode: ReadingMode)
    suspend fun setSepiaIntensity(intensity: Float)
    suspend fun setMarkdownTextSize(size: Float)
    suspend fun setLastImageFormat(format: String)
    suspend fun setLastImageQuality(quality: Int)
    suspend fun setLastImageResizeOption(option: String)
    suspend fun setLastStripMetadata(strip: Boolean)
    suspend fun setHasSeenOcrDisclaimer(hasSeen: Boolean)
}
