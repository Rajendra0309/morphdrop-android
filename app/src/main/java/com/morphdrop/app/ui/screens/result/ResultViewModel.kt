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
                    val outputUrisStr = workInfo.outputData.getString(com.morphdrop.app.worker.ConversionWorker.KEY_OUTPUT_URIS) ?: outputUriStr
                    
                    if (outputUrisStr != null) {
                        val uriList = outputUrisStr.split(",").map { Uri.parse(it) }
                        val outputItems = uriList.map { uri ->
                            val fileName = FileHelper.getFileName(context, uri)
                            val fileSize = FileHelper.getFileSize(context, uri)
                            OutputFileItem(
                                id = uri.toString(),
                                fileName = fileName,
                                fileSizeFormatted = FileHelper.formatFileSize(fileSize),
                                extension = fileName.substringAfterLast('.', ""),
                                uri = uri
                            )
                        }
                        
                        val firstUri = uriList.first()
                        val isMultiple = uriList.size > 1
                        
                        val subtitleText = if (isMultiple) {
                            val folderName = firstUri.path?.split("/")?.filter { it.isNotBlank() }?.let { 
                                if (it.size > 1) it[it.size - 2] else "MorphDrop"
                            } ?: "MorphDrop"
                            "Saved to $folderName folder"
                        } else {
                            "1 file created • ${outputItems.first().fileSizeFormatted}"
                        }
                        
                        _uiState.value = ResultUiState(
                            title = "Conversion Complete!",
                            subtitle = subtitleText,
                            outputFiles = outputItems
                        )
                    }
                }
            }
        }
    }

    fun openFile(context: Context, fileItem: OutputFileItem) {
        val files = _uiState.value.outputFiles
        if (files.size > 1) {
            // Try to open the parent folder specifically
            val firstUri = files.first().uri ?: return
            try {
                val fullPath = firstUri.path ?: ""
                if (fullPath.isNotBlank()) {
                    val file = java.io.File(fullPath)
                    val parent = file.parentFile
                    
                    // On Android 10+, we might be dealing with MediaStore URIs which don't have direct file paths
                    // but our use cases currently save to cache or external storage files if possible
                    if (parent != null && parent.exists() && parent.isDirectory) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.fromFile(parent), "resource/folder")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(Intent.createChooser(intent, "Open Folder"))
                            return
                        }
                    }
                }
            } catch (_: Exception) {}
            
            // Fallback: Use MediaStore-aware opening if possible, or just open first file
            fileItem.uri?.let { uri ->
                val intent = FileHelper.openFile(context, uri)
                context.startActivity(Intent.createChooser(intent, "Open File"))
            }
        } else {
            fileItem.uri?.let { uri ->
                val intent = FileHelper.openFile(context, uri)
                context.startActivity(Intent.createChooser(intent, "Open File"))
            }
        }
    }

    fun shareFile(context: Context, fileItem: OutputFileItem) {
        val files = _uiState.value.outputFiles
        if (files.size > 1) {
            val uris = ArrayList<Uri>()
            files.forEach { item ->
                item.uri?.let { uri ->
                    if (uri.scheme == "content") {
                        uris.add(uri)
                    } else {
                        val file = java.io.File(uri.path ?: "")
                        if (file.exists()) {
                            uris.add(androidx.core.content.FileProvider.getUriForFile(context, "com.morphdrop.app.fileprovider", file))
                        }
                    }
                }
            }
            if (uris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Files"))
            }
        } else {
            fileItem.uri?.let { uri ->
                val intent = FileHelper.shareFile(context, uri)
                context.startActivity(Intent.createChooser(intent, "Share File"))
            }
        }
    }
}
