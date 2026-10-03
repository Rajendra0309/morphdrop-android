package com.morphdrop.app.ui.screens.result

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class OutputFileItem(
    val id: String,
    val fileName: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long = 0,
    val extension: String,
    val uri: Uri? = null
)

data class ResultUiState(
    val title: String = "Conversion Complete!",
    val subtitle: String = "Processing...",
    val outputFiles: List<OutputFileItem> = emptyList(),
    // Defaults to false: screens that build a finished result inline (PDF tools)
    // must not get stuck on the "Wrapping up…" loader. The worker-driven
    // ResultViewModel opts into true explicitly while it loads.
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Structured folder name for multi-file output (no subtitle parsing). */
    val folderName: String? = null,
    /** Total input size, when the worker reported it. */
    val inputSizeFormatted: String? = null,
    /** Total output size across all output files. */
    val outputSizeFormatted: String? = null,
    /**
     * Savings summary, e.g. "75% smaller". Null when not meaningful
     * (unknown input size, or output larger/equal — shown as neutral text).
     */
    val savingsText: String? = null
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val workIdString: String? = savedStateHandle["workId"]
    private val _uiState = MutableStateFlow(ResultUiState(isLoading = true))
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        val idStr = workIdString
        if (idStr.isNullOrBlank()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Missing conversion reference.")
            }
        } else {
            loadWorkInfo(idStr)
        }
    }

    private fun loadWorkInfo(idStr: String) {
        val workId = try {
            UUID.fromString(idStr)
        } catch (e: IllegalArgumentException) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Invalid conversion reference.")
            }
            return
        }

        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo == null) return@collect
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val outputUriStr = workInfo.outputData.getString(ConversionWorker.KEY_OUTPUT_URI)
                        val outputUrisStr = workInfo.outputData.getString(ConversionWorker.KEY_OUTPUT_URIS)
                            ?: outputUriStr

                        val uriList = outputUrisStr
                            ?.split(",")
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                            ?: emptyList()

                        if (uriList.isNotEmpty()) {
                            val outputItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                uriList.map { uri ->
                                    val fileName = FileHelper.getFileName(context, uri)
                                    val fileSize = FileHelper.getFileSize(context, uri)
                                    OutputFileItem(
                                        id = uri.toString(),
                                        fileName = fileName,
                                        fileSizeFormatted = FileHelper.formatFileSize(fileSize),
                                        fileSizeBytes = fileSize,
                                        extension = fileName.substringAfterLast('.', ""),
                                        uri = uri
                                    )
                                }
                            }

                            val isMultiple = uriList.size > 1
                            val folderName = if (isMultiple) {
                                uriList.firstOrNull()?.path
                                    ?.split("/")
                                    ?.filter { it.isNotBlank() }
                                    ?.let { if (it.size > 1) it[it.size - 2] else "MorphDrop" }
                                    ?: "MorphDrop"
                            } else null

                            // Before/after size comparison with savings percentage.
                            val inputBytes = workInfo.outputData
                                .getLong(ConversionWorker.KEY_INPUT_SIZE_BYTES, 0L)
                            val outputBytes = outputItems.sumOf { it.fileSizeBytes }
                            val inputSizeFormatted = inputBytes
                                .takeIf { it > 0 }
                                ?.let { FileHelper.formatFileSize(it) }
                            val outputSizeFormatted = FileHelper.formatFileSize(outputBytes)
                            val savingsText = if (inputBytes > 0 && outputBytes > 0 && inputBytes != outputBytes) {
                                val pct = ((inputBytes - outputBytes) * 100.0 / inputBytes).toInt()
                                when {
                                    pct > 0 -> "$pct% smaller"
                                    pct < 0 -> "${-pct}% larger"
                                    else -> null
                                }
                            } else null

                            val subtitleText = if (isMultiple) {
                                "Saved to $folderName folder"
                            } else {
                                "1 file created • ${outputItems.first().fileSizeFormatted}"
                            }

                            _uiState.value = ResultUiState(
                                title = "Conversion Complete!",
                                subtitle = subtitleText,
                                outputFiles = outputItems,
                                isLoading = false,
                                folderName = folderName,
                                inputSizeFormatted = inputSizeFormatted,
                                outputSizeFormatted = outputSizeFormatted,
                                savingsText = savingsText
                            )
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Conversion finished but no output was produced."
                                )
                            }
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val message = workInfo.outputData
                            .getString(ConversionWorker.KEY_ERROR)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Conversion failed."
                        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Conversion was cancelled.")
                        }
                    }
                    else -> Unit // still running; keep loading state
                }
            }
        }
    }

    /**
     * file:// URIs must go through FileProvider before leaving the app
     * (FileUriExposedException otherwise). content:// URIs pass through.
     */
    private fun shareableUri(uri: Uri): Uri {
        if (uri.scheme == "content") return uri
        val file = File(uri.path ?: return uri)
        return if (file.exists()) {
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrDefault(uri)
        } else uri
    }

    fun openFile(context: Context, fileItem: OutputFileItem) {
        val uri = fileItem.uri ?: return
        try {
            val intent = FileHelper.openFile(context, shareableUri(uri))
            context.startActivity(Intent.createChooser(intent, "Open File"))
        } catch (_: Exception) {
        }
    }

    /** Best-effort folder open for multi-file output. */
    fun openOutputFolder(context: Context) {
        val folderName = _uiState.value.folderName ?: return
        try {
            val intent = FileHelper.openFolderIntent(context, folderName)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Open Folder"))
            }
        } catch (_: Exception) {
        }
    }

    fun shareFile(context: Context, fileItem: OutputFileItem) {
        val uri = fileItem.uri ?: return
        try {
            val intent = FileHelper.shareFile(context, shareableUri(uri))
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun shareAllFiles(context: Context) {
        try {
            val uris = ArrayList<Uri>()
            _uiState.value.outputFiles.forEach { item ->
                item.uri?.let { uris.add(shareableUri(it)) }
            }
            if (uris.isEmpty()) return
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Files"))
        } catch (_: Exception) {
        }
    }
}
