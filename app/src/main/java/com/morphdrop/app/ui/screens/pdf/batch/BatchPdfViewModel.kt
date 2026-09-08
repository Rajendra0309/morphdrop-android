package com.morphdrop.app.ui.screens.pdf.batch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.morphdrop.app.domain.model.BatchCompressConfig
import com.morphdrop.app.domain.model.BatchMergeConfig
import com.morphdrop.app.domain.model.BatchPasswordConfig
import com.morphdrop.app.domain.model.BatchPdfItemResult
import com.morphdrop.app.domain.model.BatchPdfOperation
import com.morphdrop.app.domain.model.BatchRotateConfig
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.domain.model.PageNumberFormat
import com.morphdrop.app.domain.model.PageNumberPosition
import com.morphdrop.app.domain.model.RotateScope
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.WatermarkPosition
import com.morphdrop.app.domain.model.WatermarkType
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.worker.BatchPdfWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class BatchPdfStep {
    SELECT_FILES,
    SELECT_OPERATION,
    CONFIGURE,
    PROCESSING,
    RESULTS
}

data class SelectedPdfFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long
)

data class BatchPdfUiState(
    val currentStep: BatchPdfStep = BatchPdfStep.SELECT_FILES,
    val selectedFiles: List<SelectedPdfFile> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val selectedOperation: BatchPdfOperation = BatchPdfOperation.COMPRESS,

    // Configurations
    val compressConfig: BatchCompressConfig = BatchCompressConfig(),
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    val pageNumberConfig: PageNumberConfig = PageNumberConfig(),
    val passwordConfig: BatchPasswordConfig = BatchPasswordConfig(),
    val rotateConfig: BatchRotateConfig = BatchRotateConfig(),
    val mergeConfig: BatchMergeConfig = BatchMergeConfig(),

    // Processing status
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressFile: String = "",
    val progressPercent: Int = 0,

    // Results
    val results: List<BatchPdfItemResult> = emptyList(),
    val outputFolder: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@HiltViewModel
class BatchPdfViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(BatchPdfUiState())
    val state: StateFlow<BatchPdfUiState> = _state.asStateFlow()

    private val workManager = WorkManager.getInstance(context)
    private var currentWorkId: UUID? = null
    private var workObserverJob: Job? = null

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return

        for (uri in uris) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
        }

        val currentUris = _state.value.selectedFiles.map { it.uri }.toSet()
        val newEntries = uris.filter { it !in currentUris }.map { uri ->
            val name = FileHelper.getFileName(context, uri)
            val size = FileHelper.getFileSize(context, uri).coerceAtLeast(0L)
            SelectedPdfFile(uri = uri, name = name, sizeBytes = size)
        }

        _state.update { current ->
            val updated = current.selectedFiles + newEntries
            val totalSize = updated.sumOf { it.sizeBytes }
            current.copy(
                selectedFiles = updated,
                totalSizeBytes = totalSize,
                errorMessage = null
            )
        }
    }

    fun onRemoveFile(uri: Uri) {
        _state.update { current ->
            val updated = current.selectedFiles.filter { it.uri != uri }
            val totalSize = updated.sumOf { it.sizeBytes }
            current.copy(
                selectedFiles = updated,
                totalSizeBytes = totalSize
            )
        }
    }

    fun onClearAllFiles() {
        _state.update {
            it.copy(
                selectedFiles = emptyList(),
                totalSizeBytes = 0L,
                currentStep = BatchPdfStep.SELECT_FILES
            )
        }
    }

    fun onMoveFileUp(index: Int) {
        if (index <= 0) return
        _state.update { current ->
            val list = current.selectedFiles.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            current.copy(selectedFiles = list)
        }
    }

    fun onMoveFileDown(index: Int) {
        val list = _state.value.selectedFiles
        if (index >= list.size - 1) return
        _state.update { current ->
            val mList = current.selectedFiles.toMutableList()
            val item = mList.removeAt(index)
            mList.add(index + 1, item)
            current.copy(selectedFiles = mList)
        }
    }

    fun onSelectOperation(operation: BatchPdfOperation) {
        _state.update {
            it.copy(
                selectedOperation = operation,
                currentStep = BatchPdfStep.CONFIGURE
            )
        }
    }

    fun onProceedToOperationSelection() {
        if (_state.value.selectedFiles.isEmpty()) {
            _state.update { it.copy(errorMessage = "Please select at least one PDF file.") }
            return
        }
        _state.update { it.copy(currentStep = BatchPdfStep.SELECT_OPERATION) }
    }

    fun onNavigateToStep(step: BatchPdfStep) {
        val current = _state.value
        if (current.currentStep == BatchPdfStep.PROCESSING) return
        when (step) {
            BatchPdfStep.SELECT_FILES -> {
                _state.update { it.copy(currentStep = BatchPdfStep.SELECT_FILES) }
            }
            BatchPdfStep.SELECT_OPERATION -> {
                if (current.selectedFiles.isNotEmpty()) {
                    _state.update { it.copy(currentStep = BatchPdfStep.SELECT_OPERATION) }
                }
            }
            BatchPdfStep.CONFIGURE -> {
                if (current.selectedFiles.isNotEmpty()) {
                    _state.update { it.copy(currentStep = BatchPdfStep.CONFIGURE) }
                }
            }
            else -> {}
        }
    }

    fun onBackStep() {
        _state.update { current ->
            when (current.currentStep) {
                BatchPdfStep.SELECT_FILES -> current
                BatchPdfStep.SELECT_OPERATION -> current.copy(currentStep = BatchPdfStep.SELECT_FILES)
                BatchPdfStep.CONFIGURE -> current.copy(currentStep = BatchPdfStep.SELECT_OPERATION)
                BatchPdfStep.PROCESSING -> current
                BatchPdfStep.RESULTS -> current.copy(currentStep = BatchPdfStep.SELECT_FILES)
            }
        }
    }

    // Config update functions
    fun updateCompressConfig(update: (BatchCompressConfig) -> BatchCompressConfig) {
        _state.update { it.copy(compressConfig = update(it.compressConfig)) }
    }

    fun updateWatermarkConfig(update: (WatermarkConfig) -> WatermarkConfig) {
        _state.update { it.copy(watermarkConfig = update(it.watermarkConfig)) }
    }

    fun updatePageNumberConfig(update: (PageNumberConfig) -> PageNumberConfig) {
        _state.update { it.copy(pageNumberConfig = update(it.pageNumberConfig)) }
    }

    fun updatePasswordConfig(update: (BatchPasswordConfig) -> BatchPasswordConfig) {
        _state.update { it.copy(passwordConfig = update(it.passwordConfig)) }
    }

    fun updateRotateConfig(update: (BatchRotateConfig) -> BatchRotateConfig) {
        _state.update { it.copy(rotateConfig = update(it.rotateConfig)) }
    }

    fun updateMergeConfig(update: (BatchMergeConfig) -> BatchMergeConfig) {
        _state.update { it.copy(mergeConfig = update(it.mergeConfig)) }
    }

    fun startBatchProcessing() {
        val files = _state.value.selectedFiles
        if (files.isEmpty()) {
            _state.update { it.copy(errorMessage = "No PDF files selected.") }
            return
        }

        val op = _state.value.selectedOperation

        // Validation for password operation
        if (op == BatchPdfOperation.PASSWORD && _state.value.passwordConfig.password.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter a password to protect the PDFs.") }
            return
        }

        val inputUriStrings: Array<String?> = Array(files.size) { i -> files[i].uri.toString() }
        val dataBuilder = androidx.work.Data.Builder()
            .putString(BatchPdfWorker.KEY_OPERATION, op.name)
            .putStringArray(BatchPdfWorker.KEY_INPUT_URIS, inputUriStrings)

        // Pack operation configs
        when (op) {
            BatchPdfOperation.COMPRESS -> {
                val c = _state.value.compressConfig
                dataBuilder.putBoolean(BatchPdfWorker.KEY_IS_TARGET_SIZE, c.isTargetSizeMode)
                dataBuilder.putFloat(BatchPdfWorker.KEY_COMPRESS_QUALITY, c.quality)
                dataBuilder.putFloat(BatchPdfWorker.KEY_TARGET_SIZE_MB, c.targetSizeMb)
            }
            BatchPdfOperation.WATERMARK -> {
                val w = _state.value.watermarkConfig
                dataBuilder.putString(BatchPdfWorker.KEY_WATERMARK_TYPE, w.type.name)
                dataBuilder.putString(BatchPdfWorker.KEY_WATERMARK_TEXT, w.text)
                dataBuilder.putFloat(BatchPdfWorker.KEY_WATERMARK_FONT_SIZE, w.fontSizeSp)
                dataBuilder.putLong(BatchPdfWorker.KEY_WATERMARK_COLOR, w.fontColor)
                if (w.imageUri != null) {
                    dataBuilder.putString(BatchPdfWorker.KEY_WATERMARK_IMAGE_URI, w.imageUri)
                }
                dataBuilder.putFloat(BatchPdfWorker.KEY_WATERMARK_SCALE, w.imageScale)
                dataBuilder.putFloat(BatchPdfWorker.KEY_WATERMARK_OPACITY, w.opacity)
                dataBuilder.putFloat(BatchPdfWorker.KEY_WATERMARK_ROTATION, w.rotationDegrees)
                dataBuilder.putString(BatchPdfWorker.KEY_WATERMARK_POSITION, w.position.name)
                dataBuilder.putBoolean(BatchPdfWorker.KEY_WATERMARK_SKIP_FIRST, w.skipFirstPage)
            }
            BatchPdfOperation.PAGE_NUMBERS -> {
                val p = _state.value.pageNumberConfig
                dataBuilder.putString(BatchPdfWorker.KEY_PAGE_NUM_POSITION, p.position.name)
                dataBuilder.putString(BatchPdfWorker.KEY_PAGE_NUM_FORMAT, p.format.name)
                dataBuilder.putString(BatchPdfWorker.KEY_PAGE_NUM_TEMPLATE, p.customTemplate)
                dataBuilder.putFloat(BatchPdfWorker.KEY_PAGE_NUM_FONT_SIZE, p.fontSizeSp)
                dataBuilder.putLong(BatchPdfWorker.KEY_PAGE_NUM_COLOR, p.fontColor)
                dataBuilder.putInt(BatchPdfWorker.KEY_PAGE_NUM_START, p.startNumber)
                dataBuilder.putBoolean(BatchPdfWorker.KEY_PAGE_NUM_SKIP_FIRST, p.skipFirstPage)
                dataBuilder.putBoolean(BatchPdfWorker.KEY_PAGE_NUM_SKIP_LAST, p.skipLastPage)
                dataBuilder.putFloat(BatchPdfWorker.KEY_PAGE_NUM_MARGIN, p.marginDp)
            }
            BatchPdfOperation.PASSWORD -> {
                val p = _state.value.passwordConfig
                dataBuilder.putString(BatchPdfWorker.KEY_PASSWORD, p.password)
                dataBuilder.putBoolean(BatchPdfWorker.KEY_ALLOW_PRINT, p.allowPrinting)
                dataBuilder.putBoolean(BatchPdfWorker.KEY_ALLOW_COPY, p.allowCopying)
                dataBuilder.putBoolean(BatchPdfWorker.KEY_ALLOW_EDIT, p.allowEditing)
            }
            BatchPdfOperation.ROTATE -> {
                val r = _state.value.rotateConfig
                dataBuilder.putInt(BatchPdfWorker.KEY_ROTATE_DEGREES, r.degrees)
                dataBuilder.putString(BatchPdfWorker.KEY_ROTATE_SCOPE, r.scope.name)
            }
            BatchPdfOperation.MERGE -> {
                val m = _state.value.mergeConfig
                dataBuilder.putBoolean(BatchPdfWorker.KEY_MERGE_ADD_NUMBERS, m.addPageNumbers)
            }
        }

        val workRequest = OneTimeWorkRequestBuilder<BatchPdfWorker>()
            .setInputData(dataBuilder.build())
            .build()

        currentWorkId = workRequest.id

        _state.update {
            it.copy(
                currentStep = BatchPdfStep.PROCESSING,
                isProcessing = true,
                progressCurrent = 0,
                progressTotal = files.size,
                progressFile = "Initializing...",
                progressPercent = 0,
                results = emptyList()
            )
        }

        workManager.enqueue(workRequest)

        workObserverJob?.cancel()
        workObserverJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo == null) return@collect

                val progress = workInfo.progress
                val cur = progress.getInt(BatchPdfWorker.KEY_PROGRESS_CURRENT, _state.value.progressCurrent)
                val tot = progress.getInt(BatchPdfWorker.KEY_PROGRESS_TOTAL, _state.value.progressTotal)
                val file = progress.getString(BatchPdfWorker.KEY_PROGRESS_FILE) ?: _state.value.progressFile
                val pct = progress.getInt(BatchPdfWorker.KEY_PROGRESS_PERCENT, _state.value.progressPercent)

                _state.update {
                    it.copy(
                        progressCurrent = cur,
                        progressTotal = tot,
                        progressFile = file,
                        progressPercent = pct
                    )
                }

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val outputData = workInfo.outputData
                        val json = outputData.getString(BatchPdfWorker.KEY_RESULTS_JSON)
                        val folder = outputData.getString(BatchPdfWorker.KEY_OUTPUT_FOLDER)

                        val parsedResults: List<BatchPdfItemResult> = if (!json.isNullOrBlank()) {
                            try {
                                val type = object : TypeToken<List<BatchPdfItemResult>>() {}.type
                                Gson().fromJson(json, type)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        } else emptyList()

                        _state.update {
                            it.copy(
                                isProcessing = false,
                                currentStep = BatchPdfStep.RESULTS,
                                results = parsedResults,
                                outputFolder = folder,
                                infoMessage = "Batch completed successfully!"
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString("error") ?: "Batch processing failed."
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                currentStep = BatchPdfStep.CONFIGURE,
                                errorMessage = error
                            )
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                currentStep = BatchPdfStep.CONFIGURE,
                                infoMessage = "Batch operation was cancelled."
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelBatchProcessing() {
        currentWorkId?.let { workManager.cancelWorkById(it) }
        workObserverJob?.cancel()
        _state.update {
            it.copy(
                isProcessing = false,
                currentStep = BatchPdfStep.CONFIGURE,
                infoMessage = "Operation cancelled"
            )
        }
    }

    fun openFolder(context: Context) {
        val folder = _state.value.outputFolder ?: return
        try {
            val encodedFolder = Uri.encode(folder)
            val docUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2F$encodedFolder")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        try {
            val dmIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (dmIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dmIntent)
                return
            }
        } catch (_: Exception) {}

        // Fallback: If any file was produced, open the first file
        val firstUriStr = _state.value.results.firstOrNull { it.isSuccess && it.outputUriString != null }?.outputUriString
        if (firstUriStr != null) {
            try {
                val viewIntent = Intent(context, com.morphdrop.app.PdfViewerActivity::class.java).apply {
                    data = Uri.parse(firstUriStr)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(viewIntent)
                _state.update { it.copy(infoMessage = "Opened file from Downloads/$folder") }
                return
            } catch (_: Exception) {}
        }

        _state.update { it.copy(infoMessage = "Saved in Downloads/$folder") }
    }

    fun shareAll(context: Context) {
        val successResults = _state.value.results.filter { it.isSuccess && it.outputUriString != null }
        if (successResults.isEmpty()) return

        val uris = ArrayList<Uri>()
        for (item in successResults) {
            item.outputUriString?.let { uris.add(Uri.parse(it)) }
        }

        if (uris.size == 1) {
            val shareIntent = FileHelper.shareFile(context, uris.first())
            context.startActivity(shareIntent)
        } else if (uris.size > 1) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Batch PDFs"))
        }
    }

    fun dismissMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }
}
