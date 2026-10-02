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
import com.morphdrop.app.domain.model.LastOpenedPdf
import com.morphdrop.app.domain.model.ToolPreset
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
        val LAST_SEEN_APP_VERSION = stringPreferencesKey("last_seen_app_version")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val LAST_PDF_URI = stringPreferencesKey("last_pdf_uri")
        val LAST_PDF_PAGE = intPreferencesKey("last_pdf_page")
        val LAST_PDF_TIMESTAMP = longPreferencesKey("last_pdf_timestamp")
        val LAST_PDF_NAME = stringPreferencesKey("last_pdf_name")
        val LAST_PDF_PAGES = intPreferencesKey("last_pdf_pages")
        /** Per-tool preset keys are dynamic: "tool_preset_<toolId>". */
        fun toolPresetKey(toolId: String) = stringPreferencesKey("tool_preset_$toolId")
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

    override val lastSeenAppVersion: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_SEEN_APP_VERSION] ?: ""
    }

    override suspend fun setLastSeenAppVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SEEN_APP_VERSION] = version
        }
    }

    /**
     * "Match wallpaper" dynamic color. Defaults to true on Android 12+ (the
     * theme layer ignores it on older versions); the user can turn it off in
     * Settings > Appearance.
     */
    override val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] ?: true
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    /**
     * Last PDF opened in the viewer + the page the user left off on. Powers
     * the Home "Pick up where you left off" card.
     */
    override val lastOpenedPdf: Flow<LastOpenedPdf?> = context.dataStore.data.map { preferences ->
        val uri = preferences[PreferencesKeys.LAST_PDF_URI]
        if (uri.isNullOrBlank()) {
            null
        } else {
            LastOpenedPdf(
                uri = uri,
                page = preferences[PreferencesKeys.LAST_PDF_PAGE] ?: 0,
                timestamp = preferences[PreferencesKeys.LAST_PDF_TIMESTAMP] ?: 0L,
                displayName = preferences[PreferencesKeys.LAST_PDF_NAME] ?: "",
                totalPages = preferences[PreferencesKeys.LAST_PDF_PAGES] ?: 0
            )
        }
    }

    override suspend fun saveLastOpenedPdf(
        uri: String,
        page: Int,
        displayName: String,
        totalPages: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PDF_URI] = uri
            preferences[PreferencesKeys.LAST_PDF_PAGE] = page.coerceAtLeast(0)
            preferences[PreferencesKeys.LAST_PDF_TIMESTAMP] = System.currentTimeMillis()
            preferences[PreferencesKeys.LAST_PDF_NAME] = displayName
            preferences[PreferencesKeys.LAST_PDF_PAGES] = totalPages.coerceAtLeast(0)
        }
    }

    override suspend fun clearLastOpenedPdf() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_PDF_URI)
            preferences.remove(PreferencesKeys.LAST_PDF_PAGE)
            preferences.remove(PreferencesKeys.LAST_PDF_TIMESTAMP)
            preferences.remove(PreferencesKeys.LAST_PDF_NAME)
            preferences.remove(PreferencesKeys.LAST_PDF_PAGES)
        }
    }

    /**
     * Per-tool conversion presets (last-used settings), keyed by normalized
     * tool id. Stored as a compact JSON string per tool.
     */
    override fun toolPreset(toolId: String): Flow<ToolPreset?> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.toolPresetKey(toolId)]?.let { ToolPreset.fromJson(it) }
        }

    override suspend fun saveToolPreset(toolId: String, preset: ToolPreset) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.toolPresetKey(toolId)] = preset.toJson()
        }
    }
}
