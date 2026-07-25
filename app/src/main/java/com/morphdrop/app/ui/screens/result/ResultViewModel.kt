package com.morphdrop.app.ui.screens.result

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutputFileItem(
    val id: String,
    val fileName: String,
    val fileSizeFormatted: String,
    val extension: String,
    val uri: Uri? = null
)

data class ResultUiState(
    val title: String = "Conversion Complete!",
    val subtitle: String = "Processing...",
    val outputFiles: List<OutputFileItem> = emptyList()
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val workIdString: String? = savedStateHandle["workId"]
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        workIdString?.let { idStr ->
            loadWorkInfo(idStr)
        }
    }

    private fun loadWorkInfo(idStr: String) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workId = java.util.UUID.fromString(idStr)
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo != null && workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    val outputUriStr = workInfo.outputData.getString(com.morphdrop.app.worker.ConversionWorker.KEY_OUTPUT_URI)
                    
                    if (outputUriStr != null) {
                        val uri = Uri.parse(outputUriStr)
                        val fileName = FileHelper.getFileName(context, uri)
                        val fileSize = FileHelper.getFileSize(context, uri)
                        val extension = fileName.substringAfterLast('.', "")
                        
                        val item = OutputFileItem(
                            id = uri.toString(),
                            fileName = fileName,
                            fileSizeFormatted = FileHelper.formatFileSize(fileSize),
                            extension = extension,
                            uri = uri
                        )
                        
                        _uiState.value = ResultUiState(
                            title = "Conversion Complete!",
                            subtitle = "1 file created • ${item.fileSizeFormatted}",
                            outputFiles = listOf(item)
                        )
                    }
                }
            }
        }
    }

    fun openFile(context: Context, fileItem: OutputFileItem) {
        fileItem.uri?.let { uri ->
            val intent = FileHelper.openFile(context, uri)
            context.startActivity(Intent.createChooser(intent, "Open File"))
        }
    }

    fun shareFile(context: Context, fileItem: OutputFileItem) {
        fileItem.uri?.let { uri ->
            val intent = FileHelper.shareFile(context, uri)
            context.startActivity(Intent.createChooser(intent, "Share File"))
        }
    }
}
