package com.morphdrop.app

import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.ThemeMode
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.ui.screens.history.resolveConversionType
import com.morphdrop.app.ui.screens.settings.SettingsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SettingsViewModelTest {

    private class FakeSettingsRepository : SettingsRepository {
        val themeFlow = MutableStateFlow(ThemeMode.SYSTEM)
        val folderFlow = MutableStateFlow("Downloads/MorphDrop")

        override val themeMode: Flow<ThemeMode> = themeFlow
        override val outputFolderName: Flow<String> = folderFlow
        override val hasSeenWelcome: Flow<Boolean> = MutableStateFlow(true)
        override val lastUpdateCheck: Flow<Long> = MutableStateFlow(0L)
        override val readingMode: Flow<ReadingMode> = MutableStateFlow(ReadingMode.DEFAULT)
        override val sepiaIntensity: Flow<Float> = MutableStateFlow(0.5f)
        override val markdownTextSize: Flow<Float> = MutableStateFlow(16f)
        override val lastImageFormat: Flow<String> = MutableStateFlow("PNG")
        override val lastImageQuality: Flow<Int> = MutableStateFlow(80)
        override val lastImageResizeOption: Flow<String> = MutableStateFlow("Original")
        override val lastStripMetadata: Flow<Boolean> = MutableStateFlow(false)
        override val hasSeenOcrDisclaimer: Flow<Boolean> = MutableStateFlow(true)
        override val skippedUpdateVersion: Flow<String> = MutableStateFlow("")

        override suspend fun setThemeMode(mode: ThemeMode) {
            themeFlow.value = mode
        }

        override suspend fun setOutputFolderName(name: String) {
            folderFlow.value = name
        }

        override suspend fun setHasSeenWelcome(hasSeen: Boolean) {}
        override suspend fun setLastUpdateCheck(timestamp: Long) {}
        override suspend fun setReadingMode(mode: ReadingMode) {}
        override suspend fun setSepiaIntensity(intensity: Float) {}
        override suspend fun setMarkdownTextSize(size: Float) {}
        override suspend fun setLastImageFormat(format: String) {}
        override suspend fun setLastImageQuality(quality: Int) {}
        override suspend fun setLastImageResizeOption(option: String) {}
        override suspend fun setLastStripMetadata(strip: Boolean) {}
        override suspend fun setHasSeenOcrDisclaimer(hasSeen: Boolean) {}
        override suspend fun setSkippedUpdateVersion(version: String) {}
    }

    @Test
    fun defaultSettingsUiState_hasCorrectInitialValues() {
        val state = SettingsUiState()
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals("Downloads/MorphDrop", state.defaultOutputDirectory)
        assertEquals("0 B", state.cacheSizeFormatted)
    }

    @Test
    fun fakeSettingsRepository_updatesThemeAndFolderCorrectly() = runBlocking {
        val repo = FakeSettingsRepository()

        assertEquals(ThemeMode.SYSTEM, repo.themeFlow.value)
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeFlow.value)

        assertEquals("Downloads/MorphDrop", repo.folderFlow.value)
        repo.setOutputFolderName("MorphDrop/Converted")
        assertEquals("MorphDrop/Converted", repo.folderFlow.value)
    }

    @Test
    fun resolveConversionType_correctlyResolvesAllTools() {
        val batchOcr = resolveConversionType("batch_ocr")
        assertNotNull(batchOcr)
        assertEquals("batch_ocr", batchOcr?.id)

        val markdownEditor = resolveConversionType("markdown_editor")
        assertNotNull(markdownEditor)
        assertEquals("markdown_editor", markdownEditor?.id)

        val watermarkPdf = resolveConversionType("watermark_pdf")
        assertNotNull(watermarkPdf)
        assertEquals("watermark_pdf", watermarkPdf?.id)

        val pageNumbers = resolveConversionType("page_numbers_pdf")
        assertNotNull(pageNumbers)
        assertEquals("page_numbers_pdf", pageNumbers?.id)

        val compressPdf = resolveConversionType("compress_pdf")
        assertNotNull(compressPdf)
        assertEquals("compress_pdf", compressPdf?.id)

        val rotatePdf = resolveConversionType("rotate_pdf")
        assertNotNull(rotatePdf)
        assertEquals("rotate_pdf", rotatePdf?.id)

        val pdfToImages = resolveConversionType("pdf_to_images")
        assertNotNull(pdfToImages)
        assertEquals("pdf_to_images", pdfToImages?.id)
    }
}
