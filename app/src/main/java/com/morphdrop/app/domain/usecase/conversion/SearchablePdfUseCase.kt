package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.morphdrop.app.data.pdf.ScriptSpans
import com.morphdrop.app.data.pdf.TablePdfWriter
import com.morphdrop.app.domain.model.OcrScript
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
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
        val document = PDDocument()
        try {
            // Latin text uses the standard Helvetica (WinAnsi). Devanagari
            // additionally embeds the bundled Noto font, and each element
            // is split into script spans: Devanagari spans use the Noto
            // font, everything else Helvetica — the bundled font has no
            // Latin glyphs, so a mixed-script page ("MorphDrop" heading
            // over Hindi lines) must never be written with it alone.
            val latinFont: PDFont = PDType1Font.HELVETICA
            val devanagariFont: PDFont? = if (script == OcrScript.DEVANAGARI) {
                loadTextFont(document, script)
            } else {
                null
            }
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
                                var previousRight: Int? = null
                                for (element in line.elements) {
                                    val box = element.boundingBox ?: continue
                                    val word = element.text.trim()
                                    if (word.isEmpty()) continue
                                    // Words the OCR split into elements that
                                    // sit a word-space apart need an explicit
                                    // space in the layer, or extraction joins
                                    // them into one long word.
                                    val leadingSpace = previousRight?.let { right ->
                                        ScriptSpans.isWordGap(right, box.left, box.height())
                                    } ?: false
                                    previousRight = box.right
                                    val fontSize = (box.height() * scaleY * 0.92f).coerceIn(4f, 72f)
                                    // Bitmap boxes are top-left origin; PDF text
                                    // is positioned by its baseline from the bottom.
                                    val baseline = pageHeightPt - box.bottom * scaleY + fontSize * 0.22f
                                    writeElement(
                                        stream = stream,
                                        word = word,
                                        leadingSpace = leadingSpace,
                                        latinFont = latinFont,
                                        devanagariFont = devanagariFont,
                                        fontSize = fontSize,
                                        startX = box.left * scaleX,
                                        baseline = baseline,
                                        targetWidth = box.width() * scaleX
                                    )
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
     * Writes one recognized word as one or more script spans sharing the
     * element's box: the spans are scaled together so together they fill
     * the box width, and each span advances the next one's x position.
     * Characters a span's font cannot draw are dropped rather than
     * throwing "No glyph" and failing the whole page.
     */
    private fun writeElement(
        stream: PDPageContentStream,
        word: String,
        leadingSpace: Boolean,
        latinFont: PDFont,
        devanagariFont: PDFont?,
        fontSize: Float,
        startX: Float,
        baseline: Float,
        targetWidth: Float
    ) {
        val spans = ArrayList<Pair<PDFont, String>>()
        if (leadingSpace) {
            spans += latinFont to " "
        }
        for (span in ScriptSpans.split(word)) {
            if (span.devanagari && devanagariFont != null) {
                spans += devanagariFont to span.text
            } else {
                spans += latinFont to TablePdfWriter.sanitize(span.text)
            }
        }
        val drawable = spans.mapNotNull { (font, text) ->
            val filtered = filterToGlyphs(font, text)
            if (filtered.isEmpty()) null else font to filtered
        }
        if (drawable.isEmpty()) return
        val naturalWidths = drawable.map { (font, text) ->
            runCatching { font.getStringWidth(text) / 1000f * fontSize }.getOrDefault(0f)
        }
        val totalNatural = naturalWidths.sum()
        val scaling = if (totalNatural > 1f) targetWidth / totalNatural * 100f else 100f
        var x = startX
        drawable.forEachIndexed { index, (font, text) ->
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.setHorizontalScaling(scaling)
            stream.newLineAtOffset(x, baseline)
            stream.showText(text)
            stream.endText()
            x += naturalWidths[index] * scaling / 100f
        }
    }

    /**
     * Keeps only the characters [font] can actually encode. PDFBox throws
     * "No glyph for U+XXXX" from inside showText when a character is
     * missing, so encodability is probed per character up front; this
     * port exposes hasGlyph only per font subtype, while encode() is the
     * common path that showText itself uses.
     */
    private fun filterToGlyphs(font: PDFont, text: String): String {
        val kept = StringBuilder()
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            val charText = String(Character.toChars(codePoint))
            if (runCatching { font.encode(charText) }.isSuccess) {
                kept.append(charText)
            }
            index += Character.charCount(codePoint)
        }
        return kept.toString()
    }

    /**
     * Latin gets Helvetica. Devanagari embeds the bundled Noto Sans
     * Devanagari (assets, SIL OFL) — deterministic on every device,
     * unlike /system/fonts, whose names and variable-font formats vary
     * by OEM. System fonts are a second chance; if nothing loads, fail
     * loudly instead of silently writing '?' for every character.
     */
    private fun loadTextFont(document: PDDocument, script: OcrScript): PDFont {
        if (script != OcrScript.DEVANAGARI) return PDType1Font.HELVETICA
        val bundled = runCatching {
            context.assets.open("fonts/NotoSansDevanagari-Regular.ttf").use { stream ->
                PDType0Font.load(document, stream)
            }
        }.getOrNull()
        if (bundled != null) return bundled
        val systemCandidates = listOf(
            "/system/fonts/NotoSansDevanagari-Regular.ttf",
            "/system/fonts/NotoSansDevanagari.ttf"
        )
        for (path in systemCandidates) {
            val file = File(path)
            if (file.exists()) {
                val ttf = runCatching { PDType0Font.load(document, file) }.getOrNull()
                if (ttf != null) return ttf
            }
        }
        throw IllegalStateException(
            "Devanagari text needs a Unicode font, and none could be loaded on this device. " +
                "The searchable PDF was not created."
        )
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
