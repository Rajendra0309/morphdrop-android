package com.morphdrop.app.ui.screens.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PageData(
    val number: Int,
    val originalIndex: Int,
    val rotation: Int = 0
)

data class PdfPageEditorState(
    val selectedFile: Uri? = null,
    val fileName: String = "",
    val pages: List<PageData> = emptyList(),
    val outputFileName: String = "",
    val isLoading: Boolean = false
)

@HiltViewModel
class PdfPageEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(PdfPageEditorState())
    val state: StateFlow<PdfPageEditorState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
        val name = FileHelper.getFileName(context, uri)
        
        _state.update { it.copy(
            selectedFile = uri,
            fileName = name,
            outputFileName = if (name.isNotBlank()) name.substringBeforeLast(".") + "_edited.pdf" else "",
            isLoading = true
        ) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    val pageCount = renderer.pageCount
                    val pageList = (0 until pageCount).map { i ->
                        PageData(number = i + 1, originalIndex = i)
                    }
                    _state.update { it.copy(pages = pageList, isLoading = false) }
                    renderer.close()
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun rotatePage(originalIndex: Int) {
        _state.update { currentState ->
            val updatedPages = currentState.pages.map { page ->
                if (page.originalIndex == originalIndex) {
                    page.copy(rotation = (page.rotation + 90) % 360)
                } else page
            }
            currentState.copy(pages = updatedPages)
        }
    }

    fun onOutputFileNameChanged(name: String) {
        _state.update { it.copy(outputFileName = name) }
    }

    fun startEditing(context: Context): UUID? {
        val currentState = _state.value
        val uri = currentState.selectedFile ?: return null
        val pages = currentState.pages

        val pageOrder = pages.map { it.originalIndex }.joinToString(",")
        val rotations = pages.filter { it.rotation != 0 }
            .joinToString(",") { "${it.originalIndex}:${it.rotation}" }

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(
                ConversionWorker.KEY_CONVERSION_TYPE to "page_editor",
                ConversionWorker.KEY_INPUT_URI to uri.toString(),
                ConversionWorker.KEY_PAGE_ORDER to pageOrder,
                "page_rotations" to rotations,
                ConversionWorker.KEY_OUTPUT_FILE_NAME to currentState.outputFileName
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
