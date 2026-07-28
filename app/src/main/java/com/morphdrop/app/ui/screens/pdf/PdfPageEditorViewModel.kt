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

data class PageData(
    val number: Int,
    val originalIndex: Int,
    val rotation: Int = 0
)

data class PdfPageEditorState(
    val selectedFile: Uri? = null,
    val pages: List<PageData> = emptyList()
)

@HiltViewModel
class PdfPageEditorViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(PdfPageEditorState())
    val state: StateFlow<PdfPageEditorState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri) {
        // In a real app, we'd load the page count/thumbnails here.
        _state.update { it.copy(selectedFile = uri) }
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

    fun onPagesUpdated(pages: List<PageData>) {
        _state.update { it.copy(pages = pages) }
    }

    fun startEditing(context: Context): UUID? {
        val currentState = _state.value
        val uri = currentState.selectedFile ?: return null
        val pages = currentState.pages

        val pageOrder = pages.map { it.number - 1 }.joinToString(",")
        val rotations = pages.filter { it.rotation != 0 }
            .joinToString(",") { "${it.number - 1}:${it.rotation}" }

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(
                ConversionWorker.KEY_CONVERSION_TYPE to "page_editor",
                ConversionWorker.KEY_INPUT_URI to uri.toString(),
                ConversionWorker.KEY_PAGE_ORDER to pageOrder,
                "page_rotations" to rotations
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}
