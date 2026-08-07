package com.morphdrop.app.ui.screens.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HistoryDetailUiState(
    val historyItem: ConversionHistoryEntity? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
)

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val historyId: Long = savedStateHandle.get<Long>("historyId") ?: 0L

    private val _uiState = MutableStateFlow(HistoryDetailUiState())
    val uiState: StateFlow<HistoryDetailUiState> = _uiState.asStateFlow()

    init {
        if (historyId > 0L) {
            viewModelScope.launch {
                historyRepository.getHistoryById(historyId).collect { item ->
                    _uiState.value = HistoryDetailUiState(
                        historyItem = item,
                        isLoading = false,
                        isDeleted = item == null && _uiState.value.historyItem != null
                    )
                }
            }
        } else {
            _uiState.value = HistoryDetailUiState(isLoading = false)
        }
    }

    fun deleteItem() {
        val item = _uiState.value.historyItem ?: return
        viewModelScope.launch {
            historyRepository.deleteHistory(item)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    fun openFile(context: Context) {
        val item = _uiState.value.historyItem ?: return
        val urisStr = item.outputUris.ifBlank { item.outputFileNames }
        if (urisStr.isBlank() || urisStr == "-") return

        try {
            val uriList = urisStr.split(",").map { it.trim() }
            if (uriList.size > 1) {
                // Try to open the specific parent folder
                val firstUri = Uri.parse(uriList.first())
                try {
                    val fullPath = firstUri.path ?: ""
                    if (fullPath.isNotBlank()) {
                        val file = File(fullPath)
                        val parent = file.parentFile
                        if (parent != null && parent.exists() && parent.isDirectory) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.fromFile(parent), "resource/folder")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open Folder"))
                            return
                        }
                    }
                } catch (_: Exception) {}

                // Check if first file exists before opening
                val uri = Uri.parse(uriList.first())
                val exists = if (uri.scheme == "content") {
                    try {
                        context.contentResolver.openInputStream(uri)?.close()
                        true
                    } catch (_: Exception) { false }
                } else {
                    File(uri.path ?: "").exists()
                }

                if (!exists) {
                    Toast.makeText(context, "File or folder does not exist anymore", Toast.LENGTH_SHORT).show()
                    return
                }

                // Fallback: Just open the first file
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val uri = Uri.parse(uriList.first())
                    if (uri.scheme == "content") {
                        val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                        setDataAndType(uri, mimeType)
                    } else {
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "com.morphdrop.app.fileprovider",
                            File(uri.path ?: "")
                        )
                        val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"
                        setDataAndType(contentUri, mimeType)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open file"))
            } else {
                val uri = Uri.parse(uriList.first())
                val exists = if (uri.scheme == "content") {
                    try {
                        context.contentResolver.openInputStream(uri)?.close()
                        true
                    } catch (_: Exception) { false }
                } else {
                    File(uri.path ?: "").exists()
                }

                if (!exists) {
                    Toast.makeText(context, "File does not exist anymore", Toast.LENGTH_SHORT).show()
                    return
                }

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    if (uri.scheme == "content") {
                        val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                        setDataAndType(uri, mimeType)
                    } else {
                        val file = File(uri.path ?: "")
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "com.morphdrop.app.fileprovider",
                            file
                        )
                        val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"
                        setDataAndType(contentUri, mimeType)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open file"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context) {
        val item = _uiState.value.historyItem ?: return
        val urisStr = item.outputUris.ifBlank { item.outputFileNames }
        if (urisStr.isBlank() || urisStr == "-") return

        try {
            val uriList = urisStr.split(",").map { it.trim() }.map { Uri.parse(it) }
            
            if (uriList.size > 1) {
                val shareUris = java.util.ArrayList<Uri>()
                var missingCount = 0
                uriList.forEach { uri ->
                    if (uri.scheme == "content") {
                        shareUris.add(uri)
                    } else {
                        val file = File(uri.path ?: "")
                        if (file.exists()) {
                            shareUris.add(FileProvider.getUriForFile(context, "com.morphdrop.app.fileprovider", file))
                        } else {
                            missingCount++
                        }
                    }
                }
                
                if (missingCount > 0 && shareUris.isEmpty()) {
                    Toast.makeText(context, "Files do not exist anymore", Toast.LENGTH_SHORT).show()
                    return
                }

                if (shareUris.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share files"))
                }
            } else {
                val uri = uriList.first()
                val exists = if (uri.scheme == "content") {
                    try {
                        context.contentResolver.openInputStream(uri)?.close()
                        true
                    } catch (_: Exception) { false }
                } else {
                    File(uri.path ?: "").exists()
                }

                if (!exists) {
                    Toast.makeText(context, "File does not exist anymore", Toast.LENGTH_SHORT).show()
                    return
                }

                val shareUri = if (uri.scheme == "content") {
                    uri
                } else {
                    FileProvider.getUriForFile(context, "com.morphdrop.app.fileprovider", File(uri.path ?: ""))
                }
                
                val mimeType = context.contentResolver.getType(shareUri) ?: "*/*"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share file"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
