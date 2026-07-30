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

data class SplitPdfState(
    val selectedFile: Uri? = null,
    val fileName: String = "",
    val pageRanges: String = "",
    val outputFolderName: String = ""
)

@HiltViewModel
class SplitPdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(SplitPdfState())
    val state: StateFlow<SplitPdfState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri?) {
        val name = uri?.let { FileHelper.getFileName(context, it) } ?: ""
        _state.update { it.copy(
            selectedFile = uri,
            fileName = name,
            outputFolderName = if (name.isNotBlank()) name.substringBeforeLast(".") + "_split" else ""
        ) }
    }

    fun onPageRangesChanged(ranges: String) {
        _state.update { it.copy(pageRanges = ranges) }
    }

    fun onOutputFolderNameChanged(name: String) {
        _state.update { it.copy(outputFolderName = name) }
    }

    fun startSplit(context: Context): UUID? {
        val currentState = _state.value
        val uri = currentState.selectedFile ?: return null

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(
                ConversionWorker.KEY_CONVERSION_TYPE to "split_pdf",
                ConversionWorker.KEY_INPUT_URI to uri.toString(),
                ConversionWorker.KEY_PAGE_RANGE to currentState.pageRanges,
                ConversionWorker.KEY_OUTPUT_FILE_NAME to currentState.outputFolderName
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
