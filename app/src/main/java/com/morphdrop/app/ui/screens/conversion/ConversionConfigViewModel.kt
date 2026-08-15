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

data class MergeItem(
    val uri: Uri,
    val name: String,
    val pageRange: String = "",
    val thumbnailUri: Uri? = null
)

data class WorkbenchPage(
    val uri: Uri,
    val originalIndex: Int,
    val sourceFileName: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

data class ConversionConfigState(
    val conversionType: ConversionType? = null,
    val selectedFileUris: List<Uri> = emptyList(),
    val mergeItems: List<MergeItem> = emptyList(),
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
    val pdfPageCount: Int = 0,
    val workbenchPages: List<WorkbenchPage> = emptyList(),
    val selectedWorkbenchPages: Set<String> = emptySet(), // Set of IDs
    val pageRotations: Map<String, Int> = emptyMap(), // ID to degrees
    val showOrganizerDialog: Boolean = false,
    val isPdfLoading: Boolean = false,
    val splitMode: String = "selection", // "selection", "every_n", "all"
    val splitEveryN: Int = 1,
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

    fun onFilesSelected(context: Context, uris: List<Uri>, append: Boolean = false) {
        if (uris.isEmpty()) return

        val type = _state.value.conversionType
        val allowedExts = getAllowedExtensions(type)
        val isMultiAllowed = type?.isMultiFileAllowed == true

        val incomingUris = if (!isMultiAllowed && uris.size > 1) {
            listOf(uris.first())
        } else {
            uris
        }

        val currentUris = if (append) _state.value.selectedFileUris else emptyList()
        val currentNames = if (append) _state.value.selectedFileNames else emptyList()
        
        val selectedUris = currentUris + incomingUris
        val names = currentNames.toMutableList()
        var totalSize = if (append) _state.value.selectedFileSize.coerceAtLeast(0L) else 0L

        var hasInvalidFile = false
        var invalidFileName = ""

        for (uri in incomingUris) {
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
                if (type?.id == "merge_pdf") "${baseName}_merged.$outputExt"
                else "converted_batch_${System.currentTimeMillis()}.$outputExt"
            } else {
                "${baseName}_converted.$outputExt"
            }
        }

        _state.update {
            val s = it.copy(
                selectedFileUris = selectedUris,
                selectedFileNames = names,
                selectedFileSize = totalSize,
                mergeItems = selectedUris.mapIndexed { idx, uri ->
                    MergeItem(uri, names.getOrElse(idx) { "File" })
                },
                outputFileName = if (it.outputFileName.isBlank() || !append) outName else it.outputFileName,
                isBatchMode = selectedUris.size > 1,
                selectedPreviewUri = selectedUris.firstOrNull(), // Use raw URI as fallback for dimension calculation
                errorMessage = null
            )
            s.copy(isConvertEnabled = isStateValid(s))
        }

        if (type?.inputType == com.morphdrop.app.domain.model.FileType.PDF && selectedUris.isNotEmpty()) {
            _state.update { it.copy(isPdfLoading = true) }
            viewModelScope.launch {
                val allWorkbenchPages = mutableListOf<WorkbenchPage>()
                var totalPagesCount = 0
                
                for ((idx, uri) in selectedUris.withIndex()) {
                    val pageCount = getPdfPageCount(context, uri)
                    totalPagesCount += pageCount
                    val fileName = names.getOrElse(idx) { "File" }
                    
                    // Generate a thumbnail for the first page of this file
                    val thumb = PdfThumbnailHelper.getThumbnailUri(context, uri, 0)
                    
                    // Update this specific merge item in the list immediately to show in carousel
                    _state.update { s ->
                        val updatedMergeItems = s.mergeItems.toMutableList()
                        val itemIndex = updatedMergeItems.indexOfFirst { it.uri == uri }
                        if (itemIndex != -1) {
                            updatedMergeItems[itemIndex] = updatedMergeItems[itemIndex].copy(thumbnailUri = thumb)
                        }
                        
                        // For the very first file, update the main preview immediately
                        val newPreviewUri = if (idx == 0 && thumb != null) thumb else s.selectedPreviewUri
                        
                        s.copy(
                            mergeItems = updatedMergeItems,
                            selectedPreviewUri = newPreviewUri
                        )
                    }

                    for (i in 0 until pageCount) {
                        allWorkbenchPages.add(WorkbenchPage(uri, i, fileName))
                    }
                }

                _state.update { 
                    it.copy(
                        pdfPageCount = totalPagesCount,
                        workbenchPages = allWorkbenchPages,
                        selectedWorkbenchPages = allWorkbenchPages.map { p -> p.id }.toSet(),
                        pageRotations = emptyMap(),
                        isPdfLoading = false,
                        showOrganizerDialog = type.id in listOf("page_editor", "split_pdf", "merge_pdf")
                    )
                }
                
                if (totalPagesCount == 0) {
                    _state.update { it.copy(errorMessage = "Could not read PDF pages. The file(s) might be protected or corrupted.") }
                }
            }
        }
    }

    private suspend fun getPdfPageCount(context: Context, uri: Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun onFileSelected(context: Context, uri: Uri, fileName: String, fileSize: Long) {
        val type = _state.value.conversionType
        _state.update {
            val s = it.copy(
                selectedFileUris = listOf(uri),
                selectedFileNames = listOf(fileName),
                selectedFileSize = fileSize,
                mergeItems = if (type?.id == "merge_pdf") listOf(MergeItem(uri, fileName)) else emptyList(),
                isBatchMode = false,
                selectedPreviewUri = if (type?.inputType != com.morphdrop.app.domain.model.FileType.PDF) uri else null
            )
            s.copy(isConvertEnabled = isStateValid(s))
        }
        
        if (type?.inputType == com.morphdrop.app.domain.model.FileType.PDF) {
            _state.update { it.copy(isPdfLoading = true) }
            viewModelScope.launch {
                val pageCount = getPdfPageCount(context, uri)
                val workbenchPages = (0 until pageCount).map { i -> WorkbenchPage(uri, i, fileName) }
                val initialSelected = workbenchPages.map { it.id }.toSet()

                _state.update { 
                    it.copy(
                        pdfPageCount = pageCount,
                        workbenchPages = workbenchPages,
                        selectedWorkbenchPages = initialSelected,
                        pageRotations = emptyMap(),
                        isPdfLoading = false,
                        showOrganizerDialog = type.id in listOf("page_editor", "split_pdf", "merge_pdf"),
                        selectedPreviewUri = uri // Use original as fallback for now
                    )
                }

                if (pageCount > 0) {
                    val previewUri = PdfThumbnailHelper.getThumbnailUri(context, uri)
                    if (previewUri != null) {
                        _state.update { it.copy(selectedPreviewUri = previewUri) }
                    }
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

    fun setShowOrganizerDialog(show: Boolean) {
        _state.update { it.copy(showOrganizerDialog = show) }
    }

    fun togglePageSelection(pageId: String) {
        _state.update { 
            val newSelected = it.selectedWorkbenchPages.toMutableSet()
            if (newSelected.contains(pageId)) {
                newSelected.remove(pageId)
            } else {
                newSelected.add(pageId)
            }
            it.copy(selectedWorkbenchPages = newSelected)
        }
    }

    fun rotatePage(pageId: String) {
        _state.update { 
            val newRotations = it.pageRotations.toMutableMap()
            val currentRotation = newRotations[pageId] ?: 0
            newRotations[pageId] = (currentRotation + 90) % 360
            it.copy(pageRotations = newRotations)
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _state.value.workbenchPages.indices || toIndex !in _state.value.workbenchPages.indices) return
        _state.update { 
            val newList = it.workbenchPages.toMutableList()
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            it.copy(workbenchPages = newList)
        }
    }

    fun onSplitModeChanged(mode: String) {
        _state.update { it.copy(splitMode = mode) }
    }

    fun onSplitEveryNChanged(n: Int) {
        _state.update { it.copy(splitEveryN = n.coerceAtLeast(1)) }
    }

    fun onMoveMergeItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _state.value.mergeItems.indices || toIndex !in _state.value.mergeItems.indices) return
        _state.update { 
            val newList = it.mergeItems.toMutableList()
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            it.copy(mergeItems = newList)
        }
    }

    fun onMergeItemRangeChanged(index: Int, range: String) {
        if (index !in _state.value.mergeItems.indices) return
        _state.update { 
            val newList = it.mergeItems.toMutableList()
            newList[index] = newList[index].copy(pageRange = range)
            it.copy(mergeItems = newList)
        }
    }

    fun getPageRange(): IntRange? {
        val start = _state.value.pageRangeStart.toIntOrNull() ?: return null
        val end = _state.value.pageRangeEnd.toIntOrNull() ?: return null
        if (start < 1 || end < start) return null
        return start..end
    }

    private fun isStateValid(s: ConversionConfigState): Boolean {
        if (s.selectedFileUris.isEmpty() || s.errorMessage != null) return false
        if (s.conversionType?.id in listOf("merge_pdf", "merge_pdfs")) {
            if (s.selectedFileUris.size < 2) return false
        }
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

            if (type.id == "page_editor") {
                val activeWorkbenchPages = currentState.workbenchPages.filter { currentState.selectedWorkbenchPages.contains(it.id) }
                
                // Map to indices for the worker (which currently expects indices for ONE file)
                val selectedOrder = activeWorkbenchPages.map { it.originalIndex }
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_PAGE_ORDER, selectedOrder.joinToString(","))
                
                // Map rotations using original index
                val rotationsStr = activeWorkbenchPages.associate { it.originalIndex to (currentState.pageRotations[it.id] ?: 0) }
                    .entries.joinToString(",") { "${it.key}:${it.value}" }
                dataBuilder.putString("page_rotations", rotationsStr)
            }

            if (type.id == "split_pdf") {
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_SPLIT_MODE, currentState.splitMode)
                
                // For Split, we use workbench pages too
                val activeWorkbenchPages = currentState.workbenchPages.filter { currentState.selectedWorkbenchPages.contains(it.id) }
                val pageOrder = activeWorkbenchPages.map { it.originalIndex }.joinToString(",")
                val rotationsStr = activeWorkbenchPages.associate { it.originalIndex to (currentState.pageRotations[it.id] ?: 0) }
                    .entries.joinToString(",") { "${it.key}:${it.value}" }
                
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_PAGE_ORDER, pageOrder)
                dataBuilder.putString("page_rotations", rotationsStr)

                when (currentState.splitMode) {
                    "selection" -> {
                        val selectedIndices = activeWorkbenchPages.map { it.originalIndex }
                        dataBuilder.putString("split_indices", selectedIndices.joinToString(","))
                    }
                    "every_n" -> {
                        dataBuilder.putInt("split_every_n", currentState.splitEveryN)
                        val selectedIndices = activeWorkbenchPages.map { it.originalIndex }
                        dataBuilder.putString("split_indices", selectedIndices.joinToString(","))
                    }
                    "all" -> {
                        val selectedIndices = activeWorkbenchPages.map { it.originalIndex }
                        dataBuilder.putString("split_indices", selectedIndices.joinToString(","))
                    }
                }
            }

            if (type.id == "protect_pdf") {
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_PASSWORD, currentState.pdfPassword)
                dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_PRINTING, currentState.allowPrinting)
                dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_COPYING, currentState.allowCopying)
                dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_EDITING, currentState.allowEditing)
            }

            if (type.id == "merge_pdf" || type.id == "merge_pdfs") {
                val activeWorkbenchPages = currentState.workbenchPages.filter { currentState.selectedWorkbenchPages.contains(it.id) }
                val mergeData = activeWorkbenchPages.map { p ->
                    mapOf(
                        "uri" to p.uri.toString(),
                        "index" to p.originalIndex.toString(),
                        "rotation" to (currentState.pageRotations[p.id] ?: 0).toString()
                    )
                }
                val json = com.google.gson.Gson().toJson(mergeData)
                dataBuilder.putString("merge_payload", json)
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