package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import javax.inject.Inject

class TextToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val PAGE_WIDTH = 595 // A4 Width in points
        private const val PAGE_HEIGHT = 842 // A4 Height in points
        private const val MARGIN_LEFT = 45f
        private const val MARGIN_RIGHT = 45f
        private const val MARGIN_TOP = 45f
        private const val MARGIN_BOTTOM = 45f
    }

    suspend operator fun invoke(
        txtUri: Uri,
        outputFileName: String = "text_to_pdf_${System.currentTimeMillis()}.pdf",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        onProgress(10)
        // Stream via BufferedReader (no readBytes()): BOM-aware encoding detection.
        val lines = readTextLines(txtUri)
        onProgress(30)

        val pdfDocument = PdfDocument()

        try {
            val usableWidth = (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT).toInt()
            val maxBottom = PAGE_HEIGHT - MARGIN_BOTTOM

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas
            var yPos = MARGIN_TOP

            // FIXED 9.5pt size used directly on the PDF canvas: sp/scaledDensity
            // belongs to screens, not to a 72-dpi PDF page, where it blew text up
            // on high-density devices.
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1F2328")
                textSize = 9.5f
                typeface = Typeface.DEFAULT
            }

            fun newPage() {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                yPos = MARGIN_TOP
            }

            /**
             * Draws [layout], splitting it across pages by text lines when a single
             * input line wraps taller than one page (instead of clipping it).
             */
            fun drawLayoutMaybeSplit(layout: StaticLayout, spacingAfter: Float) {
                val totalLines = layout.lineCount
                if (totalLines == 0) {
                    yPos += spacingAfter
                    return
                }
                var firstLine = 0
                while (firstLine < totalLines) {
                    val available = maxBottom - yPos
                    var lastLine = firstLine
                    while (lastLine < totalLines &&
                        (layout.getLineBottom(lastLine) - layout.getLineTop(firstLine)).toFloat() <= available
                    ) {
                        lastLine++
                    }
                    if (lastLine == firstLine) {
                        if (yPos > MARGIN_TOP) {
                            newPage()
                            continue
                        }
                        lastLine = firstLine + 1
                    }
                    val chunkTop = layout.getLineTop(firstLine).toFloat()
                    val chunkHeight = layout.getLineBottom(lastLine - 1).toFloat() - chunkTop
                    canvas.save()
                    canvas.clipRect(
                        MARGIN_LEFT,
                        yPos,
                        MARGIN_LEFT + layout.width,
                        yPos + chunkHeight
                    )
                    canvas.translate(MARGIN_LEFT, yPos - chunkTop)
                    layout.draw(canvas)
                    canvas.restore()
                    yPos += chunkHeight
                    firstLine = lastLine
                    if (firstLine < totalLines) newPage()
                }
                yPos += spacingAfter
            }

            for ((index, line) in lines.withIndex()) {
                kotlinx.coroutines.yield() // Support cancellation
                if (line.isEmpty()) {
                    yPos += 9.5f * 1.5f
                    if (yPos > maxBottom) {
                        newPage()
                    }
                    continue
                }

                val layout = createStaticLayout(line, textPaint, usableWidth)
                drawLayoutMaybeSplit(layout, spacingAfter = 4f)

                val currentProgress = 30 + ((index + 1).toFloat() / lines.size.coerceAtLeast(1) * 60).toInt()
                onProgress(currentProgress)
            }

            pdfDocument.finishPage(currentPage)

            if (pdfDocument.pages.isEmpty()) {
                val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
                pdfDocument.finishPage(page)
            }

            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            onProgress(95)
            val savedUri = FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, baos.toByteArray())
            onProgress(100)
            savedUri
        } finally {
            try { pdfDocument.close() } catch (_: Exception) {}
        }
    }

    /**
     * Streams the text file through a BufferedReader (no readBytes()): detects a
     * BOM to pick the encoding (UTF-8 / UTF-16LE / UTF-16BE) and strips it so it
     * never shows up as garbage in the first line.
     */
    private fun readTextLines(uri: Uri): List<String> {
        FileHelper.readFileFromUri(context, uri).buffered().use { buffered ->
            buffered.mark(4)
            val bom = ByteArray(4)
            var read = 0
            while (read < 4) {
                val n = buffered.read(bom, read, 4 - read)
                if (n == -1) break
                read += n
            }
            buffered.reset()
            val charset: Charset
            val bomLength: Int
            when {
                read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> {
                    charset = Charsets.UTF_8; bomLength = 3
                }
                read >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> {
                    charset = Charsets.UTF_16LE; bomLength = 2
                }
                read >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> {
                    charset = Charsets.UTF_16BE; bomLength = 2
                }
                else -> {
                    charset = Charsets.UTF_8; bomLength = 0
                }
            }
            repeat(bomLength) { buffered.read() }
            return BufferedReader(InputStreamReader(buffered, charset)).readLines()
        }
    }

    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int
    ): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
        }
    }
}
