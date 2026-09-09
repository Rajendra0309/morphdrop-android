package com.morphdrop.app.ui.screens.pdf.compress

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.PdfSizeAnalysis
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.usecase.conversion.CompressPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.CompressionLevel
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

data class PdfCompressUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val pageCount: Int = 1,
    val previewBitmap: Bitmap? = null,
    val isAnalyzing: Boolean = false,
    val analysis: PdfSizeAnalysis? = null,
    val isTargetSizeMode: Boolean = false,
    val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
    val qualitySlider: Int = 50,
    val targetSizeInput: String = "5",
    val isTargetSizeMb: Boolean = true,
    val isProcessing: Boolean = false,
    val currentIteration: Int = 0,
    val maxIterations: Int = 4,
    val isSuccess: Boolean = false,
    val resultUri: Uri? = null,
    val resultFileName: String = "",
    val resultOriginalSize: Long = 0L,
    val resultNewSize: Long = 0L,
    val errorMessage: String? = null
) {
    val requestedTargetBytes: Long
        get() {
            val num = targetSizeInput.toFloatOrNull() ?: 0f
            return if (isTargetSizeMb) (num * 1024L * 1024L).toLong() else (num * 1024L).toLong()
        }

    val isTargetLargerThanOriginal: Boolean
        get() = fileSize > 0 && requestedTargetBytes >= fileSize

    val isTargetBelowMinimum: Boolean
        get() = analysis != null && analysis.estimatedMinBytes > 0 && requestedTargetBytes < analysis.estimatedMinBytes
}

@HiltViewModel
class PdfCompressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val compressPdfUseCase: CompressPdfUseCase,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val initialUriString: String? = savedStateHandle["uri"]

    private val _state = MutableStateFlow(PdfCompressUiState())
    val state: StateFlow<PdfCompressUiState> = _state.asStateFlow()

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
            val size = FileHelper.getFileSize(context, uri)
            _state.update {
                it.copy(
                    selectedUri = uri,
                    fileName = FileHelper.getFileName(context, uri),
                    fileSize = size,
                    isAnalyzing = true,
                    errorMessage = null,
                    isSuccess = false,
                    resultUri = null
                )
            }

            // Load page count and thumbnail
            val count = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                            renderer.pageCount
                        }
                    } ?: 1
                } catch (_: Exception) { 1 }
            }

            val thumb = PdfThumbnailHelper.getThumbnail(context, uri, 0)

            // Analyze PDF structure & image distribution
            val sizeAnalysis = compressPdfUseCase.analyzePdf(uri)

            _state.update {
                it.copy(
                    pageCount = count,
                    previewBitmap = thumb,
                    isAnalyzing = false,
                    analysis = sizeAnalysis,
                    targetSizeInput = if (size > 0) {
                        val halfMb = (size / 1024L / 1024L / 2).coerceAtLeast(1)
                        halfMb.toString()
                    } else "5"
                )
            }
        }
    }

    fun setTargetSizeMode(enabled: Boolean) {
        _state.update { it.copy(isTargetSizeMode = enabled) }
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _state.update {
            it.copy(
                compressionLevel = level,
                qualitySlider = when (level) {
                    CompressionLevel.HIGH -> 30
                    CompressionLevel.MEDIUM -> 60
                    CompressionLevel.LOW -> 85
                }
            )
        }
    }

    fun setQualitySlider(quality: Int) {
        _state.update {
            it.copy(
                qualitySlider = quality,
                compressionLevel = when {
                    quality <= 40 -> CompressionLevel.HIGH
                    quality <= 70 -> CompressionLevel.MEDIUM
                    else -> CompressionLevel.LOW
                }
            )
        }
    }

    fun setTargetSizeInput(input: String) {
        _state.update { it.copy(targetSizeInput = input) }
    }

    fun setTargetSizeUnit(isMb: Boolean) {
        _state.update { it.copy(isTargetSizeMb = isMb) }
    }

    fun applyPreset(presetMb: Float) {
        _state.update {
            it.copy(
                isTargetSizeMode = true,
                isTargetSizeMb = true,
                targetSizeInput = if (presetMb == presetMb.toLong().toFloat()) presetMb.toLong().toString() else presetMb.toString()
            )
        }
    }

    private var processingJob: kotlinx.coroutines.Job? = null

    fun compressPdf() {
        val uri = _state.value.selectedUri ?: return
        if (_state.value.isProcessing) return

        processingJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    currentIteration = 1,
                    errorMessage = null
                )
            }

            try {
                val baseName = _state.value.fileName.substringBeforeLast('.')
                val outName = "${baseName}_compressed.pdf"

                val targetKb = if (_state.value.isTargetSizeMode) {
                    val targetBytes = _state.value.requestedTargetBytes
                    (targetBytes / 1024L).toInt()
                } else null

                val level = _state.value.compressionLevel

                val result = compressPdfUseCase(
                    pdfUri = uri,
                    compressionLevel = level,
                    targetSizeKb = targetKb,
                    outputFileName = outName,
                    onProgress = { iter, max, _ ->
                        _state.update {
                            it.copy(
                                currentIteration = iter,
                                maxIterations = max
                            )
                        }
                    }
                )

                // Record in history
                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "compress_pdf",
                            inputFileName = _state.value.fileName,
                            outputFileNames = outName,
                            outputUris = result.outputUri.toString(),
                            displayName = outName,
                            success = true
                        )
                    )
                    com.morphdrop.app.ui.widget.WidgetUpdateHelper.updateAllWidgets(context)
                } catch (_: Exception) {}

                _state.update {
                    it.copy(
                        isProcessing = false,
                        isSuccess = true,
                        resultUri = result.outputUri,
                        resultFileName = outName,
                        resultOriginalSize = result.originalSize,
                        resultNewSize = result.newSize
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = e.localizedMessage ?: "Compression failed. This PDF might be protected or corrupted."
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
                resultFileName = "",
                resultOriginalSize = 0L,
                resultNewSize = 0L,
                errorMessage = null
            )
        }
    }
}
