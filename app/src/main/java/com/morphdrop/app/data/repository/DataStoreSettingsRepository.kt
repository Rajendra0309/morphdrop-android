package com.morphdrop.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.ThemeMode
import com.morphdrop.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OUTPUT_FOLDER_NAME = stringPreferencesKey("output_folder_name")
        val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val SEPIA_INTENSITY = floatPreferencesKey("sepia_intensity")
        val MARKDOWN_TEXT_SIZE = floatPreferencesKey("markdown_text_size")
        val LAST_IMAGE_FORMAT = stringPreferencesKey("last_image_format")
        val LAST_IMAGE_QUALITY = intPreferencesKey("last_image_quality")
        val LAST_IMAGE_RESIZE_OPTION = stringPreferencesKey("last_image_resize_option")
        val LAST_STRIP_METADATA = booleanPreferencesKey("last_strip_metadata")
        val HAS_SEEN_OCR_DISCLAIMER = booleanPreferencesKey("has_seen_ocr_disclaimer")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
    }

    override val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeString)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override val outputFolderName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.OUTPUT_FOLDER_NAME] ?: "MorphDrop"
    }

    override val hasSeenWelcome: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAS_SEEN_WELCOME] ?: false
    }

    override val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_UPDATE_CHECK] ?: 0L
    }

    override val readingMode: Flow<ReadingMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[PreferencesKeys.READING_MODE] ?: ReadingMode.DEFAULT.name
        try {
            ReadingMode.valueOf(modeString)
        } catch (_: Exception) {
            ReadingMode.DEFAULT
        }
    }

    override val sepiaIntensity: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SEPIA_INTENSITY] ?: 0.5f
    }

    override val markdownTextSize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MARKDOWN_TEXT_SIZE] ?: 16f
    }

    override val lastImageFormat: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_IMAGE_FORMAT] ?: "jpg"
    }

    override val lastImageQuality: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_IMAGE_QUALITY] ?: 90
    }

    override val lastImageResizeOption: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_IMAGE_RESIZE_OPTION] ?: "Original"
    }

    override val lastStripMetadata: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_STRIP_METADATA] ?: false
    }

    override val hasSeenOcrDisclaimer: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAS_SEEN_OCR_DISCLAIMER] ?: false
    }

    override val skippedUpdateVersion: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SKIPPED_UPDATE_VERSION] ?: ""
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

    override suspend fun setLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK] = timestamp
        }
    }

    override suspend fun setReadingMode(mode: ReadingMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.READING_MODE] = mode.name
        }
    }

    override suspend fun setSepiaIntensity(intensity: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEPIA_INTENSITY] = intensity
        }
    }

    override suspend fun setMarkdownTextSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MARKDOWN_TEXT_SIZE] = size
        }
    }

    override suspend fun setLastImageFormat(format: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_IMAGE_FORMAT] = format
        }
    }

    override suspend fun setLastImageQuality(quality: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_IMAGE_QUALITY] = quality
        }
    }

    override suspend fun setLastImageResizeOption(option: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_IMAGE_RESIZE_OPTION] = option
        }
    }

    override suspend fun setLastStripMetadata(strip: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_STRIP_METADATA] = strip
        }
    }

    override suspend fun setHasSeenOcrDisclaimer(hasSeen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_OCR_DISCLAIMER] = hasSeen
        }
    }

    override suspend fun setSkippedUpdateVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIPPED_UPDATE_VERSION] = version
        }
    }
}
