package com.morphdrop.app.ui.screens.pdf.rotate

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.RotateScope
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.usecase.conversion.RotatePdfPagesUseCase
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

data class PdfRotateUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val pageCount: Int = 1,
    val previewBitmap: Bitmap? = null,
    val isPreviewLoading: Boolean = false,
    val degrees: Int = 90,
    val scope: RotateScope = RotateScope.ALL_PAGES,
    val customRangeText: String = "",
    val isProcessing: Boolean = false,
    val isSuccess: Boolean = false,
    val resultUri: Uri? = null,
    val resultFileName: String = "",
    val resultFileSize: Long = 0L,
    val errorMessage: String? = null
)

@HiltViewModel
class PdfRotateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val rotatePdfPagesUseCase: RotatePdfPagesUseCase,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val initialUriString: String? = savedStateHandle["uri"]

    private val _state = MutableStateFlow(PdfRotateUiState())
    val state: StateFlow<PdfRotateUiState> = _state.asStateFlow()

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

            _state.update {
                it.copy(
                    pageCount = count,
                    previewBitmap = thumb,
                    isPreviewLoading = false
                )
            }
        }
    }

    fun setDegrees(deg: Int) {
        _state.update { it.copy(degrees = deg) }
    }

    fun setScope(scope: RotateScope) {
        _state.update { it.copy(scope = scope) }
    }

    fun setCustomRangeText(text: String) {
        _state.update { it.copy(customRangeText = text) }
    }

    fun rotatePdf() {
        val uri = _state.value.selectedUri ?: return
        if (_state.value.isProcessing) return

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }

            try {
                val baseName = _state.value.fileName.substringBeforeLast('.')
                val outName = "${baseName}_rotated.pdf"

                val targetPages = when (_state.value.scope) {
                    RotateScope.ALL_PAGES -> {
                        if (_state.value.customRangeText.isNotBlank()) {
                            parsePageRange(_state.value.customRangeText, _state.value.pageCount)
                        } else null
                    }
                    RotateScope.EVEN_PAGES -> (1.._state.value.pageCount).filter { it % 2 == 0 }
                    RotateScope.ODD_PAGES -> (1.._state.value.pageCount).filter { it % 2 != 0 }
                }

                val outputUri = rotatePdfPagesUseCase(
                    pdfUri = uri,
                    rotationDegrees = _state.value.degrees,
                    targetPages = targetPages,
                    outputFileName = outName
                )

                val outSize = FileHelper.getFileSize(context, outputUri).let { if (it <= 0) _state.value.fileSize else it }

                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "rotate_pdf",
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
                _state.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.localizedMessage ?: "Failed to rotate PDF."
                    )
                }
            }
        }
    }

    fun reset() {
        _state.update {
            it.copy(
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
