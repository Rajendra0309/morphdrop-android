package com.morphdrop.app.domain.repository

import com.morphdrop.app.domain.model.ReadingMode
import kotlinx.coroutines.flow.Flow
import com.morphdrop.app.domain.model.ThemeMode

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val outputFolderName: Flow<String>
    val hasSeenWelcome: Flow<Boolean>
    val lastUpdateCheck: Flow<Long>
    val readingMode: Flow<ReadingMode>
    val sepiaIntensity: Flow<Float>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOutputFolderName(name: String)
    suspend fun setHasSeenWelcome(hasSeen: Boolean)
    suspend fun setLastUpdateCheck(timestamp: Long)
    suspend fun setReadingMode(mode: ReadingMode)
    suspend fun setSepiaIntensity(intensity: Float)
}
