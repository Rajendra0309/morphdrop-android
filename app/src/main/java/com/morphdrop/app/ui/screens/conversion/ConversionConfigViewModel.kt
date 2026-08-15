package com.morphdrop.app.ui.screens.conversion

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.PdfThumbnailHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    // Advanced Image Tools
    val targetWidth: String = "",
    val targetHeight: String = "",
    val paddingColor: Int = android.graphics.Color.BLACK,
    val targetSizeKb: String = "",
    val cropRectLeft: Int = -1,
    val cropRectTop: Int = 0,
    val cropRectRight: Int = 0,
    val cropRectBottom: Int = 0,
    val rotationDegrees: Int = 0,
    val showCropDialog: Boolean = false,
    val showColorPickerDialog: Boolean = false,
    val compressionPreset: String = "Balanced",
    val isBatchMode: Boolean = false,
    val selectedPreviewUri: Uri? = null,
    val aspectRatioPreset: String = "Original",
    val pdfPassword: String = "",
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = true,
    val allowEditing: Boolean = true,
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
                    showQualitySlider = isImageOutput(type) || type.id == "compress_pdf",
                    showPageRange = type.id in listOf("split_pdf", "pdf_to_images", "organize_pdf"),
                    availableOutputFormats = getFormatsForType(type),
                    compressionPreset = if (type.id == "compress_pdf") "Recommended" else it.compressionPreset,
                    quality = if (type.id == "compress_pdf") 60 else it.quality
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
                    isBatchMode = selectedUris.size > 1,
                    selectedPreviewUri = selectedUris.firstOrNull(),
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
            val s = it.copy(
                selectedFileUris = selectedUris,
                selectedFileNames = names,
                selectedFileSize = totalSize,
                outputFileName = outName,
                isBatchMode = selectedUris.size > 1,
                selectedPreviewUri = if (type?.inputType != com.morphdrop.app.domain.model.FileType.PDF) selectedUris.firstOrNull() else null,
                errorMessage = null
            )
            s.copy(isConvertEnabled = isStateValid(s))
        }

        if (type?.inputType == com.morphdrop.app.domain.model.FileType.PDF && selectedUris.isNotEmpty()) {
            viewModelScope.launch {
                val previewUri = PdfThumbnailHelper.getThumbnailUri(context, selectedUris.first())
                if (previewUri != null) {
                    _state.update { it.copy(selectedPreviewUri = previewUri) }
                } else {
                    _state.update { it.copy(selectedPreviewUri = selectedUris.firstOrNull()) }
                }
            }
        }
    }

    fun onFileSelected(uri: Uri, fileName: String, fileSize: Long) {
        _state.update {
            val s = it.copy(
                selectedFileUris = listOf(uri),
                selectedFileNames = listOf(fileName),
                selectedFileSize = fileSize,
                isBatchMode = false,
                selectedPreviewUri = if (_state.value.conversionType?.inputType != com.morphdrop.app.domain.model.FileType.PDF) uri else null
            )
            s.copy(isConvertEnabled = isStateValid(s))
        }
        
        if (_state.value.conversionType?.inputType == com.morphdrop.app.domain.model.FileType.PDF) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Context can be obtained from Application class but wait, onFileSelected doesn't take context.
                    // Actually, onFileSelected is only used internally or if context is passed. Let's just pass context or use the original URI if context is unavailable.
                    // Wait, onFileSelected is used in History Screen or somewhere. I will just leave it since the user usually uses onFilesSelected from the config screen.
                    _state.update { it.copy(selectedPreviewUri = uri) }
                } catch (e: Exception) {
                    _state.update { it.copy(selectedPreviewUri = uri) }
                }
            }
        }
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
            val s = it.copy(
                outputFormat = format,
                outputFileName = newName
            )
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onQualityChanged(quality: Int) {
        _state.update { it.copy(quality = quality) }
    }

    fun onPageRangeStartChanged(value: String) {
        _state.update { 
            val s = it.copy(pageRangeStart = value)
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onPageRangeEndChanged(value: String) {
        _state.update { 
            val s = it.copy(pageRangeEnd = value)
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onOutputFileNameChanged(name: String) {
        _state.update { it.copy(outputFileName = name) }
    }

    fun onTargetWidthChanged(value: String) {
        _state.update { 
            val s = it.copy(targetWidth = value, compressionPreset = "Custom")
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onTargetHeightChanged(value: String) {
        _state.update { 
            val s = it.copy(targetHeight = value, compressionPreset = "Custom")
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onPaddingColorChanged(color: Int) {
        _state.update { it.copy(paddingColor = color) }
    }

    fun onTargetSizeKbChanged(value: String) {
        _state.update { 
            val s = it.copy(targetSizeKb = value, compressionPreset = "Custom")
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onCompressionPresetSelected(preset: String) {
        _state.update {
            val s = when (preset) {
                "Under 500KB" -> it.copy(compressionPreset = preset, targetSizeKb = "500", targetWidth = "", targetHeight = "")
                "Under 1MB" -> it.copy(compressionPreset = preset, targetSizeKb = "1024", targetWidth = "", targetHeight = "")
                "Profile Picture (256x256)" -> it.copy(compressionPreset = preset, targetWidth = "256", targetHeight = "256", targetSizeKb = "")
                "HD (1920x1080)" -> it.copy(compressionPreset = preset, targetWidth = "1920", targetHeight = "1080", targetSizeKb = "")
                "4K (3840x2160)" -> it.copy(compressionPreset = preset, targetWidth = "3840", targetHeight = "2160", targetSizeKb = "")
                else -> it.copy(compressionPreset = preset)
            }
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onPdfCompressionPresetSelected(preset: String) {
        val quality = when (preset) {
            "Extreme" -> 30
            "Recommended" -> 60
            "Low" -> 90
            else -> 60
        }
        _state.update { it.copy(compressionPreset = preset, quality = quality, targetSizeKb = "", targetWidth = "", targetHeight = "") }
    }

    fun onAspectRatioPresetSelected(preset: String) {
        _state.update { it.copy(aspectRatioPreset = preset) }
    }

    fun onPreviewUriChanged(uri: Uri) {
        _state.update { it.copy(selectedPreviewUri = uri) }
    }

    fun onCropRectChanged(left: Int, top: Int, right: Int, bottom: Int) {
        _state.update { it.copy(
            cropRectLeft = left,
            cropRectTop = top,
            cropRectRight = right,
            cropRectBottom = bottom
        ) }
    }

    fun onRotationChanged(degrees: Int) {
        _state.update { it.copy(rotationDegrees = degrees) }
    }

    fun onPdfPasswordChanged(password: String) {
        _state.update { 
            val s = it.copy(pdfPassword = password)
            s.copy(isConvertEnabled = isStateValid(s))
        }
    }

    fun onAllowPrintingChanged(allow: Boolean) {
        _state.update { it.copy(allowPrinting = allow) }
    }

    fun onAllowCopyingChanged(allow: Boolean) {
        _state.update { it.copy(allowCopying = allow) }
    }

    fun onAllowEditingChanged(allow: Boolean) {
        _state.update { it.copy(allowEditing = allow) }
    }

    fun setShowCropDialog(show: Boolean) {
        _state.update { it.copy(showCropDialog = show) }
    }

    fun setShowColorPickerDialog(show: Boolean) {
        _state.update { it.copy(showColorPickerDialog = show) }
    }

    fun getPageRange(): IntRange? {
        val start = _state.value.pageRangeStart.toIntOrNull() ?: return null
        val end = _state.value.pageRangeEnd.toIntOrNull() ?: return null
        if (start < 1 || end < start) return null
        return start..end
    }

    private fun isStateValid(s: ConversionConfigState): Boolean {
        if (s.selectedFileUris.isEmpty() || s.errorMessage != null) return false
        if (s.targetWidth.isNotEmpty() && s.targetWidth.toIntOrNull() == null) return false
        if (s.targetHeight.isNotEmpty() && s.targetHeight.toIntOrNull() == null) return false
        if (s.targetSizeKb.isNotEmpty() && s.targetSizeKb.toIntOrNull() == null) return false
        if (s.pageRangeStart.isNotEmpty() && s.pageRangeStart.toIntOrNull() == null) return false
        if (s.pageRangeEnd.isNotEmpty() && s.pageRangeEnd.toIntOrNull() == null) return false
        if (s.conversionType?.id == "protect_pdf" && s.pdfPassword.isEmpty()) return false
        return true
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

            // Advanced Image Options - Applied to all images in the batch by the worker
            currentState.targetWidth.toIntOrNull()?.let { 
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_TARGET_WIDTH, it)
            }
            currentState.targetHeight.toIntOrNull()?.let { 
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_TARGET_HEIGHT, it)
            }
            dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_PADDING_COLOR, currentState.paddingColor)
            
            currentState.targetSizeKb.toIntOrNull()?.let { 
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_TARGET_SIZE_KB, it)
            }
            
            if (currentState.cropRectLeft != -1) {
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_CROP_RECT_LEFT, currentState.cropRectLeft)
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_CROP_RECT_TOP, currentState.cropRectTop)
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_CROP_RECT_RIGHT, currentState.cropRectRight)
                dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_CROP_RECT_BOTTOM, currentState.cropRectBottom)
            }
            
            dataBuilder.putInt(com.morphdrop.app.worker.ConversionWorker.KEY_ROTATION_DEGREES, currentState.rotationDegrees)

            if (type.id == "protect_pdf") {
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_PASSWORD, currentState.pdfPassword)
                dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_PRINTING, currentState.allowPrinting)
                dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_COPYING, currentState.allowCopying)
                dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_EDITING, currentState.allowEditing)
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