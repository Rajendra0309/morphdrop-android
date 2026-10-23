package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.morphdrop.app.data.pdf.TablePdfWriter
import com.morphdrop.app.domain.model.OcrScript
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Makes a scanned (image-only) PDF searchable: every page is rendered,
 * OCR'd with ML Kit, and rebuilt as a page image with an invisible text
 * layer placed word-by-word at the recognized positions. The result
 * looks identical to the scan but supports search, selection and
 * copy/paste, and PDF to Text can read it.
 *
 * Pages are re-rendered at 200 DPI, so the output is a faithful copy of
 * the pages as rendered, not the original embedded images.
 */
class SearchablePdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ocrUseCase: OcrUseCase
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        outputFileName: String = "searchable_${System.currentTimeMillis()}.pdf",
        script: OcrScript = OcrScript.LATIN,
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        onProgress(5)
        val (pfd, tempFile) = openPdfDescriptor(pdfUri)
        try {
            val renderer = PdfRenderer(pfd)
            try {
                val pageCount = renderer.pageCount
                if (pageCount <= 0) {
                    throw IllegalArgumentException("This PDF has no pages to process.")
                }
                val outputBytes = buildSearchablePdf(renderer, pageCount, script, onProgress)
                val uri = FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, outputBytes)
                onProgress(100)
                uri
            } finally {
                renderer.close()
            }
        } finally {
            pfd.close()
            tempFile?.delete()
        }
    }

    private suspend fun buildSearchablePdf(
        renderer: PdfRenderer,
        pageCount: Int,
        script: OcrScript,
        onProgress: (Int) -> Unit
    ): ByteArray {
        val font = PDType1Font.HELVETICA
        val document = PDDocument()
        try {
            for (pageIndex in 0 until pageCount) {
                coroutineContext.ensureActive()
                val page = renderer.openPage(pageIndex)
                val pageWidthPt = page.width.toFloat()
                val pageHeightPt = page.height.toFloat()
                var bitmap: Bitmap? = null
                try {
                    // 200 DPI render: sharp enough for reading and OCR,
                    // without the file ballooning the way 300 DPI JPEGs do.
                    val scale = min(200f / 72f, 3000f / maxOf(page.width, page.height).coerceAtLeast(1))
                    val bmpWidth = (page.width * scale).toInt().coerceAtLeast(1)
                    val bmpHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val layout = ocrUseCase.recognizeLayout(bitmap, script).getOrElse { error ->
                        throw IllegalStateException(error.message ?: "Text recognition failed", error)
                    }

                    val outPage = PDPage(PDRectangle(pageWidthPt, pageHeightPt))
                    document.addPage(outPage)
                    PDPageContentStream(document, outPage).use { stream ->
                        // Page image.
                        val jpeg = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, jpeg)
                        val image = JPEGFactory.createFromStream(document, ByteArrayInputStream(jpeg.toByteArray()))
                        stream.drawImage(image, 0f, 0f, pageWidthPt, pageHeightPt)

                        // Invisible text layer, one entry per recognized word.
                        val scaleX = pageWidthPt / bmpWidth
                        val scaleY = pageHeightPt / bmpHeight
                        stream.setRenderingMode(RenderingMode.NEITHER)
                        for (block in layout.textBlocks) {
                            for (line in block.lines) {
                                for (element in line.elements) {
                                    val box = element.boundingBox ?: continue
                                    val word = TablePdfWriter.sanitize(element.text).trim()
                                    if (word.isEmpty()) continue
                                    val fontSize = (box.height() * scaleY * 0.92f).coerceIn(4f, 72f)
                                    val naturalWidth = font.getStringWidth(word) / 1000f * fontSize
                                    val targetWidth = box.width() * scaleX
                                    stream.beginText()
                                    stream.setFont(font, fontSize)
                                    if (naturalWidth > 1f) {
                                        stream.setHorizontalScaling(targetWidth / naturalWidth * 100f)
                                    }
                                    // Bitmap boxes are top-left origin; PDF text
                                    // is positioned by its baseline from the bottom.
                                    val baseline = pageHeightPt - box.bottom * scaleY + fontSize * 0.22f
                                    stream.newLineAtOffset(box.left * scaleX, baseline)
                                    stream.showText(word)
                                    stream.endText()
                                }
                            }
                        }
                    }
                } finally {
                    bitmap?.recycle()
                    page.close()
                }
                onProgress(10 + ((pageIndex + 1) * 85 / pageCount))
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        } finally {
            document.close()
        }
    }

    /**
     * Opens a descriptor PdfRenderer can read: a plain file or a content
     * descriptor when the provider offers one, else a cache copy (returned
     * alongside so the caller can delete it).
     */
    private fun openPdfDescriptor(uri: Uri): Pair<ParcelFileDescriptor, File?> {
        if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (file.exists()) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) to null
            }
        }
        val direct = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (direct != null) {
            return direct to null
        }
        val temp = File.createTempFile("searchable_", ".pdf", context.cacheDir)
        try {
            FileHelper.readFileFromUri(context, uri).use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            runCatching { temp.delete() }
            throw e
        }
        return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY) to temp
    }
}
