package com.morphdrop.app.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.usecase.conversion.CompressPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.CompressionLevel
import com.morphdrop.app.domain.usecase.conversion.ExcelToPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.ImageConverterUseCase
import com.morphdrop.app.domain.usecase.conversion.ImagesToPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.MdToPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.MetadataUseCase
import com.morphdrop.app.domain.usecase.conversion.MergePdfUseCase
import com.morphdrop.app.domain.usecase.conversion.MergePdfItem
import com.morphdrop.app.domain.usecase.conversion.PdfPageEditorUseCase
import com.morphdrop.app.domain.usecase.conversion.PdfPasswordUseCase
import com.morphdrop.app.domain.usecase.conversion.PdfToImagesUseCase
import com.morphdrop.app.domain.usecase.conversion.ReorderPdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.RotatePdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.SplitPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.TextToPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.WatermarkPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.AddPageNumbersUseCase
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class ConversionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val pdfToImagesUseCase: PdfToImagesUseCase,
    private val imagesToPdfUseCase: ImagesToPdfUseCase,
    private val excelToPdfUseCase: ExcelToPdfUseCase,
    private val textToPdfUseCase: TextToPdfUseCase,
    private val mdToPdfUseCase: MdToPdfUseCase,
    private val imageConverterUseCase: ImageConverterUseCase,
    private val mergePdfUseCase: MergePdfUseCase,
    private val splitPdfUseCase: SplitPdfUseCase,
    private val compressPdfUseCase: CompressPdfUseCase,
    private val rotatePdfPagesUseCase: RotatePdfPagesUseCase,
    private val watermarkPdfUseCase: WatermarkPdfUseCase,
    private val addPageNumbersUseCase: AddPageNumbersUseCase,
    private val reorderPdfPagesUseCase: ReorderPdfPagesUseCase,
    private val pdfPasswordUseCase: PdfPasswordUseCase,
    private val pdfPageEditorUseCase: PdfPageEditorUseCase,
    private val metadataUseCase: MetadataUseCase,
    private val historyRepository: HistoryRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_CONVERSION_TYPE = "conversion_type"
        const val KEY_INPUT_URI = "input_uri"
        const val KEY_INPUT_URIS = "input_uris"
        const val KEY_OUTPUT_FILE_NAME = "output_file_name"
        const val KEY_QUALITY = "quality"
        const val KEY_PAGE_RANGE = "page_range"
        const val KEY_PAGE_ORDER = "page_order"
        const val KEY_ROTATION_DEGREES = "rotation_degrees"
        const val KEY_PASSWORD = "password"
        const val KEY_ACTION = "action"
        const val KEY_TARGET_FORMAT = "target_format"
        const val KEY_SPLIT_MODE = "split_mode"

        const val KEY_ALLOW_PRINTING = "allow_printing"
        const val KEY_ALLOW_COPYING = "allow_copying"
        const val KEY_ALLOW_EDITING = "allow_editing"

        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_URIS = "output_uris"
        const val KEY_ERROR = "error"
        
        // Advanced Image Tool Keys
        const val KEY_TARGET_WIDTH = "target_width"
        const val KEY_TARGET_HEIGHT = "target_height"
        const val KEY_RESIZE_SCALE = "resize_scale"
        const val KEY_PADDING_COLOR = "padding_color"
        const val KEY_TARGET_SIZE_KB = "target_size_kb"
        const val KEY_STRIP_METADATA = "strip_metadata"
        const val KEY_CROP_RECT_LEFT = "crop_rect_left"
        const val KEY_CROP_RECT_TOP = "crop_rect_top"
        const val KEY_CROP_RECT_RIGHT = "crop_rect_right"
        const val KEY_CROP_RECT_BOTTOM = "crop_rect_bottom"

        // Metadata Tool Keys
        const val KEY_AUTHOR = "author"
        const val KEY_TITLE = "title"
        const val KEY_SUBJECT = "subject"
        const val KEY_SOFTWARE = "software"
        const val KEY_COPYRIGHT = "copyright"
        const val KEY_DATE_CREATED = "date_created"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_CAMERA_MAKE = "camera_make"
        const val KEY_CAMERA_MODEL = "camera_model"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversionType = inputData.getString(KEY_CONVERSION_TYPE) ?: "File Conversion"
        val startTime = System.currentTimeMillis()
        val notificationId = id.hashCode()
        val cancelPendingIntent = androidx.work.WorkManager.getInstance(appContext).createCancelPendingIntent(id)

        var convertedCount = 0
        var totalImageCount = 0
        var isBatchImageConversion = false

        fun checkCancellation() {
            if (isStopped) throw kotlinx.coroutines.CancellationException("Worker stopped by user")
        }

        val inputUriString = inputData.getString(KEY_INPUT_URI)
        val inputUrisArray = inputData.getStringArray(KEY_INPUT_URIS)
        val inputFileName = inputUriString?.let {
            FileHelper.getFileName(appContext, Uri.parse(it))
        } ?: if (!inputUrisArray.isNullOrEmpty()) {
            "${inputUrisArray.size} Files"
        } else {
            "Input File"
        }

        val outputFileNameInput = inputData.getString(KEY_OUTPUT_FILE_NAME) ?: "Converted_File"
        val outputFileName = if (!outputFileNameInput.contains(".")) {
            val inputExt = inputFileName.substringAfterLast('.', "").lowercase()
            val ext = when (conversionType) {
                "pdf_to_images", "split_pdf" -> "" // Folder
                "images_to_pdf", "excel_to_pdf", "txt_to_pdf", "md_to_pdf", "compress_pdf" -> "pdf"
                "image_converter", "compress_images" -> inputData.getString(KEY_TARGET_FORMAT) ?: "jpg"
                "metadata_editor" -> inputExt
                else -> if (inputExt.isNotBlank()) inputExt else "pdf"
            }
            if (ext.isNotEmpty()) "$outputFileNameInput.$ext" else outputFileNameInput
        } else outputFileNameInput

        val generatedFileNames = mutableListOf<String>()

        try {
            try {
                val foregroundInfo = notificationHelper.createForegroundInfo(
                    notificationId,
                    mapIdToDisplayName(conversionType),
                    0,
                    cancelPendingIntent
                )
                setForeground(foregroundInfo)
            } catch (_: Throwable) {
                // Foreground service may be constrained by OS policy
            }

            notificationHelper.showProgressNotification(
                notificationId,
                mapIdToDisplayName(conversionType),
                5,
                cancelPendingIntent
            )
            setProgress(workDataOf(
                "progress" to 5,
                "output_name" to outputFileName
            ))

            val resultUris: List<Uri> = when (conversionType) {
                "pdf_to_images" -> {
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val quality = inputData.getInt(KEY_QUALITY, 80)
                    val rangeStr = inputData.getString(KEY_PAGE_RANGE)
                    val pageRange = parsePageRange(rangeStr)
                    val formatStr = inputData.getString(KEY_TARGET_FORMAT) ?: "png"
                    val result = pdfToImagesUseCase(
                        pdfUri = uri, 
                        outputFormat = formatStr, 
                        quality = quality, 
                        pageRange = pageRange,
                        outputFolderName = outputFileName
                    )
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 80, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "images_to_pdf", "image_to_pdf" -> {
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uris = inputUrisArray?.map { Uri.parse(it) }
                        ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                    val result = listOf(imagesToPdfUseCase(uris, outputFileName = outputFileName))
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 80, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "excel_to_pdf" -> {
                    checkCancellation()
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(excelToPdfUseCase(
                        xlsxUri = uri, 
                        outputFileName = outputFileName,
                        onProgress = { p ->
                            checkCancellation()
                            val mappedProgress = 20 + (p * 0.7).toInt()
                            notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), mappedProgress, cancelPendingIntent)
                            kotlinx.coroutines.runBlocking { setProgress(workDataOf("progress" to mappedProgress)) }
                        }
                    ))
                    result
                }

                "txt_to_pdf", "text_to_pdf" -> {
                    checkCancellation()
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(textToPdfUseCase(
                        txtUri = uri, 
                        outputFileName = outputFileName,
                        onProgress = { p ->
                            if (isStopped) return@textToPdfUseCase
                            val mappedProgress = 20 + (p * 0.7).toInt()
                            notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), mappedProgress, cancelPendingIntent)
                            kotlinx.coroutines.runBlocking { setProgress(workDataOf("progress" to mappedProgress)) }
                        }
                    ))
                    result
                }

                "md_to_pdf", "markdown_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(mdToPdfUseCase(uri, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 80, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "image_converter", "compress_images" -> {
                    val uris = inputUrisArray?.map { Uri.parse(it) }
                        ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                    
                    totalImageCount = uris.size
                    isBatchImageConversion = totalImageCount > 1

                    val targetFormat = inputData.getString(KEY_TARGET_FORMAT) ?: "jpg"
                    val quality = inputData.getInt(KEY_QUALITY, 90)
                    val stripMetadata = inputData.getBoolean(KEY_STRIP_METADATA, false)
                    
                    val targetWidth = if (inputData.getInt(KEY_TARGET_WIDTH, -1) != -1) inputData.getInt(KEY_TARGET_WIDTH, -1) else null
                    val targetHeight = if (inputData.getInt(KEY_TARGET_HEIGHT, -1) != -1) inputData.getInt(KEY_TARGET_HEIGHT, -1) else null
                    val resizeScaleFloat = if (inputData.getFloat(KEY_RESIZE_SCALE, -1f) > 0f) inputData.getFloat(KEY_RESIZE_SCALE, -1f) else null
                    val paddingColor = if (inputData.getInt(KEY_PADDING_COLOR, Int.MIN_VALUE) != Int.MIN_VALUE) inputData.getInt(KEY_PADDING_COLOR, 0) else null
                    val targetSizeKb = if (inputData.getInt(KEY_TARGET_SIZE_KB, -1) != -1) inputData.getInt(KEY_TARGET_SIZE_KB, -1) else null
                    
                    val cropLeft = inputData.getInt(KEY_CROP_RECT_LEFT, -1)
                    val cropRect = if (cropLeft != -1) {
                        android.graphics.Rect(
                            cropLeft,
                            inputData.getInt(KEY_CROP_RECT_TOP, 0),
                            inputData.getInt(KEY_CROP_RECT_RIGHT, 0),
                            inputData.getInt(KEY_CROP_RECT_BOTTOM, 0)
                        )
                    } else null
                    
                    val rotationDegrees = inputData.getInt(KEY_ROTATION_DEGREES, 0)

                    // Create folder name: MorphDrop/ directly for 1 file, or Batch_YYYY-MM-DD_HH-mm subfolder for multiple
                    val batchSubFolder = if (isBatchImageConversion) {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                        "MorphDrop/Batch_[$timestamp]"
                    } else null

                    val convertedUris = mutableListOf<Uri>()

                    for ((idx, u) in uris.withIndex()) {
                        checkCancellation()

                        val currentStatusText = if (isBatchImageConversion) {
                            "Converting ${idx + 1} of $totalImageCount..."
                        } else {
                            "Converting Image..."
                        }

                        val progressPercent = ((idx.toFloat() / totalImageCount) * 90).toInt().coerceAtLeast(10)
                        notificationHelper.showProgressNotification(
                            notificationId,
                            currentStatusText,
                            progressPercent,
                            cancelPendingIntent
                        )
                        setProgress(workDataOf(
                            "progress" to progressPercent,
                            "stage_text" to currentStatusText,
                            "converted_count" to convertedCount,
                            "total_count" to totalImageCount
                        ))

                        val originalName = FileHelper.getFileName(appContext, u).substringBeforeLast('.')
                        val genName = if (isBatchImageConversion) {
                            "$originalName.$targetFormat"
                        } else {
                            outputFileName
                        }

                        generatedFileNames.add(genName)

                        val convertedUri = imageConverterUseCase(
                            inputUri = u,
                            outputFormat = targetFormat,
                            quality = quality,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            resizeScale = resizeScaleFloat,
                            paddingColor = paddingColor,
                            cropRect = cropRect,
                            rotationDegrees = rotationDegrees,
                            targetSizeKb = targetSizeKb,
                            stripMetadata = stripMetadata,
                            outputFolderName = batchSubFolder,
                            outputFileName = genName
                        )

                        convertedUris.add(convertedUri)
                        convertedCount++
                    }

                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 100, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 100))
                    convertedUris
                }

                "merge_pdf", "merge_pdfs" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    
                    val payload = inputData.getString("merge_payload")
                    val result = if (payload != null) {
                        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
                        val mergeItems: List<Map<String, String>> = com.google.gson.Gson().fromJson(payload, type)
                        
                        val pdfItems = mergeItems.map { item ->
                            MergePdfItem(
                                uri = Uri.parse(item["uri"]),
                                pageIndex = item["index"]?.toInt() ?: 0,
                                rotation = item["rotation"]?.toInt() ?: 0
                            )
                        }
                        
                        listOf(mergePdfUseCase(pdfItems, outputFileName = outputFileName))
                    } else {
                        val uris = inputUrisArray?.map { Uri.parse(it) }
                            ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                        listOf(mergePdfUseCase.legacy(uris, outputFileName = outputFileName))
                    }
                    
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 85, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "split_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    
                    val pageOrder = inputData.getString(KEY_PAGE_ORDER)?.split(",")
                        ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    val selectedIndices = inputData.getString("split_indices")?.split(",")
                        ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                    val rotationsMap = parseRotations(inputData.getString("page_rotations"))
                    
                    val splitMode = inputData.getString(KEY_SPLIT_MODE) ?: "selection"
                    val everyN = inputData.getInt("split_every_n", 1)

                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME)
                    if (outName != null) generatedFileNames.add(outName)
                    
                    val result = splitPdfUseCase(
                        pdfUri = uri,
                        pageOrder = pageOrder,
                        selectedPages = selectedIndices,
                        rotations = rotationsMap,
                        splitMode = splitMode,
                        splitEveryN = everyN,
                        outputFolderName = outName
                    )
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 85, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "compress_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 25, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 25))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val quality = inputData.getInt(KEY_QUALITY, 50)
                    val targetSizeKb = if (inputData.getInt(KEY_TARGET_SIZE_KB, -1) != -1) inputData.getInt(KEY_TARGET_SIZE_KB, -1) else null
                    
                    val level = when {
                        quality <= 40 -> CompressionLevel.HIGH
                        quality <= 70 -> CompressionLevel.MEDIUM
                        else -> CompressionLevel.LOW
                    }
                    val compressResult = compressPdfUseCase(
                        pdfUri = uri, 
                        compressionLevel = level,
                        targetSizeKb = targetSizeKb,
                        outputFileName = outputFileName,
                        onProgress = { iter, max, sz ->
                            checkCancellation()
                            val prog = 25 + ((iter.toFloat() / max.toFloat()) * 60).toInt()
                            notificationHelper.showProgressNotification(notificationId, "Compressing... (Pass $iter of $max)", prog, cancelPendingIntent)
                            kotlinx.coroutines.runBlocking {
                                setProgress(workDataOf("progress" to prog, "iteration" to iter))
                            }
                        }
                    )
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 90, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 90))
                    listOf(compressResult.outputUri)
                }

                "watermark_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val text = inputData.getString("watermark_text") ?: "CONFIDENTIAL"
                    val config = WatermarkConfig(text = text)
                    val result = listOf(watermarkPdfUseCase(uri, config = config, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 90, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 90))
                    result
                }

                "page_numbers_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val config = PageNumberConfig()
                    val result = listOf(addPageNumbersUseCase(uri, config = config, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 90, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 90))
                    result
                }

                "rotate_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val degrees = inputData.getInt(KEY_ROTATION_DEGREES, 90)
                    val result = listOf(rotatePdfPagesUseCase(uri, rotationDegrees = degrees, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 85, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "reorder_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val pageOrderStr = requireNotNull(inputData.getString(KEY_PAGE_ORDER))
                    val orderList = pageOrderStr.split(",").map { it.trim().toInt() }
                    val result = listOf(reorderPdfPagesUseCase(uri, newOrder = orderList, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 85, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "protect_pdf", "unlock_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val password = requireNotNull(inputData.getString(KEY_PASSWORD))
                    
                    val action = if (conversionType == "unlock_pdf") {
                        PdfPasswordUseCase.Action.REMOVE_PASSWORD
                    } else {
                        val actionStr = inputData.getString(KEY_ACTION) ?: "ADD_PASSWORD"
                        if (actionStr == "REMOVE_PASSWORD") PdfPasswordUseCase.Action.REMOVE_PASSWORD else PdfPasswordUseCase.Action.ADD_PASSWORD
                    }
                    
                    val allowPrinting = inputData.getBoolean(KEY_ALLOW_PRINTING, true)
                    val allowCopying = inputData.getBoolean(KEY_ALLOW_COPYING, true)
                    val allowEditing = inputData.getBoolean(KEY_ALLOW_EDITING, true)

                    val result = listOf(pdfPasswordUseCase(
                        uri, 
                        password = password, 
                        action = action, 
                        allowPrinting = allowPrinting,
                        allowCopying = allowCopying,
                        allowEditing = allowEditing,
                        outputFileName = outputFileName
                    ))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 85, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "page_editor" -> {
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val pageOrderStr = requireNotNull(inputData.getString(KEY_PAGE_ORDER))
                    val orderList = pageOrderStr.split(",").map { it.trim().toInt() }
                    val rotationsStr = inputData.getString("page_rotations") ?: ""
                    val rotationsMap = rotationsStr.split(",")
                        .filter { it.isNotBlank() }
                        .associate { 
                            val parts = it.split(":")
                            parts[0].toInt() to parts[1].toInt()
                        }
                    val result = listOf(pdfPageEditorUseCase(uri, newOrder = orderList, rotations = rotationsMap, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 85, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "metadata_editor" -> {
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 30, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val action = inputData.getString(KEY_ACTION) ?: "scrub"
                    val resultUri = if (action == "edit") {
                        val author = inputData.getString(KEY_AUTHOR)
                        val title = inputData.getString(KEY_TITLE)
                        val subject = inputData.getString(KEY_SUBJECT)
                        val software = inputData.getString(KEY_SOFTWARE)
                        val copyright = inputData.getString(KEY_COPYRIGHT)
                        val dateCreated = inputData.getString(KEY_DATE_CREATED)
                        val lat = if (inputData.keyValueMap.containsKey(KEY_LATITUDE)) inputData.getDouble(KEY_LATITUDE, 0.0) else null
                        val lng = if (inputData.keyValueMap.containsKey(KEY_LONGITUDE)) inputData.getDouble(KEY_LONGITUDE, 0.0) else null
                        val make = inputData.getString(KEY_CAMERA_MAKE)
                        val model = inputData.getString(KEY_CAMERA_MODEL)
                        val editParams = com.morphdrop.app.domain.model.MetadataEditParams(
                            author = author,
                            title = title,
                            subject = subject,
                            software = software,
                            copyright = copyright,
                            dateCreated = dateCreated,
                            latitude = lat,
                            longitude = lng,
                            cameraMake = make,
                            cameraModel = model
                        )
                        metadataUseCase.editMetadataAndSave(appContext, uri, editParams, outputFileName)
                    } else {
                        metadataUseCase.scrubMetadataAndSave(appContext, uri, outputFileName)
                    }
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 80, cancelPendingIntent)
                    setProgress(workDataOf("progress" to 80))
                    listOf(resultUri)
                }

                else -> throw IllegalArgumentException("Unsupported conversion type: $conversionType")
            }

            notificationHelper.showProgressNotification(notificationId, mapIdToDisplayName(conversionType), 100, cancelPendingIntent)
            setProgress(workDataOf("progress" to 100))

            val duration = System.currentTimeMillis() - startTime
            val outputNames = resultUris.joinToString(", ") { uri ->
                val name = FileHelper.getFileName(appContext, uri)
                if (name == "unknown" || name.isBlank()) uri.lastPathSegment ?: "converted_file" else name
            }
            val outUrisString = resultUris.joinToString(",") { it.toString() }
            
            val finalOutputName = if (resultUris.size == 1) {
                FileHelper.getFileName(appContext, resultUris.first())
            } else {
                outputFileName
            }

            historyRepository.insertHistory(
                ConversionHistoryEntity(
                    conversionType = mapIdToDisplayName(conversionType),
                    inputFileName = inputFileName,
                    outputFileNames = outputNames,
                    outputUris = outUrisString,
                    displayName = finalOutputName,
                    timestamp = System.currentTimeMillis(),
                    duration = duration,
                    success = true
                )
            )
            com.morphdrop.app.ui.widget.WidgetUpdateHelper.updateAllWidgets(appContext)

            val primaryOutputUri = resultUris.firstOrNull()
            notificationHelper.showCompletionNotification(
                notificationId,
                mapIdToDisplayName(conversionType),
                primaryOutputUri
            )

            Result.success(
                workDataOf(
                    KEY_OUTPUT_URI to primaryOutputUri.toString(),
                    KEY_OUTPUT_URIS to outUrisString
                )
            )
        } catch (e: Exception) {
            // Handle cancellation gracefully
            if (e is kotlinx.coroutines.CancellationException || isStopped) {
                if (isBatchImageConversion && convertedCount > 0) {
                    val cancelMessage = "Conversion cancelled. $convertedCount of $totalImageCount images converted."
                    notificationHelper.showCancelledNotification(
                        notificationId,
                        mapIdToDisplayName(conversionType),
                        cancelMessage
                    )
                    return@withContext Result.failure(
                        workDataOf(
                            "is_cancelled" to true,
                            "cancel_message" to cancelMessage,
                            "converted_count" to convertedCount,
                            "total_count" to totalImageCount
                        )
                    )
                }

                // If single file cancelled, clean up generated incomplete files
                generatedFileNames.forEach { name ->
                    FileHelper.deleteFileByName(appContext, "MorphDrop", name)
                }
                
                notificationHelper.showCancelledNotification(
                    notificationId,
                    mapIdToDisplayName(conversionType),
                    "Conversion cancelled."
                )
                return@withContext Result.failure(
                    workDataOf(
                        "is_cancelled" to true,
                        "cancel_message" to "Conversion cancelled."
                    )
                )
            }

            val duration = System.currentTimeMillis() - startTime
            historyRepository.insertHistory(
                ConversionHistoryEntity(
                    conversionType = mapIdToDisplayName(conversionType),
                    inputFileName = inputFileName,
                    outputFileNames = "",
                    outputUris = "",
                    displayName = "Failed Conversion",
                    timestamp = System.currentTimeMillis(),
                    duration = duration,
                    success = false
                )
            )

            notificationHelper.showErrorNotification(
                notificationId,
                mapIdToDisplayName(conversionType),
                e.localizedMessage ?: "Conversion failed"
            )

            Result.failure(workDataOf(KEY_ERROR to (e.localizedMessage ?: "Unknown error")))
        }
    }

    private fun mapIdToDisplayName(id: String): String {
        return when (id) {
            "metadata_editor" -> "Metadata Inspector & Editor"
            "page_editor" -> "Organize PDF"
            "split_pdf" -> "Split PDF"
            "protect_pdf" -> "Protect PDF"
            "merge_pdf", "merge_pdfs" -> "Merge PDFs"
            "compress_pdf" -> "Compress PDF"
            "pdf_to_images" -> "PDF to Images"
            "images_to_pdf", "image_to_pdf" -> "Images to PDF"
            "excel_to_pdf" -> "Excel to PDF"
            "text_to_pdf", "txt_to_pdf" -> "Text to PDF"
            "md_to_pdf", "markdown_to_pdf" -> "Markdown to PDF"
            "image_converter" -> "Image Converter"
            "compress_images" -> "Compress Images"
            else -> id.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    private fun parsePageRange(rangeStr: String?): IntRange? {
        if (rangeStr.isNullOrBlank()) return null
        val parts = rangeStr.split("-").mapNotNull { it.trim().toIntOrNull() }
        return when {
            parts.size >= 2 -> parts[0]..parts[1]
            parts.size == 1 -> parts[0]..parts[0]
            else -> null
        }
    }

    private fun parseRotations(rotationsStr: String?): Map<Int, Int> {
        if (rotationsStr.isNullOrBlank()) return emptyMap()
        return try {
            rotationsStr.split(",")
                .filter { it.isNotBlank() }
                .associate { 
                    val parts = it.split(":")
                    parts[0].toInt() to parts[1].toInt()
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}