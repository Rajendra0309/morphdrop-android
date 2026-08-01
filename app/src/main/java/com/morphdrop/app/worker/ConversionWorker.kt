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
import com.morphdrop.app.domain.usecase.conversion.PdfToWordUseCase
import com.morphdrop.app.domain.usecase.conversion.PowerPointToPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.ReorderPdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.RotatePdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.SplitPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.TextToPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.WordToPdfUseCase
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
    private val wordToPdfUseCase: WordToPdfUseCase,
    private val pdfToWordUseCase: PdfToWordUseCase,
    private val excelToPdfUseCase: ExcelToPdfUseCase,
    private val powerPointToPdfUseCase: PowerPointToPdfUseCase,
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

        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_URIS = "output_uris"
        const val KEY_ERROR = "error"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversionType = inputData.getString(KEY_CONVERSION_TYPE) ?: "File Conversion"
        val startTime = System.currentTimeMillis()
        val notificationId = id.hashCode()

        val inputUriString = inputData.getString(KEY_INPUT_URI)
        val inputUrisArray = inputData.getStringArray(KEY_INPUT_URIS)
        val inputFileName = inputUriString?.let {
            FileHelper.getFileName(appContext, Uri.parse(it))
        } ?: if (!inputUrisArray.isNullOrEmpty()) {
            "${inputUrisArray.size} Files"
        } else {
            "Input File"
        }

        try {
            val outputFileName = inputData.getString(KEY_OUTPUT_FILE_NAME) ?: "Converted_File"
            
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
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val quality = inputData.getInt(KEY_QUALITY, 80)
                    val rangeStr = inputData.getString(KEY_PAGE_RANGE)
                    val pageRange = parsePageRange(rangeStr)
                    val formatStr = inputData.getString(KEY_TARGET_FORMAT) ?: "png"
                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME)
                    val result = pdfToImagesUseCase(
                        pdfUri = uri, 
                        outputFormat = formatStr, 
                        quality = quality, 
                        pageRange = pageRange,
                        outputFolderName = outName
                    )
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "images_to_pdf", "image_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uris = inputUrisArray?.map { Uri.parse(it) }
                        ?: listOf(Uri.parse(requireNotNull(inputUriString)))
                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME) ?: "converted_${System.currentTimeMillis()}.pdf"
                    val result = listOf(imagesToPdfUseCase(uris, outputFileName = outName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "word_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 40)
                    setProgress(workDataOf("progress" to 40))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(wordToPdfUseCase(uri))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "pdf_to_word" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 40)
                    setProgress(workDataOf("progress" to 40))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(pdfToWordUseCase(uri))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "excel_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 40)
                    setProgress(workDataOf("progress" to 40))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(excelToPdfUseCase(uri))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "ppt_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 40)
                    setProgress(workDataOf("progress" to 40))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val result = listOf(powerPointToPdfUseCase(uri))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "txt_to_pdf", "text_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME) ?: "text_to_pdf_${System.currentTimeMillis()}.pdf"
                    val result = listOf(textToPdfUseCase(uri, outputFileName = outName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 80)
                    setProgress(workDataOf("progress" to 80))
                    result
                }

                "md_to_pdf", "markdown_to_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME) ?: "md_to_pdf_${System.currentTimeMillis()}.pdf"
                    val result = listOf(mdToPdfUseCase(uri, outputFileName = outName))
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
                    val result = listOf(imageConverterUseCase(inputUri = uri, outputFormat = targetFormat, quality = quality))
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
                    val result = uris.mapIndexed { idx, u ->
                        val res = imageConverterUseCase(
                            inputUri = u,
                            outputFormat = targetFormat,
                            quality = quality,
                            outputFileName = "compressed_${idx}_${System.currentTimeMillis()}.$targetFormat"
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
                    val result = listOf(mergePdfUseCase(uris, outputFileName = outName))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "split_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val rangeStr = inputData.getString(KEY_PAGE_RANGE)
                    val ranges = parsePageRanges(rangeStr)
                    val outName = inputData.getString(KEY_OUTPUT_FILE_NAME)
                    val result = splitPdfUseCase(pdfUri = uri, pageRanges = ranges, outputFolderName = outName)
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                "compress_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val quality = inputData.getInt(KEY_QUALITY, 50)
                    val level = when {
                        quality <= 40 -> CompressionLevel.HIGH
                        quality <= 70 -> CompressionLevel.MEDIUM
                        else -> CompressionLevel.LOW
                    }
                    val compressResult = compressPdfUseCase(pdfUri = uri, compressionLevel = level)
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    listOf(compressResult.outputUri)
                }

                "rotate_pdf" -> {
                    notificationHelper.showProgressNotification(notificationId, conversionType, 30)
                    setProgress(workDataOf("progress" to 30))
                    val uri = Uri.parse(requireNotNull(inputUriString))
                    val degrees = inputData.getInt(KEY_ROTATION_DEGREES, 90)
                    val result = listOf(rotatePdfPagesUseCase(uri, rotationDegrees = degrees))
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
                    val result = listOf(reorderPdfPagesUseCase(uri, newOrder = orderList))
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
                    val result = listOf(pdfPasswordUseCase(uri, password = password, action = action))
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
                    val result = listOf(pdfPageEditorUseCase(uri, newOrder = orderList, rotations = rotationsMap))
                    notificationHelper.showProgressNotification(notificationId, conversionType, 85)
                    setProgress(workDataOf("progress" to 85))
                    result
                }

                else -> throw IllegalArgumentException("Unsupported conversion type: $conversionType")
            }

            notificationHelper.showProgressNotification(notificationId, conversionType, 100)
            setProgress(workDataOf("progress" to 100))

            val duration = System.currentTimeMillis() - startTime
            val outputNames = resultUris.joinToString(",") { Uri.parse(it.toString()).lastPathSegment ?: "converted_file" }
            val outUrisString = resultUris.joinToString(",") { it.toString() }
            
            // Determine display name (folder name for multi-file, file name for single)
            val chosenDisplayName = inputData.getString(KEY_OUTPUT_FILE_NAME) 
                ?: outputNames.split(",").firstOrNull() 
                ?: "Converted File"

            historyRepository.insertHistory(
                ConversionHistoryEntity(
                    conversionType = conversionType,
                    inputFileName = inputFileName,
                    outputFileNames = outputNames,
                    outputUris = outUrisString,
                    displayName = chosenDisplayName,
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

    private fun parsePageRanges(rangesStr: String?): List<IntRange> {
        if (rangesStr.isNullOrBlank()) return listOf(1..1)
        return rangesStr.split(",").mapNotNull { parsePageRange(it.trim()) }
    }
}
