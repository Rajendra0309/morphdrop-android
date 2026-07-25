package com.morphdrop.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isDarkMode: Flow<Boolean>
    val outputFolderName: Flow<String>

    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setOutputFolderName(name: String)
}
