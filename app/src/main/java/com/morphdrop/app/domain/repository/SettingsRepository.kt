package com.morphdrop.app.domain.repository

import kotlinx.coroutines.flow.Flow
import com.morphdrop.app.domain.model.ThemeMode

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val outputFolderName: Flow<String>
    val hasSeenWelcome: Flow<Boolean>
    val lastUpdateCheck: Flow<Long>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOutputFolderName(name: String)
    suspend fun setHasSeenWelcome(hasSeen: Boolean)
    suspend fun setLastUpdateCheck(timestamp: Long)
}
