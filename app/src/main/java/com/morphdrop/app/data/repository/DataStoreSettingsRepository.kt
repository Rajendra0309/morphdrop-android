package com.morphdrop.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.morphdrop.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.morphdrop.app.domain.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OUTPUT_FOLDER_NAME = stringPreferencesKey("output_folder_name")
        val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
    }

    override val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeString)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override val outputFolderName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.OUTPUT_FOLDER_NAME] ?: "MorphDrop"
    }

    override val hasSeenWelcome: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAS_SEEN_WELCOME] ?: false
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setOutputFolderName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OUTPUT_FOLDER_NAME] = name
        }
    }

    override suspend fun setHasSeenWelcome(hasSeen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_WELCOME] = hasSeen
        }
    }
}
