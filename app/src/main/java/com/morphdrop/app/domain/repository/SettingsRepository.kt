package com.morphdrop.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isDarkMode: Flow<Boolean>
    val outputFolderName: Flow<String>
    val hasSeenWelcome: Flow<Boolean>

    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setOutputFolderName(name: String)
    suspend fun setHasSeenWelcome(hasSeen: Boolean)
}
