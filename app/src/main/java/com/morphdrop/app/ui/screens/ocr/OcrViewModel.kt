package com.morphdrop.app.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.conversion.OcrUseCase
import com.morphdrop.app.domain.usecase.history.SaveHistoryUseCase
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.PdfThumbnailHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.morphdrop.app.domain.model.OcrScript
import javax.inject.Inject

data class OcrUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSizeFormatted: String = "",
    val isPdf: Boolean = false,
    val pdfPageCount: Int = 1,
    val selectedPage: Int = 1,
    val extractAllPages: Boolean = false,
    val selectedScript: OcrScript = OcrScript.LATIN,
    val isExtracting: Boolean = false,
    val progressText: String = "",
    val progressFraction: Float = 0f,
    val extractedText: String = "",
    val pageTextCache: Map<Int, String> = emptyMap(),
    val currentThumbnailBitmap: Bitmap? = null,
    val isThumbnailLoading: Boolean = false,
    val showSaveFileNameDialog: Boolean = false,
    val customSaveFileName: String = "",
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val savedFileUri: Uri? = null,
    val showDisclaimerDialog: Boolean = false,
    val hasAcknowledgedDisclaimer: Boolean = false
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrUseCase: OcrUseCase,
    private val settingsRepository: SettingsRepository,
    private val saveHistoryUseCase: SaveHistoryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hasSeen = settingsRepository.hasSeenOcrDisclaimer.first()
            _state.update { it.copy(hasAcknowledgedDisclaimer = hasSeen) }
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        val name = FileHelper.getFileName(context, uri)
        val size = FileHelper.getFileSize(context, uri)
        val sizeFormatted = FileHelper.formatFileSize(size)
        val mime = FileHelper.getMimeType(context, uri)
        val isImage = mime.startsWith("image/", ignoreCase = true)
        val isPdfFile = !isImage && (mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true))

        val baseName = FileHelper.getFileNameWithoutExtension(name).ifBlank { "extracted_text" }
        val defaultSaveName = "${baseName}_ocr.txt"

        viewModelScope.launch {
            val pageCount = if (isPdfFile) {
                val count = ocrUseCase.getPdfPageCount(uri)
                if (count > 0) count else 1
            } else {
                1
            }

            val hasSeen = settingsRepository.hasSeenOcrDisclaimer.first()

            _state.update {
                it.copy(
                    selectedUri = uri,
                    fileName = name,
                    fileSizeFormatted = sizeFormatted,
                    isPdf = isPdfFile,
                    pdfPageCount = pageCount,
                    selectedPage = 1,
                    extractAllPages = false,
                    extractedText = "",
                    pageTextCache = emptyMap(),
                    customSaveFileName = defaultSaveName,
                    wordCount = 0,
                    charCount = 0,
                    errorMessage = null,
                    infoMessage = null,
                    savedFileUri = null,
                    showDisclaimerDialog = !hasSeen
                )
            }

            loadThumbnail(context, uri, isPdfFile, pageIndex = 0)
        }
    }

    fun onPageSelected(context: Context, page: Int) {
        val clamped = page.coerceIn(1, _state.value.pdfPageCount)
        if (_state.value.selectedPage != clamped) {
            _state.update { it.copy(selectedPage = clamped) }
            val uri = _state.value.selectedUri
            if (uri != null && _state.value.isPdf) {
                viewModelScope.launch {
                    loadThumbnail(context, uri, isPdf = true, pageIndex = clamped - 1)
                }
            }
        }
    }

    fun onToggleExtractAllPages(extractAll: Boolean) {
        _state.update { it.copy(extractAllPages = extractAll) }
    }

    fun onScriptSelected(script: OcrScript) {
        _state.update { it.copy(selectedScript = script) }
    }

    private suspend fun loadThumbnail(context: Context, uri: Uri, isPdf: Boolean, pageIndex: Int) {
        _state.update { it.copy(isThumbnailLoading = true) }
        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            try {
                if (isPdf) {
                    PdfThumbnailHelper.getThumbnail(context, uri, pageIndex)
                } else {
                    ocrUseCase.loadUniversalScaledBitmap(context, uri)
                }
            } catch (_: Exception) {
                null
            }
        }
        _state.update {
            it.copy(
                currentThumbnailBitmap = bitmap,
                isThumbnailLoading = false
            )
        }
    }

    fun startExtraction() {
        val currentUri = _state.value.selectedUri ?: return
        val isPdf = _state.value.isPdf
        val extractAll = _state.value.extractAllPages
        val targetPage = _state.value.selectedPage
        val targetScript = _state.value.selectedScript

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isExtracting = true,
                    errorMessage = null,
                    infoMessage = null,
                    progressText = "Preparing for text extraction...",
                    progressFraction = 0f
                )
            }

            val result: Result<String> = if (!isPdf) {
                _state.update { it.copy(progressText = "Extracting text from image...") }
                ocrUseCase.extractFromImageUri(currentUri, targetScript)
            } else if (!extractAll) {
                val cachedText = _state.value.pageTextCache[targetPage]
                if (cachedText != null) {
                    Result.success(cachedText)
                } else {
                    _state.update { it.copy(progressText = "Extracting text from page $targetPage of ${_state.value.pdfPageCount}...") }
                    val res = ocrUseCase.extractFromPdfPage(currentUri, targetPage - 1, targetScript)
                    if (res.isSuccess) {
                        val text = res.getOrDefault("")
                        _state.update { state ->
                            state.copy(pageTextCache = state.pageTextCache + (targetPage to text))
                        }
                    }
                    res
                }
            } else {
                ocrUseCase.extractFromPdfAllPages(currentUri, targetScript) { current, total ->
                    val fraction = current.toFloat() / total.toFloat()
                    _state.update { state ->
                        state.copy(
                            progressText = "Extracting page $current of $total...",
                            progressFraction = fraction
                        )
                    }
                }
            }

            if (result.isSuccess) {
                val text = result.getOrDefault("")
                val words = calculateWordCount(text)
                val chars = text.length
                _state.update {
                    it.copy(
                        isExtracting = false,
                        extractedText = text,
                        wordCount = words,
                        charCount = chars,
                        progressFraction = 1f
                    )
                }
            } else {
                val exception = result.exceptionOrNull()
                val message = exception?.message ?: "Text extraction failed. Please try again."
                _state.update {
                    it.copy(
                        isExtracting = false,
                        errorMessage = message,
                        progressFraction = 0f
                    )
                }
            }
        }
    }

    fun copyToClipboard(context: Context) {
        val text = _state.value.extractedText
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Extracted Text", text)
            clipboard.setPrimaryClip(clip)
            _state.update { it.copy(infoMessage = "Copied to clipboard") }
        }
    }

    fun openSaveFileNameDialog() {
        if (_state.value.extractedText.isNotEmpty()) {
            val name = _state.value.fileName
            val baseName = FileHelper.getFileNameWithoutExtension(name).ifBlank { "extracted_text" }
            _state.update {
                it.copy(
                    showSaveFileNameDialog = true,
                    customSaveFileName = if (it.customSaveFileName.isBlank()) "${baseName}_ocr.txt" else it.customSaveFileName
                )
            }
        }
    }

    fun onCustomFileNameChange(newName: String) {
        _state.update { it.copy(customSaveFileName = newName) }
    }

    fun dismissSaveFileNameDialog() {
        _state.update { it.copy(showSaveFileNameDialog = false) }
    }

    fun confirmSaveAsTxt(context: Context) {
        val text = _state.value.extractedText
        val originalName = _state.value.fileName
        val rawInput = _state.value.customSaveFileName.trim()

        if (text.isEmpty() || rawInput.isEmpty()) return

        val outputFileName = if (rawInput.endsWith(".txt", ignoreCase = true)) rawInput else "$rawInput.txt"

        viewModelScope.launch {
            try {
                val textBytes = text.toByteArray(Charsets.UTF_8)

                val savedUri = FileHelper.saveToFile(
                    context = context,
                    settingsRepository = settingsRepository,
                    fileName = outputFileName,
                    data = textBytes
                )

                // Save history record with outputFileName as displayName so History screen displays actual text filename!
                saveHistoryUseCase(
                    ConversionHistoryEntity(
                        conversionType = "ocr_text_extractor",
                        inputFileName = originalName,
                        outputFileNames = outputFileName,
                        outputUris = savedUri.toString(),
                        displayName = outputFileName,
                        success = true
                    )
                )

                // Default Toast notification only (no duplicate black snackbar!)
                _state.update {
                    it.copy(
                        savedFileUri = savedUri,
                        showSaveFileNameDialog = false,
                        infoMessage = "Saved as $outputFileName to Downloads/MorphDrop"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        showSaveFileNameDialog = false,
                        errorMessage = "Failed to save file: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun shareText(context: Context) {
        val text = _state.value.extractedText
        if (text.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Extracted Text - MorphDrop")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(intent, "Share text via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    fun dismissDisclaimer() {
        viewModelScope.launch {
            settingsRepository.setHasSeenOcrDisclaimer(true)
            _state.update {
                it.copy(
                    hasAcknowledgedDisclaimer = true,
                    showDisclaimerDialog = false
                )
            }
        }
    }

    fun clearMessages() {
        _state.update {
            it.copy(
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    private fun calculateWordCount(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
    }
}
