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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val OUTPUT_FOLDER_NAME = stringPreferencesKey("output_folder_name")
    }

    override val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_DARK_MODE] ?: false
    }

    override val outputFolderName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.OUTPUT_FOLDER_NAME] ?: "MorphDrop"
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_MODE] = enabled
        }
    }

    override suspend fun setOutputFolderName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OUTPUT_FOLDER_NAME] = name
        }
    }
}
