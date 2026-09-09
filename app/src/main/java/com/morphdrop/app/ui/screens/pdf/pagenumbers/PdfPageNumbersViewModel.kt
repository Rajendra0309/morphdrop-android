package com.morphdrop.app.ui.screens.pdf.pagenumbers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.domain.model.PageNumberFormat
import com.morphdrop.app.domain.model.PageNumberPosition
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.usecase.conversion.AddPageNumbersUseCase
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

data class PdfPageNumbersUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val pageCount: Int = 1,
    val firstPageBitmap: Bitmap? = null,
    val lastPageBitmap: Bitmap? = null,
    val isPreviewLoading: Boolean = false,
    val activePreviewTab: Int = 0, // 0: First Page, 1: Last Page
    val config: PageNumberConfig = PageNumberConfig(
        position = PageNumberPosition.BOTTOM_CENTER,
        format = PageNumberFormat.PAGE_X_OF_Y,
        customTemplate = "- {page} -",
        fontSizeSp = 12f,
        fontColor = 0xFF000000,
        startNumber = 1,
        skipFirstPage = false,
        skipLastPage = false,
        marginDp = 24f
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
class PdfPageNumbersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val addPageNumbersUseCase: AddPageNumbersUseCase,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val initialUriString: String? = savedStateHandle["uri"]

    private val _state = MutableStateFlow(PdfPageNumbersUiState())
    val state: StateFlow<PdfPageNumbersUiState> = _state.asStateFlow()

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

            val firstBitmap = PdfThumbnailHelper.getThumbnail(context, uri, 0)
            val lastBitmap = if (count > 1) PdfThumbnailHelper.getThumbnail(context, uri, count - 1) else firstBitmap

            _state.update {
                it.copy(
                    pageCount = count,
                    firstPageBitmap = firstBitmap,
                    lastPageBitmap = lastBitmap,
                    isPreviewLoading = false
                )
            }
        }
    }

    fun setActivePreviewTab(tabIndex: Int) {
        _state.update { it.copy(activePreviewTab = tabIndex) }
    }

    fun updateConfig(update: (PageNumberConfig) -> PageNumberConfig) {
        _state.update { it.copy(config = update(it.config)) }
    }

    fun onCustomRangeTextChanged(text: String) {
        _state.update { it.copy(customRangeText = text) }
    }

    private var processingJob: kotlinx.coroutines.Job? = null

    fun applyPageNumbers() {
        val uri = _state.value.selectedUri ?: return
        if (_state.value.isProcessing) return

        processingJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }

            try {
                val baseName = _state.value.fileName.substringBeforeLast('.')
                val outName = "${baseName}_numbered.pdf"

                val targetPages = parsePageRange(_state.value.customRangeText, _state.value.pageCount)
                val finalConfig = _state.value.config.copy(targetPages = targetPages)

                val outputUri = addPageNumbersUseCase(
                    pdfUri = uri,
                    config = finalConfig,
                    outputFileName = outName
                )

                val outSize = FileHelper.getFileSize(context, outputUri).let { if (it <= 0) _state.value.fileSize else it }

                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "page_numbers_pdf",
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
                            errorMessage = e.localizedMessage ?: "Failed to add page numbers."
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
