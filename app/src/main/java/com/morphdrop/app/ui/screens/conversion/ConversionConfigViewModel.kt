package com.morphdrop.app.ui.screens.conversion

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ConversionConfigState(
    val conversionType: ConversionType? = null,
    val selectedFileUris: List<Uri> = emptyList(),
    val selectedFileNames: List<String> = emptyList(),
    val selectedFileSize: Long = -1,
    val outputFormat: String = "",
    val quality: Int = 85,
    val pageRangeStart: String = "",
    val pageRangeEnd: String = "",
    val outputFileName: String = "",
    val isConvertEnabled: Boolean = false,
    val showQualitySlider: Boolean = false,
    val showPageRange: Boolean = false,
    val availableOutputFormats: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val selectedFileUri: Uri?
        get() = selectedFileUris.firstOrNull()

    val selectedFileName: String
        get() = when {
            selectedFileNames.size > 1 -> "${selectedFileNames.size} files selected"
            selectedFileNames.size == 1 -> selectedFileNames.first()
            else -> ""
        }
}

@HiltViewModel
class ConversionConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversionTypeId: String = savedStateHandle["conversionTypeId"] ?: ""

    private val _state = MutableStateFlow(ConversionConfigState())
    val state: StateFlow<ConversionConfigState> = _state.asStateFlow()

    init {
        val type = ConversionType.defaultList.find { it.id == conversionTypeId }
        if (type != null) {
            _state.update {
                it.copy(
                    conversionType = type,
                    outputFormat = type.outputType.extension,
                    showQualitySlider = isImageOutput(type),
                    showPageRange = type.inputType == FileType.PDF,
                    availableOutputFormats = getFormatsForType(type)
                )
            }
        }
    }

    fun getAllowedExtensions(type: ConversionType?): List<String> {
        val t = type ?: return emptyList()
        return when (t.id) {
            "excel_to_pdf" -> listOf("xls", "xlsx", "csv")
            "text_to_pdf" -> listOf("txt")
            "md_to_pdf" -> listOf("md", "markdown")
            "pdf_to_images", "split_pdf", "compress_pdf", "protect_pdf", "organize_pdf", "merge_pdf" -> listOf("pdf")
            "images_to_pdf", "compress_images", "image_converter" -> listOf("png", "jpg", "jpeg", "webp", "bmp")
            else -> listOf(t.inputType.extension)
        }
    }

    fun onFilesSelected(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val type = _state.value.conversionType
        val allowedExts = getAllowedExtensions(type)
        val isMultiAllowed = type?.isMultiFileAllowed == true

        val selectedUris = if (!isMultiAllowed && uris.size > 1) {
            listOf(uris.first())
        } else {
            uris
        }

        val names = mutableListOf<String>()
        var totalSize = 0L
        var hasInvalidFile = false
        var invalidFileName = ""

        for (uri in selectedUris) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}

            val name = FileHelper.getFileName(context, uri)
            val size = FileHelper.getFileSize(context, uri)
            val ext = name.substringAfterLast('.', "").lowercase()

            if (allowedExts.isNotEmpty() && ext !in allowedExts) {
                hasInvalidFile = true
                invalidFileName = name
            }

            names.add(name)
            if (size > 0) totalSize += size
        }

        if (hasInvalidFile) {
            val allowedText = allowedExts.joinToString(", ") { ".$it" }
            _state.update {
                it.copy(
                    selectedFileUris = selectedUris,
                    selectedFileNames = names,
                    selectedFileSize = totalSize,
                    isConvertEnabled = false,
                    errorMessage = "Invalid file type: '$invalidFileName'. ${type?.name ?: "This tool"} only accepts $allowedText files."
                )
            }
            return
        }

        val firstFileName = names.firstOrNull() ?: "file"
        val baseName = firstFileName.substringBeforeLast('.')
        val outputExt = _state.value.outputFormat.ifBlank {
            _state.value.conversionType?.outputType?.extension ?: "pdf"
        }

        val outName = if (isFolderOutput(type)) {
            if (selectedUris.size > 1) "converted_batch_${System.currentTimeMillis()}"
            else "${baseName}_extracted"
        } else {
            if (selectedUris.size > 1) {
                "converted_batch_${System.currentTimeMillis()}.$outputExt"
            } else {
                "${baseName}_converted.$outputExt"
            }
        }

        _state.update {
            it.copy(
                selectedFileUris = selectedUris,
                selectedFileNames = names,
                selectedFileSize = totalSize,
                outputFileName = outName,
                isConvertEnabled = true,
                errorMessage = null
            )
        }
    }

    fun onFileSelected(uri: Uri, fileName: String, fileSize: Long) {
        onFilesSelected(
            context = null ?: error("Use onFilesSelected with context"),
            uris = listOf(uri)
        )
    }

    fun onOutputFormatChanged(format: String) {
        val currentType = _state.value.conversionType
        val baseName = _state.value.outputFileName.substringBeforeLast('.')
        
        val newName = if (isFolderOutput(currentType)) {
            baseName // No extension for folder outputs
        } else {
            "$baseName.$format"
        }

        _state.update {
            it.copy(
                outputFormat = format,
                outputFileName = newName
            )
        }
    }

    fun onQualityChanged(quality: Int) {
        _state.update { it.copy(quality = quality) }
    }

    fun onPageRangeStartChanged(value: String) {
        _state.update { it.copy(pageRangeStart = value) }
    }

    fun onPageRangeEndChanged(value: String) {
        _state.update { it.copy(pageRangeEnd = value) }
    }

    fun onOutputFileNameChanged(name: String) {
        _state.update { it.copy(outputFileName = name) }
    }

    fun getPageRange(): IntRange? {
        val start = _state.value.pageRangeStart.toIntOrNull() ?: return null
        val end = _state.value.pageRangeEnd.toIntOrNull() ?: return null
        if (start < 1 || end < start) return null
        return start..end
    }

    fun startConversion(context: Context): java.util.UUID? {
        val currentState = _state.value
        val uris = currentState.selectedFileUris
        if (uris.isEmpty() || !currentState.isConvertEnabled) return null
        val type = currentState.conversionType ?: return null

        return try {
            val uriStrings: Array<String?> = Array(uris.size) { uris[it].toString() }
            val dataBuilder = androidx.work.Data.Builder()
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_CONVERSION_TYPE, type.id)
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_INPUT_URI, uris.first().toString())
                .putStringArray(com.morphdrop.app.worker.ConversionWorker.KEY_INPUT_URIS, uriStrings)
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_OUTPUT_FILE_NAME, currentState.outputFileName)
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_TARGET_FORMAT, currentState.outputFormat)
                .putInt(com.morphdrop.app.worker.ConversionWorker.KEY_QUALITY, currentState.quality)

            if (currentState.pageRangeStart.isNotBlank() && currentState.pageRangeEnd.isNotBlank()) {
                dataBuilder.putString(
                    com.morphdrop.app.worker.ConversionWorker.KEY_PAGE_RANGE,
                    "${currentState.pageRangeStart}-${currentState.pageRangeEnd}"
                )
            }

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.morphdrop.app.worker.ConversionWorker>()
                .setInputData(dataBuilder.build())
                .build()

            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
            workRequest.id
        } catch (e: Exception) {
            null
        }
    }

    fun isFolderOutput(type: ConversionType?): Boolean {
        val t = type ?: return false
        // Split PDF always creates multiple files (by definition).
        // PDF to Images can be 1 or more, but typically user wants a folder.
        // However, if the user explicitly wants "File Name" for 1 page, we need to know page count.
        return t.id in listOf("split_pdf", "compress_images", "organize_pdf") || 
               (t.id == "pdf_to_images" && (_state.value.pageRangeEnd.toIntOrNull() ?: 2) - (_state.value.pageRangeStart.toIntOrNull() ?: 1) > 0)
    }

    private fun isImageOutput(type: ConversionType): Boolean {
        return type.outputType in listOf(FileType.PNG, FileType.JPG, FileType.WEBP, FileType.BMP) ||
                type.id == "compress_images"
    }

    private fun getFormatsForType(type: ConversionType): List<String> {
        return when (type.id) {
            "pdf_to_images" -> listOf("png", "jpg")
            "image_converter" -> listOf("png", "jpg", "webp", "bmp")
            else -> listOf(type.outputType.extension)
        }
    }
}
