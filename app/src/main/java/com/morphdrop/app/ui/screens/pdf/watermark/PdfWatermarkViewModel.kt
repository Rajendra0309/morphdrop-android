package com.morphdrop.app.ui.screens.pdf.watermark

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.WatermarkPosition
import com.morphdrop.app.domain.model.WatermarkType
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.usecase.conversion.WatermarkPdfUseCase
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.PdfThumbnailHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PdfWatermarkUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val pageCount: Int = 1,
    val previewBitmap: Bitmap? = null,
    val isPreviewLoading: Boolean = false,
    val config: WatermarkConfig = WatermarkConfig(
        type = WatermarkType.TEXT,
        text = "CONFIDENTIAL",
        fontSizeSp = 36f,
        fontColor = 0xFF888888,
        opacity = 0.35f,
        rotationDegrees = 45f,
        position = WatermarkPosition.DIAGONAL,
        skipFirstPage = false
    ),
    val customRangeText: String = "",
    val isProcessing: Boolean = false,
    val isSuccess: Boolean = false,
    val resultUri: Uri? = null,
    val resultFileName: String = "",
    val resultFileSize: Long = 0L,
    val errorMessage: String? = null
)

@HiltViewModel
class PdfWatermarkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val watermarkPdfUseCase: WatermarkPdfUseCase,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val initialUriString: String? = savedStateHandle["uri"]

    private val _state = MutableStateFlow(PdfWatermarkUiState())
    val state: StateFlow<PdfWatermarkUiState> = _state.asStateFlow()

    init {
        if (!initialUriString.isNullOrBlank() && initialUriString != "{uri}") {
            try {
                val uri = Uri.parse(initialUriString)
                if (!uri.scheme.isNullOrBlank()) {
                    onPdfSelected(uri)
                }
            } catch (_: Exception) {}
        }
    }

    fun onPdfSelected(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedUri = uri,
                    fileName = FileHelper.getFileName(context, uri),
                    fileSize = FileHelper.getFileSize(context, uri),
                    isPreviewLoading = true,
                    errorMessage = null,
                    isSuccess = false,
                    resultUri = null
                )
            }

            // Calculate page count and render page 0 preview bitmap
            val count = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                            renderer.pageCount
                        }
                    } ?: 1
                } catch (_: Exception) { 1 }
            }

            val bitmap = PdfThumbnailHelper.getThumbnail(context, uri, 0)

            _state.update {
                it.copy(
                    pageCount = count,
                    previewBitmap = bitmap,
                    isPreviewLoading = false
                )
            }
        }
    }

    fun updateConfig(update: (WatermarkConfig) -> WatermarkConfig) {
        _state.update { it.copy(config = update(it.config)) }
    }

    fun onCustomRangeTextChanged(text: String) {
        _state.update { it.copy(customRangeText = text) }
    }

    private var processingJob: kotlinx.coroutines.Job? = null

    fun applyWatermark() {
        val uri = _state.value.selectedUri ?: return
        if (_state.value.isProcessing) return

        processingJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }

            try {
                val baseName = _state.value.fileName.substringBeforeLast('.')
                val outName = "${baseName}_watermarked.pdf"

                // Parse custom page ranges if provided
                val targetPages = parsePageRange(_state.value.customRangeText, _state.value.pageCount)
                val finalConfig = _state.value.config.copy(targetPages = targetPages)

                val outputUri = watermarkPdfUseCase(
                    pdfUri = uri,
                    config = finalConfig,
                    outputFileName = outName
                )

                val outSize = FileHelper.getFileSize(context, outputUri).let { if (it <= 0) _state.value.fileSize else it }

                // Add to history
                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "watermark_pdf",
                            inputFileName = _state.value.fileName,
                            outputFileNames = outName,
                            outputUris = outputUri.toString(),
                            displayName = outName,
                            success = true
                        )
                    )
                } catch (_: Exception) {}

                _state.update {
                    it.copy(
                        isProcessing = false,
                        isSuccess = true,
                        resultUri = outputUri,
                        resultFileName = outName,
                        resultFileSize = outSize
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = e.localizedMessage ?: "Failed to apply watermark. The PDF might be corrupted or protected."
                        )
                    }
                }
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        _state.update { it.copy(isProcessing = false) }
    }

    fun reset() {
        processingJob?.cancel()
        _state.update {
            it.copy(
                isProcessing = false,
                isSuccess = false,
                resultUri = null,
                errorMessage = null
            )
        }
    }

    private fun parsePageRange(rangeStr: String, maxPages: Int): List<Int>? {
        if (rangeStr.isBlank()) return null
        return try {
            val pages = mutableSetOf<Int>()
            val parts = rangeStr.split(",")
            for (p in parts) {
                val trimmed = p.trim()
                if (trimmed.contains("-")) {
                    val sub = trimmed.split("-")
                    val start = sub[0].trim().toInt()
                    val end = sub[1].trim().toInt()
                    for (i in start..end) {
                        if (i in 1..maxPages) pages.add(i)
                    }
                } else {
                    val page = trimmed.toInt()
                    if (page in 1..maxPages) pages.add(page)
                }
            }
            if (pages.isNotEmpty()) pages.sorted() else null
        } catch (_: Exception) {
            null
        }
    }
}
