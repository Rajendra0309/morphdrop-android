package com.morphdrop.app.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.BatchPdfItemResult
import com.morphdrop.app.domain.model.BatchPdfOperation
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.domain.model.PageNumberFormat
import com.morphdrop.app.domain.model.PageNumberPosition
import com.morphdrop.app.domain.model.RotateScope
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.WatermarkPosition
import com.morphdrop.app.domain.model.WatermarkType
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.conversion.AddPageNumbersUseCase
import com.morphdrop.app.domain.usecase.conversion.CompressPdfUseCase
import com.morphdrop.app.domain.usecase.conversion.CompressionLevel
import com.morphdrop.app.domain.usecase.conversion.MergePdfItem
import com.morphdrop.app.domain.usecase.conversion.MergePdfUseCase
import com.morphdrop.app.domain.usecase.conversion.PdfPasswordUseCase
import com.morphdrop.app.domain.usecase.conversion.RotatePdfPagesUseCase
import com.morphdrop.app.domain.usecase.conversion.WatermarkPdfUseCase
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.util.NotificationHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class BatchPdfWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val compressPdfUseCase: CompressPdfUseCase,
    private val watermarkPdfUseCase: WatermarkPdfUseCase,
    private val addPageNumbersUseCase: AddPageNumbersUseCase,
    private val pdfPasswordUseCase: PdfPasswordUseCase,
    private val rotatePdfPagesUseCase: RotatePdfPagesUseCase,
    private val mergePdfUseCase: MergePdfUseCase,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_OPERATION = "key_operation"
        const val KEY_INPUT_URIS = "key_input_uris"

        // Compress keys
        const val KEY_IS_TARGET_SIZE = "key_is_target_size"
        const val KEY_COMPRESS_QUALITY = "key_compress_quality"
        const val KEY_TARGET_SIZE_MB = "key_target_size_mb"

        // Watermark keys
        const val KEY_WATERMARK_TYPE = "key_watermark_type"
        const val KEY_WATERMARK_TEXT = "key_watermark_text"
        const val KEY_WATERMARK_FONT_SIZE = "key_watermark_font_size"
        const val KEY_WATERMARK_COLOR = "key_watermark_color"
        const val KEY_WATERMARK_IMAGE_URI = "key_watermark_image_uri"
        const val KEY_WATERMARK_SCALE = "key_watermark_scale"
        const val KEY_WATERMARK_OPACITY = "key_watermark_opacity"
        const val KEY_WATERMARK_ROTATION = "key_watermark_rotation"
        const val KEY_WATERMARK_POSITION = "key_watermark_position"
        const val KEY_WATERMARK_SKIP_FIRST = "key_watermark_skip_first"

        // Page Number keys
        const val KEY_PAGE_NUM_POSITION = "key_page_num_position"
        const val KEY_PAGE_NUM_FORMAT = "key_page_num_format"
        const val KEY_PAGE_NUM_TEMPLATE = "key_page_num_template"
        const val KEY_PAGE_NUM_FONT_SIZE = "key_page_num_font_size"
        const val KEY_PAGE_NUM_COLOR = "key_page_num_color"
        const val KEY_PAGE_NUM_START = "key_page_num_start"
        const val KEY_PAGE_NUM_SKIP_FIRST = "key_page_num_skip_first"
        const val KEY_PAGE_NUM_SKIP_LAST = "key_page_num_skip_last"
        const val KEY_PAGE_NUM_MARGIN = "key_page_num_margin"

        // Password keys
        const val KEY_PASSWORD = "key_password"
        const val KEY_ALLOW_PRINT = "key_allow_print"
        const val KEY_ALLOW_COPY = "key_allow_copy"
        const val KEY_ALLOW_EDIT = "key_allow_edit"

        // Rotate keys
        const val KEY_ROTATE_DEGREES = "key_rotate_degrees"
        const val KEY_ROTATE_SCOPE = "key_rotate_scope"

        // Merge keys
        const val KEY_MERGE_ADD_NUMBERS = "key_merge_add_numbers"

        // Progress & Output keys
        const val KEY_PROGRESS_CURRENT = "key_progress_current"
        const val KEY_PROGRESS_TOTAL = "key_progress_total"
        const val KEY_PROGRESS_FILE = "key_progress_file"
        const val KEY_PROGRESS_PERCENT = "key_progress_percent"
        const val KEY_RESULTS_JSON = "key_results_json"
        const val KEY_OUTPUT_FOLDER = "key_output_folder"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rawOp = inputData.getString(KEY_OPERATION) ?: BatchPdfOperation.COMPRESS.name
        val operation = try {
            BatchPdfOperation.valueOf(rawOp)
        } catch (_: Exception) {
            BatchPdfOperation.COMPRESS
        }

        val inputUriStrings = inputData.getStringArray(KEY_INPUT_URIS) ?: emptyArray()
        if (inputUriStrings.isEmpty()) {
            return@withContext Result.failure(workDataOf("error" to "No PDF files selected for batch processing."))
        }

        val notificationId = id.hashCode()
        val cancelPendingIntent = androidx.work.WorkManager.getInstance(appContext)
            .createCancelPendingIntent(id)

        try {
            val foregroundInfo = notificationHelper.createForegroundInfo(
                notificationId = notificationId,
                title = "Batch ${operation.displayName}",
                progress = 0,
                cancelIntent = cancelPendingIntent
            )
            setForeground(foregroundInfo)
        } catch (_: Throwable) {}

        val rootFolder = settingsRepository.outputFolderName.first()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val opPrefix = when (operation) {
            BatchPdfOperation.COMPRESS -> "Compress"
            BatchPdfOperation.WATERMARK -> "Watermark"
            BatchPdfOperation.PAGE_NUMBERS -> "PageNumbers"
            BatchPdfOperation.PASSWORD -> "Password"
            BatchPdfOperation.ROTATE -> "Rotate"
            BatchPdfOperation.MERGE -> "Merge"
        }
        val batchFolder = "$rootFolder/Batch_PDF_${opPrefix}_$timestamp"

        val itemResults = mutableListOf<BatchPdfItemResult>()
        var successCount = 0
        var failCount = 0

        try {
            if (operation == BatchPdfOperation.MERGE) {
                if (isStopped) throw CancellationException("Worker stopped by user")

                notificationHelper.showProgressNotification(
                    notificationId = notificationId,
                    title = "Merging ${inputUriStrings.size} PDFs",
                    progress = 20,
                    cancelIntent = cancelPendingIntent
                )
                setProgress(workDataOf(
                    KEY_PROGRESS_CURRENT to 1,
                    KEY_PROGRESS_TOTAL to 1,
                    KEY_PROGRESS_FILE to "Combining files...",
                    KEY_PROGRESS_PERCENT to 20
                ))

                val mergeItems = mutableListOf<MergePdfItem>()
                var totalOriginalSize = 0L

                for (uriStr in inputUriStrings) {
                    if (isStopped) throw CancellationException("Worker stopped by user")
                    val uri = Uri.parse(uriStr)
                    val size = FileHelper.getFileSize(appContext, uri).coerceAtLeast(0L)
                    totalOriginalSize += size

                    try {
                        val stream = FileHelper.readFileFromUri(appContext, uri)
                        val doc = PDDocument.load(stream)
                        val numPages = doc.numberOfPages
                        doc.close()
                        stream.close()

                        for (p in 0 until numPages) {
                            mergeItems.add(MergePdfItem(uri = uri, pageIndex = p))
                        }
                    } catch (e: Exception) {
                        itemResults.add(
                            BatchPdfItemResult(
                                uriString = uriStr,
                                fileName = FileHelper.getFileName(appContext, uri),
                                originalSize = size,
                                isSuccess = false,
                                errorMessage = e.message ?: "Failed to read PDF pages"
                            )
                        )
                        failCount++
                    }
                }

                if (mergeItems.isNotEmpty()) {
                    if (isStopped) throw CancellationException("Worker stopped by user")
                    notificationHelper.showProgressNotification(
                        notificationId = notificationId,
                        title = "Merging ${inputUriStrings.size} PDFs",
                        progress = 60,
                        cancelIntent = cancelPendingIntent
                    )
                    setProgress(workDataOf(
                        KEY_PROGRESS_CURRENT to 1,
                        KEY_PROGRESS_TOTAL to 1,
                        KEY_PROGRESS_FILE to "Building merged PDF...",
                        KEY_PROGRESS_PERCENT to 60
                    ))

                    val mergedFileName = "merged_$timestamp.pdf"
                    var outputUri = mergePdfUseCase(
                        items = mergeItems,
                        outputFileName = mergedFileName,
                        subFolder = batchFolder
                    )

                    // If user toggled page numbering for merged document
                    val addPageNumbers = inputData.getBoolean(KEY_MERGE_ADD_NUMBERS, false)
                    if (addPageNumbers) {
                        val numberedConfig = PageNumberConfig(
                            position = PageNumberPosition.BOTTOM_CENTER,
                            format = PageNumberFormat.PAGE_X_OF_Y
                        )
                        outputUri = addPageNumbersUseCase(
                            pdfUri = outputUri,
                            config = numberedConfig,
                            outputFileName = mergedFileName,
                            subFolder = batchFolder
                        )
                    }

                    val mergedSize = FileHelper.getFileSize(appContext, outputUri).coerceAtLeast(0L)
                    itemResults.add(
                        BatchPdfItemResult(
                            uriString = inputUriStrings.first(),
                            fileName = mergedFileName,
                            outputUriString = outputUri.toString(),
                            originalSize = totalOriginalSize,
                            newSize = mergedSize,
                            isSuccess = true
                        )
                    )
                    successCount++
                }
            } else {
                // Sequential processing for all single-file batch operations
                val total = inputUriStrings.size

                for (i in 0 until total) {
                    if (isStopped) throw CancellationException("Worker stopped by user")

                    val uriStr = inputUriStrings[i]
                    val uri = Uri.parse(uriStr)
                    val originalFileName = FileHelper.getFileName(appContext, uri)
                    val baseName = FileHelper.getFileNameWithoutExtension(originalFileName)
                    val originalSize = FileHelper.getFileSize(appContext, uri).coerceAtLeast(0L)

                    val percent = ((i * 100) / total).coerceIn(0, 99)
                    notificationHelper.showProgressNotification(
                        notificationId = notificationId,
                        title = "Batch ${operation.displayName} (${i + 1}/$total)",
                        progress = percent,
                        cancelIntent = cancelPendingIntent
                    )
                    setProgress(workDataOf(
                        KEY_PROGRESS_CURRENT to (i + 1),
                        KEY_PROGRESS_TOTAL to total,
                        KEY_PROGRESS_FILE to originalFileName,
                        KEY_PROGRESS_PERCENT to percent
                    ))

                    try {
                        val outputUri: Uri = when (operation) {
                            BatchPdfOperation.COMPRESS -> {
                                val isTargetSize = inputData.getBoolean(KEY_IS_TARGET_SIZE, false)
                                val quality = inputData.getFloat(KEY_COMPRESS_QUALITY, 0.5f)
                                val targetSizeMb = inputData.getFloat(KEY_TARGET_SIZE_MB, 5f)

                                val level = when {
                                    quality >= 0.7f -> CompressionLevel.LOW
                                    quality >= 0.4f -> CompressionLevel.MEDIUM
                                    else -> CompressionLevel.HIGH
                                }
                                val targetKb = if (isTargetSize) (targetSizeMb * 1024).toInt() else null

                                val res = compressPdfUseCase(
                                    pdfUri = uri,
                                    compressionLevel = level,
                                    targetSizeKb = targetKb,
                                    outputFileName = "${baseName}_compressed.pdf",
                                    subFolder = batchFolder
                                )
                                res.outputUri
                            }

                            BatchPdfOperation.WATERMARK -> {
                                val typeStr = inputData.getString(KEY_WATERMARK_TYPE) ?: WatermarkType.TEXT.name
                                val posStr = inputData.getString(KEY_WATERMARK_POSITION) ?: WatermarkPosition.DIAGONAL.name

                                val config = WatermarkConfig(
                                    type = try { WatermarkType.valueOf(typeStr) } catch (_: Exception) { WatermarkType.TEXT },
                                    text = inputData.getString(KEY_WATERMARK_TEXT) ?: "CONFIDENTIAL",
                                    fontSizeSp = inputData.getFloat(KEY_WATERMARK_FONT_SIZE, 36f),
                                    fontColor = inputData.getLong(KEY_WATERMARK_COLOR, 0xFF888888),
                                    imageUri = inputData.getString(KEY_WATERMARK_IMAGE_URI),
                                    imageScale = inputData.getFloat(KEY_WATERMARK_SCALE, 0.5f),
                                    opacity = inputData.getFloat(KEY_WATERMARK_OPACITY, 0.35f),
                                    rotationDegrees = inputData.getFloat(KEY_WATERMARK_ROTATION, 45f),
                                    position = try { WatermarkPosition.valueOf(posStr) } catch (_: Exception) { WatermarkPosition.DIAGONAL },
                                    skipFirstPage = inputData.getBoolean(KEY_WATERMARK_SKIP_FIRST, false)
                                )

                                watermarkPdfUseCase(
                                    pdfUri = uri,
                                    config = config,
                                    outputFileName = "${baseName}_watermarked.pdf",
                                    subFolder = batchFolder
                                )
                            }

                            BatchPdfOperation.PAGE_NUMBERS -> {
                                val posStr = inputData.getString(KEY_PAGE_NUM_POSITION) ?: PageNumberPosition.BOTTOM_CENTER.name
                                val fmtStr = inputData.getString(KEY_PAGE_NUM_FORMAT) ?: PageNumberFormat.PAGE_X_OF_Y.name

                                val config = PageNumberConfig(
                                    position = try { PageNumberPosition.valueOf(posStr) } catch (_: Exception) { PageNumberPosition.BOTTOM_CENTER },
                                    format = try { PageNumberFormat.valueOf(fmtStr) } catch (_: Exception) { PageNumberFormat.PAGE_X_OF_Y },
                                    customTemplate = inputData.getString(KEY_PAGE_NUM_TEMPLATE) ?: "- {page} -",
                                    fontSizeSp = inputData.getFloat(KEY_PAGE_NUM_FONT_SIZE, 12f),
                                    fontColor = inputData.getLong(KEY_PAGE_NUM_COLOR, 0xFF000000),
                                    startNumber = inputData.getInt(KEY_PAGE_NUM_START, 1),
                                    skipFirstPage = inputData.getBoolean(KEY_PAGE_NUM_SKIP_FIRST, false),
                                    skipLastPage = inputData.getBoolean(KEY_PAGE_NUM_SKIP_LAST, false),
                                    marginDp = inputData.getFloat(KEY_PAGE_NUM_MARGIN, 24f)
                                )

                                addPageNumbersUseCase(
                                    pdfUri = uri,
                                    config = config,
                                    outputFileName = "${baseName}_numbered.pdf",
                                    subFolder = batchFolder
                                )
                            }

                            BatchPdfOperation.PASSWORD -> {
                                val password = inputData.getString(KEY_PASSWORD) ?: ""
                                val allowPrint = inputData.getBoolean(KEY_ALLOW_PRINT, true)
                                val allowCopy = inputData.getBoolean(KEY_ALLOW_COPY, true)
                                val allowEdit = inputData.getBoolean(KEY_ALLOW_EDIT, true)

                                pdfPasswordUseCase(
                                    pdfUri = uri,
                                    action = PdfPasswordUseCase.Action.ADD_PASSWORD,
                                    password = password,
                                    allowPrinting = allowPrint,
                                    allowCopying = allowCopy,
                                    allowEditing = allowEdit,
                                    outputFileName = "${baseName}_protected.pdf",
                                    subFolder = batchFolder
                                )
                            }

                            BatchPdfOperation.ROTATE -> {
                                val degrees = inputData.getInt(KEY_ROTATE_DEGREES, 90)
                                val scopeStr = inputData.getString(KEY_ROTATE_SCOPE) ?: RotateScope.ALL_PAGES.name
                                val scope = try { RotateScope.valueOf(scopeStr) } catch (_: Exception) { RotateScope.ALL_PAGES }

                                val targetPages = if (scope != RotateScope.ALL_PAGES) {
                                    try {
                                        val stream = FileHelper.readFileFromUri(appContext, uri)
                                        val doc = PDDocument.load(stream)
                                        val count = doc.numberOfPages
                                        doc.close()
                                        stream.close()
                                        (1..count).filter { p ->
                                            if (scope == RotateScope.EVEN_PAGES) p % 2 == 0 else p % 2 != 0
                                        }
                                    } catch (_: Exception) { null }
                                } else null

                                rotatePdfPagesUseCase(
                                    pdfUri = uri,
                                    rotationDegrees = degrees,
                                    targetPages = targetPages,
                                    outputFileName = "${baseName}_rotated.pdf",
                                    subFolder = batchFolder
                                )
                            }

                            BatchPdfOperation.MERGE -> uri
                        }

                        val newSize = FileHelper.getFileSize(appContext, outputUri).coerceAtLeast(0L)
                        itemResults.add(
                            BatchPdfItemResult(
                                uriString = uriStr,
                                fileName = originalFileName,
                                outputUriString = outputUri.toString(),
                                originalSize = originalSize,
                                newSize = newSize,
                                isSuccess = true
                            )
                        )
                        successCount++
                    } catch (e: Exception) {
                        itemResults.add(
                            BatchPdfItemResult(
                                uriString = uriStr,
                                fileName = originalFileName,
                                originalSize = originalSize,
                                isSuccess = false,
                                errorMessage = e.message ?: "Failed to process PDF"
                            )
                        )
                        failCount++
                    }
                }
            }

            // Save entry to History table
            val outUris = itemResults.filter { it.isSuccess }.mapNotNull { it.outputUriString }
            if (outUris.isNotEmpty()) {
                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "Batch ${operation.displayName}",
                            inputFileName = "${inputUriStrings.size} PDFs",
                            outputFileNames = "Batch_PDF_${opPrefix}_$timestamp",
                            outputUris = outUris.joinToString(","),
                            displayName = "Batch ${operation.displayName} ($successCount files)",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}
            }

            notificationHelper.cancelNotification(notificationId)

            val summaryTitle = "Batch ${operation.displayName} Complete"
            notificationHelper.showCompletionNotification(
                notificationId = notificationId,
                title = summaryTitle,
                outputUri = outUris.firstOrNull()?.let { Uri.parse(it) }
            )

            val jsonResults = Gson().toJson(itemResults)
            setProgress(workDataOf(
                KEY_PROGRESS_CURRENT to inputUriStrings.size,
                KEY_PROGRESS_TOTAL to inputUriStrings.size,
                KEY_PROGRESS_FILE to "Done",
                KEY_PROGRESS_PERCENT to 100
            ))

            Result.success(workDataOf(
                KEY_RESULTS_JSON to jsonResults,
                KEY_OUTPUT_FOLDER to batchFolder
            ))
        } catch (e: CancellationException) {
            notificationHelper.cancelNotification(notificationId)
            throw e
        } catch (e: Exception) {
            notificationHelper.cancelNotification(notificationId)
            Result.failure(workDataOf("error" to (e.message ?: "Batch operation failed")))
        }
    }
}
