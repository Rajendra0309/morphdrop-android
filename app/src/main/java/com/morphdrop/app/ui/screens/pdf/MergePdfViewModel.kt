package com.morphdrop.app.ui.screens.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class MergePdfState(
    val selectedFiles: List<Uri> = emptyList(),
    val outputFileName: String = ""
)

@HiltViewModel
class MergePdfViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(MergePdfState())
    val state: StateFlow<MergePdfState> = _state.asStateFlow()

    fun onFilesSelected(uris: List<Uri>) {
        _state.update { 
            val newList = it.selectedFiles + uris
            it.copy(
                selectedFiles = newList,
                outputFileName = if (it.outputFileName.isEmpty() && newList.isNotEmpty()) "merged_document.pdf" else it.outputFileName
            )
        }
    }

    fun onRemoveFile(uri: Uri) {
        _state.update { it.copy(selectedFiles = it.selectedFiles - uri) }
    }

    fun startMerge(context: Context): UUID? {
        val currentState = _state.value
        if (currentState.selectedFiles.size < 2) return null

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(
                ConversionWorker.KEY_CONVERSION_TYPE to "merge_pdf",
                ConversionWorker.KEY_INPUT_URI to currentState.selectedFiles.first().toString(),
                ConversionWorker.KEY_INPUT_URIS to currentState.selectedFiles.map { it.toString() }.toTypedArray(),
                ConversionWorker.KEY_OUTPUT_FILE_NAME to (if (currentState.outputFileName.isBlank()) "merged_${System.currentTimeMillis()}.pdf" else currentState.outputFileName)
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
