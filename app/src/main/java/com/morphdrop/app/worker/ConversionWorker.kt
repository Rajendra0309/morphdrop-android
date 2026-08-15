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
import com.morphdrop.app.domain.usecase.conversion.MergePdfUseCase
import com.morphdrop.app.domain.usecase.conversion.PdfPageEditorUseCase
import com.morphdrop.app.domain.usecase.conversion.PdfPasswordUseCase
import com.morphdrop.app.domain.usecase.conversion.PdfToImagesUseCase
import com.morphdrop.app.domain.usecase.conversion.ReorderPdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.RotatePdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.SplitPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.TextToPdfUseCase
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val reorderPdfPagesUseCase: ReorderPdfPagesUseCase,
    private val pdfPasswordUseCase: PdfPasswordUseCase,
    private val pdfPageEditorUseCase: PdfPageEditorUseCase,
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
        const val KEY_PADDING_COLOR = "padding_color"
        const val KEY_TARGET_SIZE_KB = "target_size_kb"
        const val KEY_CROP_RECT_LEFT = "crop_rect_left"
        const val KEY_CROP_RECT_TOP = "crop_rect_top"
        const val KEY_CROP_RECT_RIGHT = "crop_rect_right"
        const val KEY_CROP_RECT_BOTTOM = "crop_rect_bottom"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversionType = inputData.getString(KEY_CONVERSION_TYPE) ?: "File Conversion"
        val startTime = System.currentTimeMillis()
        val notificationId = id.hashCode()

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
            val ext = when (conversionType) {
                "pdf_to_images", "split_pdf" -> "" // Folder
                "images_to_pdf", "excel_to_pdf", "txt_to_pdf", "md_to_pdf", "compress_pdf" -> "pdf"
                "image_converter", "compress_images" -> inputData.getString(KEY_TARGET_FORMAT) ?: "jpg"
                else -> "pdf"
            }
            if (ext.isNotEmpty()) "$outputFileNameInput.$ext" else outputFileNameInput
        } else outputFileNameInput

        val generatedFileNames = mutableListOf<String>()
        generatedFileNames.add(outputFileName)

        try {

            try {
                val foregroundInfo = notificationHelper.createForegroundInfo(
                    notificationId,
                    conversionType,
                    0
                )
                setForeground(foregroundInfo)
            } catch (_: Throwable) {
                // Foreground service may be constrained by OS policy or missing permission
            }
            notificationHelper.showProgressNotification(notificationId, conversionType, 10)
            setProgress(workDataOf(
                "progress" to 15,
                "output_name" to outputFileName
            ))

            val resultUris: List<Uri> = when (conversionType) {
                "pdf_to_images" -> {
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
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
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "images_to_pdf", "image_to_pdf" -> {
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uris = inputUrisArray?.map { Uri.parse(it) }
                        ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                    val result = listOf(imagesToPdfUseCase(uris, outputFileName = outputFileName))
                    checkCancellation()
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
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
                            notificationHelper.showProgressNotification(notificationId, conversionType, mappedProgress)
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
                            notificationHelper.showProgressNotification(notificationId, conversionType, mappedProgress)
                            kotlinx.coroutines.runBlocking { setProgress(workDataOf("progress" to mappedProgress)) }
                        }
                    ))
                    result
                }

                "md_to_pdf", "markdown_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(mdToPdfUseCase(uri, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "image_converter" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val targetFormat = inputData.getString(KEY_TARGET_FORMAT) ?: "jpg"
                    val quality = inputData.getInt(KEY_QUALITY, 90)
                    
                    val targetWidth = if (inputData.getInt(KEY_TARGET_WIDTH, -1) != -1) inputData.getInt(KEY_TARGET_WIDTH, -1) else null
                    val targetHeight = if (inputData.getInt(KEY_TARGET_HEIGHT, -1) != -1) inputData.getInt(KEY_TARGET_HEIGHT, -1) else null
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

                    val result = listOf(imageConverterUseCase(
                        inputUri = uri, 
                        outputFormat = targetFormat, 
                        quality = quality, 
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        paddingColor = paddingColor,
                        cropRect = cropRect,
                        rotationDegrees = rotationDegrees,
                        targetSizeKb = targetSizeKb,
                        outputFileName = outputFileName
                    ))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "compress_images" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 20)
                    setProgress(workDataOf("progress" to 20))
                    val uris = inputUrisArray?.map { Uri.parse(it) }
                        ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                    val targetFormat = inputData.getString(KEY_TARGET_FORMAT) ?: "jpg"
                    val quality = inputData.getInt(KEY_QUALITY, 60)
                    
                    val targetWidth = if (inputData.getInt(KEY_TARGET_WIDTH, -1) != -1) inputData.getInt(KEY_TARGET_WIDTH, -1) else null
                    val targetHeight = if (inputData.getInt(KEY_TARGET_HEIGHT, -1) != -1) inputData.getInt(KEY_TARGET_HEIGHT, -1) else null
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

                    val result = uris.mapIndexed { idx, u ->
                        val genName = "compressed_${idx}_${System.currentTimeMillis()}.$targetFormat"
                        generatedFileNames.add(genName)
                        val res = imageConverterUseCase(
                            inputUri = u,
                            outputFormat = targetFormat,
                            quality = quality,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            paddingColor = paddingColor,
                            targetSizeKb = targetSizeKb,
                            cropRect = cropRect,
                            rotationDegrees = rotationDegrees,
                            outputFileName = genName
                        )
                        val p = 20 + ((idx + 1).toFloat() / uris.size * 60).toInt()
                        notificationHelper.showProgressNotification(notificationId, conversionType, p)
                        setProgress(workDataOf("progress" to p))
                        res
                    }
                    result
                }

                "merge_pdf", "merge_pdfs" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uris = inputUrisArray?.map { Uri.parse(it) }
                        ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME) ?: "merged_${System.currentTimeMillis()}.pdf"
                    generatedFileNames.add(outName)
                    val result = listOf(mergePdfUseCase(uris, outputFileName = outName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "split_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
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
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "compress_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
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
                        outputFileName = outputFileName
                    )
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    listOf(compressResult.outputUri)
                }

                "rotate_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val degrees = inputData.getInt(KEY_ROTATION_DEGREES, 90)
                    val result = listOf(rotatePdfPagesUseCase(uri, rotationDegrees = degrees, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "reorder_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val pageOrderStr = requireNotNull(inputData.getString(KEY_PAGE_ORDER))
                    val orderList = pageOrderStr.split(",").map { it.trim().toInt() }
                    val result = listOf(reorderPdfPagesUseCase(uri, newOrder = orderList, outputFileName = outputFileName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "protect_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val password = requireNotNull(inputData.getString(KEY_PASSWORD))
                    val actionStr = inputData.getString(KEY_ACTION) ?: "ADD_PASSWORD"
                    val action = if (actionStr == "REMOVE_PASSWORD") {
                        PdfPasswordUseCase.Action.REMOVE_PASSWORD
                    } else {
                        PdfPasswordUseCase.Action.ADD_PASSWORD
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
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "page_editor" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
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
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                else -> throw IllegalArgumentException("Unsupported conversion type: $conversionType")
            }

            notificationHelper.showProgressNotification(notificationId, conversionType, 100)
            setProgress(workDataOf("progress" to 100))

            val duration = System.currentTimeMillis() - startTime
            val outputNames = resultUris.joinToString(", ") { uri ->
                val name = FileHelper.getFileName(appContext, uri as Uri)
                if (name == "unknown" || name.isBlank()) uri.lastPathSegment ?: "converted_file" else name
            }
            val outUrisString = resultUris.joinToString(",") { it.toString() }
            
            // Use the actual output filename if possible
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

            val primaryOutputUri = resultUris.firstOrNull()
            notificationHelper.showCompletionNotification(
                notificationId,
                conversionType,
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
                // If the user cancelled, do not log to history and do not show completion notifications
                // Clean up any incomplete files/folders generated during this execution
                generatedFileNames.forEach { name ->
                    FileHelper.deleteFileByName(appContext, "MorphDrop", name)
                }
                return@withContext Result.failure()
            }

            val duration = System.currentTimeMillis() - startTime
            historyRepository.insertHistory(
                ConversionHistoryEntity(
                    conversionType = conversionType,
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
                conversionType,
                e.localizedMessage ?: "Conversion failed"
            )

            Result.failure(workDataOf(KEY_ERROR to (e.localizedMessage ?: "Unknown error")))
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

    private fun mapIdToDisplayName(id: String): String {
        return when (id) {
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

    private fun groupIndicesIntoRanges(indices: List<Int>): List<IntRange> {
        return emptyList()
    }
}
