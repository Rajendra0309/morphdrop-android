package com.morphdrop.app.domain.repository

import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.ThemeMode
import com.morphdrop.app.domain.model.LastOpenedPdf
import com.morphdrop.app.domain.model.ToolPreset
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
    val skippedUpdateVersion: Flow<String>
    val lastSeenAppVersion: Flow<String>
    val dynamicColorEnabled: Flow<Boolean>

    /** Last PDF opened in the viewer + page, for the Home continue-reading card. */
    val lastOpenedPdf: Flow<LastOpenedPdf?>

    /** Per-tool conversion preset (last-used settings), keyed by tool id. */
    fun toolPreset(toolId: String): Flow<ToolPreset?>

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
    suspend fun setSkippedUpdateVersion(version: String)
    suspend fun setLastSeenAppVersion(version: String)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
    suspend fun saveLastOpenedPdf(
        uri: String,
        page: Int,
        displayName: String = "",
        totalPages: Int = 0
    )
    suspend fun clearLastOpenedPdf()
    suspend fun saveToolPreset(toolId: String, preset: ToolPreset)
}
