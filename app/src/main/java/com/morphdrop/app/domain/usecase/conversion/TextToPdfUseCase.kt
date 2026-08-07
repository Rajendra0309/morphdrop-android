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
        val inputStream = FileHelper.readFileFromUri(context, txtUri)
        val bytes = inputStream.readBytes()
        val textContent = String(bytes, Charsets.UTF_8)
        val lines = textContent.split(Regex("[\\r\\n]+"))
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

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1F2328")
                textSize = spToPx(9.5f) // Reduced from 11f
                typeface = Typeface.DEFAULT
            }

            for ((index, line) in lines.withIndex()) {
                kotlinx.coroutines.yield() // Support cancellation
                if (line.isEmpty()) {
                    yPos += spToPx(9.5f) * 1.5f // Adjusted spacing
                    if (yPos > maxBottom) {
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        currentPage = pdfDocument.startPage(pageInfo)
                        canvas = currentPage.canvas
                        yPos = MARGIN_TOP
                    }
                    continue
                }

                val layout = createStaticLayout(line, textPaint, usableWidth)
                val layoutHeight = layout.height.toFloat()

                if (yPos + layoutHeight > maxBottom) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    yPos = MARGIN_TOP
                }

                canvas.save()
                canvas.translate(MARGIN_LEFT, yPos)
                layout.draw(canvas)
                canvas.restore()

                yPos += layoutHeight + 4f
                
                val currentProgress = 30 + ((index + 1).toFloat() / lines.size * 60).toInt()
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
            FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, baos.toByteArray())
        } finally {
            try { pdfDocument.close() } catch (_: Exception) {}
            try { inputStream.close() } catch (_: Exception) {}
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

    private fun spToPx(sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }
}
