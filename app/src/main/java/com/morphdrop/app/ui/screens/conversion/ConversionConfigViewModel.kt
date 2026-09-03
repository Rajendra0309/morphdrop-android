package com.morphdrop.app.ui.screens.conversion

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.ui.components.WorkbenchImageItem
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.MediaThumbnailHelper
import com.morphdrop.app.util.PdfThumbnailHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
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
    val id: String = UUID.randomUUID().toString()
)

data class ConversionConfigState(
    val conversionType: ConversionType? = null,
    val selectedFileUris: List<Uri> = emptyList(),
    val workbenchImageItems: List<WorkbenchImageItem> = emptyList(),
    val expandedImageId: String? = null,
    val mergeItems: List<MergeItem> = emptyList(),
    val selectedFileNames: List<String> = emptyList(),
    val selectedFileSize: Long = -1,
    val outputFormat: String = "",
    val quality: Int = 90,
    val resizeOption: String = "Original", // "Original", "50%", "25%", "Custom"
    val stripMetadata: Boolean = false,
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
    val errorMessage: String? = null,
    // Metadata Inspector & Editor State
    val fileMetadata: com.morphdrop.app.domain.model.FileMetadata? = null,
    val isMetadataLoading: Boolean = false,
    val editAuthor: String = "",
    val editTitle: String = "",
    val editSubject: String = "",
    val editSoftware: String = "",
    val editCopyright: String = "",
    val editDateCreated: String = "",
    val editLatitude: String = "",
    val editLongitude: String = "",
    val editCameraMake: String = "",
    val editCameraModel: String = "",
    val gpsError: String? = null,
    val metadataAction: String = "scrub"
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
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val metadataUseCase: com.morphdrop.app.domain.usecase.conversion.MetadataUseCase
) : ViewModel() {

    private val conversionTypeId: String = savedStateHandle["conversionTypeId"] ?: ""

    private val _state = MutableStateFlow(
        ConversionConfigState(
            conversionType = ConversionType.defaultList.find { it.id == conversionTypeId }
        )
    )
    val state: StateFlow<ConversionConfigState> = _state.asStateFlow()

    init {
        val type = ConversionType.defaultList.find { it.id == conversionTypeId }
        if (type != null) {
            viewModelScope.launch {
                val savedFormat = settingsRepository.lastImageFormat.first()
                val savedQuality = settingsRepository.lastImageQuality.first()
                val savedResize = settingsRepository.lastImageResizeOption.first()
                val savedStrip = settingsRepository.lastStripMetadata.first()

                _state.update {
                    it.copy(
                        outputFormat = if (isImageOutput(type)) savedFormat else type.outputType.extension,
                        quality = if (isImageOutput(type)) savedQuality else if (type.id == "compress_pdf") 60 else it.quality,
                        resizeOption = savedResize,
                        stripMetadata = savedStrip,
                        showQualitySlider = isImageOutput(type) || type.id == "compress_pdf",
                        showPageRange = type.id in listOf("split_pdf", "pdf_to_images", "organize_pdf"),
                        availableOutputFormats = getFormatsForType(type),
                        compressionPreset = if (type.id == "compress_pdf") "Recommended" else it.compressionPreset
                    )
                }
            }
        }
    }

    fun getAllowedExtensions(type: ConversionType?): List<String> {
        val t = type ?: return emptyList()
        return when (t.id) {
            "excel_to_pdf" -> listOf("xls", "xlsx", "csv")
            "text_to_pdf" -> listOf("txt")
            "md_to_pdf" -> listOf("md", "markdown")
            "pdf_to_images", "split_pdf", "compress_pdf", "protect_pdf", "unlock_pdf", "organize_pdf", "merge_pdf" -> listOf("pdf")
            "images_to_pdf", "compress_images", "image_converter" -> listOf("png", "jpg", "jpeg", "webp", "bmp")
            "metadata_editor" -> emptyList() // Empty list = ALL file types allowed!
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
        val currentImageItems = if (append) _state.value.workbenchImageItems else emptyList()

        val selectedUris = currentUris + incomingUris
        val names = currentNames.toMutableList()
        var totalSize = if (append) _state.value.selectedFileSize.coerceAtLeast(0L) else 0L

        var hasInvalidFile = false
        var invalidFileName = ""

        val newImageItems = mutableListOf<WorkbenchImageItem>()

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

            if (isImageInput(type)) {
                val workingCopy = createWorkingCacheCopy(context, uri)
                newImageItems.add(
                    WorkbenchImageItem(
                        originalUri = uri,
                        workingUri = workingCopy,
                        name = name,
                        size = size,
                        isSelected = true
                    )
                )
            }
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

        val isMultipleImages = selectedUris.size > 1
        val outName = if (isMultipleImages && type?.id != "merge_pdf") {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
            "Batch_$timestamp"
        } else if (isFolderOutput(type)) {
            "${baseName}_extracted"
        } else if (type?.id == "merge_pdf" && isMultipleImages) {
            "${baseName}_merged.$outputExt"
        } else {
            "${baseName}_converted.$outputExt"
        }

        val allImageItems = currentImageItems + newImageItems

        _state.update {
            val s = it.copy(
                selectedFileUris = selectedUris,
                selectedFileNames = names,
                selectedFileSize = totalSize,
                workbenchImageItems = allImageItems,
                mergeItems = selectedUris.mapIndexed { idx, uri ->
                    MergeItem(uri, names.getOrElse(idx) { "File" })
                },
                outputFileName = if (it.outputFileName.isBlank() || !append) outName else it.outputFileName,
                isBatchMode = selectedUris.size > 1,
                selectedPreviewUri = selectedUris.firstOrNull(),
                errorMessage = null
            )
            s.copy(isConvertEnabled = isStateValid(s))
        }

        if (selectedUris.isNotEmpty()) {
            val firstUri = selectedUris.first()
            val mime = FileHelper.getMimeType(context, firstUri)
            val ext = firstFileName.substringAfterLast('.', "").lowercase()
            val isPdfFile = mime == "application/pdf" || ext == "pdf"
            val isMediaFile = mime.startsWith("video/") || mime.startsWith("audio/") || ext in listOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "mp3", "flac", "wav")

            viewModelScope.launch {
                val thumbUri = when {
                    isPdfFile -> PdfThumbnailHelper.getThumbnailUri(context, firstUri, 0)
                    isMediaFile -> MediaThumbnailHelper.getMediaThumbnailUri(context, firstUri)
                    else -> null
                }
                if (thumbUri != null) {
                    _state.update { it.copy(selectedPreviewUri = thumbUri) }
                }
            }
        }

        if (type?.inputType == FileType.PDF && selectedUris.isNotEmpty()) {
            _state.update { it.copy(isPdfLoading = true) }
            viewModelScope.launch {
                val allWorkbenchPages = mutableListOf<WorkbenchPage>()
                var totalPagesCount = 0
                
                for ((idx, uri) in selectedUris.withIndex()) {
                    val pageCount = getPdfPageCount(context, uri)
                    totalPagesCount += pageCount
                    val fileName = names.getOrElse(idx) { "File" }
                    val thumb = PdfThumbnailHelper.getThumbnailUri(context, uri, 0)
                    
                    _state.update { s ->
                        val updatedMergeItems = s.mergeItems.toMutableList()
                        val itemIndex = updatedMergeItems.indexOfFirst { it.uri == uri }
                        if (itemIndex != -1) {
                            updatedMergeItems[itemIndex] = updatedMergeItems[itemIndex].copy(thumbnailUri = thumb)
                        }
                        
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
                
                if (totalPagesCount == 0 && type?.id != "unlock_pdf") {
                    _state.update { it.copy(errorMessage = "Could not read PDF pages. The file(s) might be protected or corrupted.") }
                }
            }
        }

        if (type?.id == "metadata_editor" && selectedUris.isNotEmpty()) {
            loadMetadata(context, selectedUris.first())
        }
    }

    fun loadMetadata(context: Context, uri: Uri) {
        _state.update { it.copy(isMetadataLoading = true) }
        viewModelScope.launch {
            val meta = metadataUseCase.inspectMetadata(context, uri)
            val defaultOutputName = "${FileHelper.getFileNameWithoutExtension(meta.fileName)}_metadata_edited"
            _state.update {
                it.copy(
                    fileMetadata = meta,
                    isMetadataLoading = false,
                    editAuthor = meta.author ?: "",
                    editTitle = meta.title ?: "",
                    editSubject = meta.subject ?: "",
                    editSoftware = meta.software ?: "",
                    editCopyright = meta.copyright ?: "",
                    editDateCreated = meta.dateCreated ?: "",
                    editLatitude = meta.latitude?.toString() ?: "",
                    editLongitude = meta.longitude?.toString() ?: "",
                    editCameraMake = meta.cameraMake ?: "",
                    editCameraModel = meta.cameraModel ?: "",
                    outputFileName = defaultOutputName,
                    isConvertEnabled = true
                )
            }
        }
    }

    fun onEditAuthorChanged(value: String) {
        _state.update { it.copy(editAuthor = value) }
    }

    fun onEditTitleChanged(value: String) {
        _state.update { it.copy(editTitle = value) }
    }

    fun onEditSubjectChanged(value: String) {
        _state.update { it.copy(editSubject = value) }
    }

    fun onEditSoftwareChanged(value: String) {
        _state.update { it.copy(editSoftware = value) }
    }

    fun onEditCopyrightChanged(value: String) {
        _state.update { it.copy(editCopyright = value) }
    }

    fun onEditDateCreatedChanged(value: String) {
        _state.update { it.copy(editDateCreated = value) }
    }

    fun onEditLatitudeChanged(value: String) {
        val lat = value.toDoubleOrNull()
        val err = if (value.isNotBlank() && (lat == null || lat !in -90.0..90.0)) "Latitude must be between -90 and 90" else null
        _state.update { it.copy(editLatitude = value, gpsError = err) }
    }

    fun onEditLongitudeChanged(value: String) {
        val lng = value.toDoubleOrNull()
        val err = if (value.isNotBlank() && (lng == null || lng !in -180.0..180.0)) "Longitude must be between -180 and 180" else null
        _state.update { it.copy(editLongitude = value, gpsError = err) }
    }

    fun onEditCameraMakeChanged(value: String) {
        _state.update { it.copy(editCameraMake = value) }
    }

    fun onEditCameraModelChanged(value: String) {
        _state.update { it.copy(editCameraModel = value) }
    }

    fun startMetadataWorker(
        context: Context,
        action: String,
        editParams: com.morphdrop.app.domain.model.MetadataEditParams? = null
    ): UUID? {
        val currentState = _state.value
        val inputUri = currentState.selectedFileUri ?: return null
        val defaultOutputName = if (action == "edit") {
            "${FileHelper.getFileNameWithoutExtension(currentState.selectedFileName)}_metadata_edited"
        } else {
            "${FileHelper.getFileNameWithoutExtension(currentState.selectedFileName)}_metadata_scrubbed"
        }
        val outputName = if (currentState.outputFileName.isNotBlank()) currentState.outputFileName else defaultOutputName

        val dataBuilder = androidx.work.Data.Builder()
            .putString(com.morphdrop.app.worker.ConversionWorker.KEY_CONVERSION_TYPE, "metadata_editor")
            .putString(com.morphdrop.app.worker.ConversionWorker.KEY_INPUT_URI, inputUri.toString())
            .putString(com.morphdrop.app.worker.ConversionWorker.KEY_OUTPUT_FILE_NAME, outputName)
            .putString(com.morphdrop.app.worker.ConversionWorker.KEY_ACTION, action)

        if (action == "edit" && editParams != null) {
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_AUTHOR, editParams.author)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_TITLE, editParams.title)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_SUBJECT, editParams.subject)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_SOFTWARE, editParams.software)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_COPYRIGHT, editParams.copyright)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_DATE_CREATED, editParams.dateCreated)
            if (editParams.latitude != null) dataBuilder.putDouble(com.morphdrop.app.worker.ConversionWorker.KEY_LATITUDE, editParams.latitude)
            if (editParams.longitude != null) dataBuilder.putDouble(com.morphdrop.app.worker.ConversionWorker.KEY_LONGITUDE, editParams.longitude)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_CAMERA_MAKE, editParams.cameraMake)
            dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_CAMERA_MODEL, editParams.cameraModel)
        }

        val request = androidx.work.OneTimeWorkRequestBuilder<com.morphdrop.app.worker.ConversionWorker>()
            .setInputData(dataBuilder.build())
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    private fun createWorkingCacheCopy(context: Context, sourceUri: Uri): Uri {
        return try {
            val destFile = File(context.cacheDir, "working_img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
            FileHelper.readFileFromUri(context, sourceUri).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            sourceUri
        }
    }

    private suspend fun getPdfPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
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
        onFilesSelected(context, listOf(uri), append = false)
    }

    fun onOutputFormatChanged(format: String) {
        val currentType = _state.value.conversionType
        val baseName = _state.value.outputFileName.substringBeforeLast('.')
        
        val newName = if (isFolderOutput(currentType)) {
            baseName
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

        viewModelScope.launch {
            if (isImageOutput(currentType ?: ConversionType.defaultList.first())) {
                settingsRepository.setLastImageFormat(format)
            }
        }
    }

    fun onQualityChanged(quality: Int) {
        _state.update { it.copy(quality = quality) }
        viewModelScope.launch {
            settingsRepository.setLastImageQuality(quality)
        }
    }

    fun onResizeOptionSelected(option: String) {
        _state.update { 
            val s = it.copy(resizeOption = option)
            s.copy(isConvertEnabled = isStateValid(s))
        }
        viewModelScope.launch {
            settingsRepository.setLastImageResizeOption(option)
        }
    }

    fun onStripMetadataChanged(strip: Boolean) {
        _state.update { it.copy(stripMetadata = strip) }
        viewModelScope.launch {
            settingsRepository.setLastStripMetadata(strip)
        }
    }

    // Workbench Grid Actions
    fun onWorkbenchItemClicked(item: WorkbenchImageItem) {
        _state.update { 
            it.copy(expandedImageId = if (it.expandedImageId == item.id) null else item.id)
        }
    }

    fun onWorkbenchItemLongClicked(item: WorkbenchImageItem) {
        _state.update { s ->
            val updated = s.workbenchImageItems.map { 
                if (it.id == item.id) it.copy(isSelected = !it.isSelected) else it 
            }
            val valid = isStateValid(s.copy(workbenchImageItems = updated))
            s.copy(workbenchImageItems = updated, isConvertEnabled = valid)
        }
    }

    fun onSelectAllWorkbenchImages() {
        _state.update { s ->
            val updated = s.workbenchImageItems.map { it.copy(isSelected = true) }
            val valid = isStateValid(s.copy(workbenchImageItems = updated))
            s.copy(workbenchImageItems = updated, isConvertEnabled = valid)
        }
    }

    fun onDeselectAllWorkbenchImages() {
        _state.update { s ->
            val updated = s.workbenchImageItems.map { it.copy(isSelected = false) }
            val valid = isStateValid(s.copy(workbenchImageItems = updated))
            s.copy(workbenchImageItems = updated, isConvertEnabled = valid)
        }
    }

    fun onImageCropped(itemId: String, croppedUri: Uri) {
        _state.update { s ->
            val updated = s.workbenchImageItems.map { 
                if (it.id == itemId) it.copy(workingUri = croppedUri, isEdited = true) else it 
            }
            s.copy(workbenchImageItems = updated)
        }
    }

    fun onRotateWorkbenchImage(item: WorkbenchImageItem) {
        _state.update { s ->
            val updated = s.workbenchImageItems.map { 
                if (it.id == item.id) {
                    it.copy(
                        rotationDegrees = (it.rotationDegrees + 90) % 360,
                        isEdited = true
                    )
                } else it 
            }
            s.copy(workbenchImageItems = updated)
        }
    }

    fun onResetWorkbenchImage(context: Context, item: WorkbenchImageItem) {
        val freshCopy = createWorkingCacheCopy(context, item.originalUri)
        _state.update { s ->
            val updated = s.workbenchImageItems.map { 
                if (it.id == item.id) {
                    it.copy(
                        workingUri = freshCopy,
                        rotationDegrees = 0,
                        isEdited = false
                    )
                } else it 
            }
            s.copy(workbenchImageItems = updated)
        }
    }

    fun onApplyWorkbenchEdit(item: WorkbenchImageItem) {
        _state.update { it.copy(expandedImageId = null) }
    }

    fun onCancelWorkbenchEdit(context: Context, item: WorkbenchImageItem) {
        val freshCopy = createWorkingCacheCopy(context, item.originalUri)
        _state.update { s ->
            val updated = s.workbenchImageItems.map { 
                if (it.id == item.id) {
                    it.copy(
                        workingUri = freshCopy,
                        rotationDegrees = 0,
                        isEdited = false
                    )
                } else it 
            }
            s.copy(workbenchImageItems = updated, expandedImageId = null)
        }
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
        if (s.errorMessage != null) return false
        if (isImageInput(s.conversionType)) {
            val selectedImages = s.workbenchImageItems.filter { it.isSelected }
            if (selectedImages.isEmpty()) return false
        } else if (s.selectedFileUris.isEmpty()) {
            return false
        }

        if (s.conversionType?.id in listOf("merge_pdf", "merge_pdfs")) {
            if (s.selectedFileUris.size < 2) return false
        }
        if (s.targetWidth.isNotEmpty() && s.targetWidth.toIntOrNull() == null) return false
        if (s.targetHeight.isNotEmpty() && s.targetHeight.toIntOrNull() == null) return false
        if (s.targetSizeKb.isNotEmpty() && s.targetSizeKb.toIntOrNull() == null) return false
        if (s.pageRangeStart.isNotEmpty() && s.pageRangeStart.toIntOrNull() == null) return false
        if (s.pageRangeEnd.isNotEmpty() && s.pageRangeEnd.toIntOrNull() == null) return false
        if ((s.conversionType?.id == "protect_pdf" || s.conversionType?.id == "unlock_pdf") && s.pdfPassword.isEmpty()) return false
        return true
    }

    fun startConversion(context: Context): UUID? {
        val currentState = _state.value
        val type = currentState.conversionType ?: return null

        val activeImageItems = currentState.workbenchImageItems.filter { it.isSelected }
        val uris = if (isImageInput(type)) {
            activeImageItems.map { it.workingUri }
        } else {
            currentState.selectedFileUris
        }

        if (uris.isEmpty() || !currentState.isConvertEnabled) return null

        return try {
            val uriStrings: Array<String?> = Array(uris.size) { uris[it].toString() }
            val dataBuilder = androidx.work.Data.Builder()
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_CONVERSION_TYPE, type.id)
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_INPUT_URI, uris.first().toString())
                .putStringArray(com.morphdrop.app.worker.ConversionWorker.KEY_INPUT_URIS, uriStrings)
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_OUTPUT_FILE_NAME, currentState.outputFileName)
                .putString(com.morphdrop.app.worker.ConversionWorker.KEY_TARGET_FORMAT, currentState.outputFormat)
                .putInt(com.morphdrop.app.worker.ConversionWorker.KEY_QUALITY, currentState.quality)
                .putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_STRIP_METADATA, currentState.stripMetadata)

            val resizeScaleFloat = when (currentState.resizeOption) {
                "50%" -> 0.5f
                "25%" -> 0.25f
                else -> -1f
            }
            if (resizeScaleFloat > 0f) {
                dataBuilder.putFloat(com.morphdrop.app.worker.ConversionWorker.KEY_RESIZE_SCALE, resizeScaleFloat)
            }

            if (currentState.pageRangeStart.isNotBlank() && currentState.pageRangeEnd.isNotBlank()) {
                dataBuilder.putString(
                    com.morphdrop.app.worker.ConversionWorker.KEY_PAGE_RANGE,
                    "${currentState.pageRangeStart}-${currentState.pageRangeEnd}"
                )
            }

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
                val selectedOrder = activeWorkbenchPages.map { it.originalIndex }
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_PAGE_ORDER, selectedOrder.joinToString(","))
                val rotationsStr = activeWorkbenchPages.associate { it.originalIndex to (currentState.pageRotations[it.id] ?: 0) }
                    .entries.joinToString(",") { "${it.key}:${it.value}" }
                dataBuilder.putString("page_rotations", rotationsStr)
            }

            if (type.id == "split_pdf") {
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_SPLIT_MODE, currentState.splitMode)
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

            if (type.id == "protect_pdf" || type.id == "unlock_pdf") {
                dataBuilder.putString(com.morphdrop.app.worker.ConversionWorker.KEY_PASSWORD, currentState.pdfPassword)
                if (type.id == "protect_pdf") {
                    dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_PRINTING, currentState.allowPrinting)
                    dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_COPYING, currentState.allowCopying)
                    dataBuilder.putBoolean(com.morphdrop.app.worker.ConversionWorker.KEY_ALLOW_EDITING, currentState.allowEditing)
                }
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
        val isMultipleSelected = _state.value.selectedFileUris.size > 1 || _state.value.workbenchImageItems.count { it.isSelected } > 1
        if (isMultipleSelected && t.id != "merge_pdf" && t.id != "images_to_pdf") {
            return true
        }
        return t.id in listOf("split_pdf", "compress_images", "organize_pdf") || 
               (t.id == "pdf_to_images" && (_state.value.pageRangeEnd.toIntOrNull() ?: 2) - (_state.value.pageRangeStart.toIntOrNull() ?: 1) > 0)
    }

    private fun isImageInput(type: ConversionType?): Boolean {
        val t = type ?: return false
        return t.inputType in listOf(FileType.PNG, FileType.JPG, FileType.WEBP, FileType.BMP) ||
                t.id in listOf("image_converter", "compress_images", "images_to_pdf")
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