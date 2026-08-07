package com.morphdrop.app.ui.screens.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class FileUriItem(
    val uri: Uri,
    val name: String
)

data class MergePdfState(
    val selectedFiles: List<FileUriItem> = emptyList(),
    val outputFileName: String = ""
)

@HiltViewModel
class MergePdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(MergePdfState())
    val state: StateFlow<MergePdfState> = _state.asStateFlow()

    fun onFilesSelected(uris: List<Uri>) {
        val newFiles = uris.map { uri ->
            FileUriItem(uri, FileHelper.getFileName(context, uri))
        }
        _state.update { 
            val newList = it.selectedFiles + newFiles
            it.copy(
                selectedFiles = newList,
                outputFileName = if (it.outputFileName.isEmpty() && newList.isNotEmpty()) "merged_document.pdf" else it.outputFileName
            )
        }
    }

    fun onRemoveFile(uri: Uri) {
        _state.update { currentState ->
            currentState.copy(selectedFiles = currentState.selectedFiles.filter { it.uri != uri })
        }
    }

    fun onOutputFileNameChanged(name: String) {
        _state.update { it.copy(outputFileName = name) }
    }

    fun startMerge(context: Context): UUID? {
        val currentState = _state.value
        if (currentState.selectedFiles.size < 2) return null

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(
                ConversionWorker.KEY_CONVERSION_TYPE to "merge_pdf",
                ConversionWorker.KEY_INPUT_URI to currentState.selectedFiles.first().uri.toString(),
                ConversionWorker.KEY_INPUT_URIS to currentState.selectedFiles.map { it.uri.toString() }.toTypedArray(),
                ConversionWorker.KEY_OUTPUT_FILE_NAME to (if (currentState.outputFileName.isBlank()) "merged_${System.currentTimeMillis()}.pdf" else currentState.outputFileName)
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
