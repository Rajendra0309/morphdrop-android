package com.morphdrop.app.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.OcrScript
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.conversion.OcrUseCase
import com.morphdrop.app.domain.usecase.history.SaveHistoryUseCase
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class BatchOcrViewMode {
    COMBINED, INDIVIDUAL
}

data class BatchOcrItemResult(
    val uri: Uri,
    val fileName: String,
    val text: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class BatchOcrUiState(
    val selectedUris: List<Uri> = emptyList(),
    val selectedScript: OcrScript = OcrScript.LATIN,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressText: String = "",
    val results: List<BatchOcrItemResult> = emptyList(),
    val failedUris: Set<Uri> = emptySet(),
    val viewMode: BatchOcrViewMode = BatchOcrViewMode.COMBINED,
    val combinedText: String = "",
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val savedSummary: String? = null
)

@HiltViewModel
class BatchOcrViewModel @Inject constructor(
    private val ocrUseCase: OcrUseCase,
    private val settingsRepository: SettingsRepository,
    private val saveHistoryUseCase: SaveHistoryUseCase,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _state = MutableStateFlow(BatchOcrUiState())
    val state: StateFlow<BatchOcrUiState> = _state.asStateFlow()

    private var extractionJob: Job? = null

    fun onFilesSelected(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val newUris = uris.distinct()
        for (uri in newUris) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
                // Ignore if security exception or persistent permissions cannot be taken
            }
        }

        _state.update { current ->
            val updatedList = (current.selectedUris + newUris).distinct()
            current.copy(
                selectedUris = updatedList,
                errorMessage = null,
                results = emptyList(),
                failedUris = emptySet(),
                combinedText = ""
            )
        }
    }

    fun onRemoveFile(uri: Uri) {
        _state.update { current ->
            val updated = current.selectedUris.filter { it != uri }
            current.copy(selectedUris = updated)
        }
    }

    fun onClearAll() {
        _state.update {
            BatchOcrUiState(selectedScript = it.selectedScript)
        }
    }

    fun onScriptSelected(script: OcrScript) {
        _state.update { it.copy(selectedScript = script) }
    }

    fun onToggleViewMode(mode: BatchOcrViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun startBatchExtraction(context: Context) {
        val currentUris = state.value.selectedUris
        if (currentUris.isEmpty()) {
            _state.update { it.copy(errorMessage = "Please select at least one image.") }
            return
        }

        cancelBatchExtraction()

        extractionJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = currentUris.size,
                    progressText = "Preparing extraction...",
                    errorMessage = null,
                    infoMessage = null,
                    results = emptyList(),
                    failedUris = emptySet()
                )
            }

            val NOTIF_ID = 9001
            notificationHelper.showProgressNotification(
                notificationId = NOTIF_ID,
                title = "Batch OCR Text Extraction",
                progress = 0
            )

            val itemResults = mutableListOf<BatchOcrItemResult>()
            val failedSet = mutableSetOf<Uri>()
            val script = state.value.selectedScript

            val extractionResult = ocrUseCase.extractFromBatch(
                uris = currentUris,
                script = script,
                onProgress = { current, total, uri ->
                    val fileName = FileHelper.getFileName(context, uri)
                    val statusText = "Extracting text ($current of $total): $fileName"
                    _state.update {
                        it.copy(
                            progressCurrent = current,
                            progressTotal = total,
                            progressText = statusText
                        )
                    }
                    val percent = (current * 100) / total
                    notificationHelper.showProgressNotification(
                        notificationId = NOTIF_ID,
                        title = "Batch OCR Text Extraction ($current/$total)",
                        progress = percent
                    )
                }
            )

            notificationHelper.cancelNotification(NOTIF_ID)

            if (extractionResult.isSuccess) {
                val batchList = extractionResult.getOrDefault(emptyList())
                val extractedMap = batchList.toMap()

                val combinedBuilder = StringBuilder()
                currentUris.forEachIndexed { index, uri ->
                    val fileName = FileHelper.getFileName(context, uri)
                    val text = extractedMap[uri]
                    if (text != null && text.isNotBlank()) {
                        itemResults.add(
                            BatchOcrItemResult(
                                uri = uri,
                                fileName = fileName,
                                text = text,
                                isSuccess = true
                            )
                        )
                        if (combinedBuilder.isNotEmpty()) combinedBuilder.append("\n\n")
                        combinedBuilder.append("--- File ${index + 1}: $fileName ---\n").append(text)
                    } else {
                        failedSet.add(uri)
                        itemResults.add(
                            BatchOcrItemResult(
                                uri = uri,
                                fileName = fileName,
                                text = "",
                                isSuccess = false,
                                errorMessage = "No readable text detected"
                            )
                        )
                    }
                }

                val totalProcessed = currentUris.size
                val successCount = itemResults.count { it.isSuccess }
                val failCount = failedSet.size

                val summaryMsg = "$successCount of $totalProcessed images processed successfully." +
                        if (failCount > 0) " $failCount failed." else ""

                _state.update {
                    it.copy(
                        isProcessing = false,
                        results = itemResults,
                        failedUris = failedSet,
                        combinedText = combinedBuilder.toString().trim(),
                        infoMessage = summaryMsg
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = extractionResult.exceptionOrNull()?.message ?: "Batch OCR failed"
                    )
                }
            }
        }
    }

    fun cancelBatchExtraction() {
        extractionJob?.cancel()
        extractionJob = null
        _state.update {
            it.copy(
                isProcessing = false,
                progressText = "Batch extraction cancelled"
            )
        }
    }

    fun copyAllToClipboard(context: Context) {
        val text = state.value.combinedText
        if (text.isBlank()) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Batch Extracted Text", text)
        clipboard.setPrimaryClip(clip)
        _state.update { it.copy(infoMessage = "Copied combined text to clipboard!") }
    }

    fun saveAsSingleTxt(context: Context) {
        val text = state.value.combinedText
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                val rootFolder = settingsRepository.outputFolderName.first()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "batch_ocr_$timestamp.txt"
                val subFolder = "$rootFolder/OCR"
                val savedUri = FileHelper.saveToDirectory(context, subFolder, fileName, text.toByteArray())

                saveHistoryUseCase(
                    ConversionHistoryEntity(
                        conversionType = "Batch OCR",
                        inputFileName = "${state.value.results.size} Images",
                        outputFileNames = fileName,
                        outputUris = savedUri.toString(),
                        displayName = fileName,
                        timestamp = System.currentTimeMillis()
                    )
                )
                com.morphdrop.app.ui.widget.WidgetUpdateHelper.updateAllWidgets(context)

                _state.update { it.copy(infoMessage = "Saved combined text to Downloads/$subFolder/$fileName") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save file: ${e.message}") }
            }
        }
    }

    fun saveAsSeparateFiles(context: Context) {
        val successResults = state.value.results.filter { it.isSuccess }
        if (successResults.isEmpty()) return

        viewModelScope.launch {
            try {
                val rootFolder = settingsRepository.outputFolderName.first()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val subFolder = "$rootFolder/OCR/batch_$timestamp"

                var savedCount = 0
                for (item in successResults) {
                    val baseName = FileHelper.getFileNameWithoutExtension(item.fileName)
                    val txtFileName = "${baseName}_ocr.txt"
                    FileHelper.saveToDirectory(
                        context = context,
                        directoryName = subFolder,
                        fileName = txtFileName,
                        data = item.text.toByteArray()
                    )
                    savedCount++
                }

                _state.update { it.copy(infoMessage = "Saved $savedCount files in Downloads/$subFolder/") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save separate files: ${e.message}") }
            }
        }
    }

    fun shareAll(context: Context) {
        val text = state.value.combinedText
        if (text.isBlank()) return

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Extracted Text")
        context.startActivity(shareIntent)
    }

    fun dismissMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }
}
