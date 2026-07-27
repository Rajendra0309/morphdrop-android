package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import javax.inject.Inject

class MdToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val PAGE_WIDTH = 595 // A4 Width in points
        private const val PAGE_HEIGHT = 842 // A4 Height in points
        private const val MARGIN_LEFT = 40f
        private const val MARGIN_RIGHT = 40f
        private const val MARGIN_TOP = 40f
        private const val MARGIN_BOTTOM = 40f
    }

    private sealed class MdElement {
        data class Heading(val level: Int, val text: String) : MdElement()
        data class Paragraph(val text: String) : MdElement()
        data class BulletList(val items: List<String>) : MdElement()
        data class NumberedList(val items: List<String>) : MdElement()
        data class CodeBlock(val lines: List<String>) : MdElement()
        data class Table(val headers: List<String>, val rows: List<List<String>>) : MdElement()
        data class Blockquote(val text: String) : MdElement()
        object HorizontalRule : MdElement()
    }

    suspend operator fun invoke(
        mdUri: Uri,
        outputFileName: String = "md_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        val inputStream = FileHelper.readFileFromUri(context, mdUri)
        val bytes = inputStream.readBytes()
        try { inputStream.close() } catch (_: Exception) {}

        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
        val rawLines = reader.readLines()
        try { reader.close() } catch (_: Exception) {}

        val elements = parseMarkdown(rawLines)
        val pdfDocument = PdfDocument()

        try {
            val usableWidth = (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT).toInt()
            val maxBottom = PAGE_HEIGHT - MARGIN_BOTTOM

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas
            var yPos = MARGIN_TOP

            fun checkNewPage(neededHeight: Float) {
                if (yPos + neededHeight > maxBottom) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    yPos = MARGIN_TOP
                }
            }

            for (element in elements) {
                when (element) {
                    is MdElement.Heading -> {
                        val textSizePt = when (element.level) {
                            1 -> 18f
                            2 -> 15f
                            3 -> 13f
                            4 -> 11f
                            5 -> 10.5f
                            else -> 10f
                        }

                        val topPadding = when (element.level) {
                            1 -> 12f
                            2 -> 10f
                            else -> 6f
                        }

                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = textSizePt
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }

                        val formattedText = formatMarkdownInline(element.text)
                        val layout = createStaticLayout(formattedText, paint, usableWidth)
                        val layoutHeight = layout.height.toFloat()

                        checkNewPage(topPadding + layoutHeight + (if (element.level <= 2) 4f else 0f))
                        yPos += topPadding

                        canvas.save()
                        canvas.translate(MARGIN_LEFT, yPos)
                        layout.draw(canvas)
                        canvas.restore()

                        yPos += layoutHeight + 4f

                        if (element.level <= 2) {
                            val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#D0D7DE")
                                style = Paint.Style.STROKE
                                strokeWidth = if (element.level == 1) 1f else 0.5f
                            }
                            canvas.drawLine(MARGIN_LEFT, yPos, MARGIN_LEFT + usableWidth, yPos, rulePaint)
                            yPos += 6f
                        }
                    }

                    is MdElement.Paragraph -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 10f
                            typeface = Typeface.DEFAULT
                        }

                        val formattedText = formatMarkdownInline(element.text)
                        val layout = createStaticLayout(formattedText, paint, usableWidth)
                        val layoutHeight = layout.height.toFloat()

                        checkNewPage(layoutHeight + 4f)

                        canvas.save()
                        canvas.translate(MARGIN_LEFT, yPos)
                        layout.draw(canvas)
                        canvas.restore()

                        yPos += layoutHeight + 5f
                    }

                    is MdElement.BulletList -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 10f
                            typeface = Typeface.DEFAULT
                        }

                        val listWidth = usableWidth - 18

                        for (item in element.items) {
                            val formattedText = formatMarkdownInline(item)
                            val layout = createStaticLayout(formattedText, paint, listWidth)
                            val layoutHeight = layout.height.toFloat()

                            checkNewPage(layoutHeight + 3f)

                            val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#1F2328")
                                style = Paint.Style.FILL
                            }
                            canvas.drawCircle(MARGIN_LEFT + 6f, yPos + 6f, 2.2f, bulletPaint)

                            canvas.save()
                            canvas.translate(MARGIN_LEFT + 18f, yPos)
                            layout.draw(canvas)
                            canvas.restore()

                            yPos += layoutHeight + 3f
                        }
                        yPos += 3f
                    }

                    is MdElement.NumberedList -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 10f
                            typeface = Typeface.DEFAULT
                        }

                        val listWidth = usableWidth - 20

                        for ((idx, item) in element.items.withIndex()) {
                            val formattedText = formatMarkdownInline(item)
                            val layout = createStaticLayout(formattedText, paint, listWidth)
                            val layoutHeight = layout.height.toFloat()

                            checkNewPage(layoutHeight + 3f)

                            val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#57606A")
                                textSize = 10f
                                typeface = Typeface.DEFAULT
                            }
                            val numStr = "${idx + 1}."
                            canvas.drawText(numStr, MARGIN_LEFT, yPos + 10f, numPaint)

                            canvas.save()
                            canvas.translate(MARGIN_LEFT + 20f, yPos)
                            layout.draw(canvas)
                            canvas.restore()

                            yPos += layoutHeight + 3f
                        }
                        yPos += 3f
                    }

                    is MdElement.CodeBlock -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 9f
                            typeface = Typeface.MONOSPACE
                        }

                        val combinedCode = element.lines.joinToString("\n")
                        val codeWidth = usableWidth - 16
                        val layout = createStaticLayout(combinedCode, paint, codeWidth)
                        val layoutHeight = layout.height.toFloat()
                        val boxHeight = layoutHeight + 12f

                        checkNewPage(boxHeight + 6f)

                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F6F8FA")
                            style = Paint.Style.FILL
                        }
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }

                        val rect = RectF(MARGIN_LEFT, yPos, MARGIN_LEFT + usableWidth, yPos + boxHeight)
                        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
                        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

                        canvas.save()
                        canvas.translate(MARGIN_LEFT + 8f, yPos + 6f)
                        layout.draw(canvas)
                        canvas.restore()

                        yPos += boxHeight + 6f
                    }

                    is MdElement.Table -> {
                        val headers = element.headers
                        val rows = element.rows
                        val allRows = mutableListOf<List<String>>()
                        if (headers.isNotEmpty()) allRows.add(headers)
                        allRows.addAll(rows)

                        if (allRows.isEmpty()) continue

                        val maxCols = allRows.maxOfOrNull { it.size } ?: 1
                        val colWidth = usableWidth / maxCols.coerceAtLeast(1)

                        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 9.5f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }

                        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT
                        }

                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F6F8FA")
                            style = Paint.Style.FILL
                        }

                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.5f
                        }

                        for ((rIdx, row) in allRows.withIndex()) {
                            val currentPaint = if (rIdx == 0) headerPaint else cellPaint
                            var maxRowHeight = 20f

                            val cellLayouts = mutableListOf<StaticLayout>()
                            for (cIdx in 0 until maxCols) {
                                val cellText = if (cIdx < row.size) row[cIdx] else ""
                                val formatted = formatMarkdownInline(cellText)
                                val layout = createStaticLayout(formatted, currentPaint, (colWidth - 8).coerceAtLeast(1))
                                cellLayouts.add(layout)
                                val h = layout.height.toFloat() + 8f
                                if (h > maxRowHeight) maxRowHeight = h
                            }

                            checkNewPage(maxRowHeight)

                            if (rIdx == 0) {
                                val rect = RectF(MARGIN_LEFT, yPos, MARGIN_LEFT + usableWidth, yPos + maxRowHeight)
                                canvas.drawRect(rect, bgPaint)
                            }

                            for (cIdx in 0 until maxCols) {
                                val xPos = MARGIN_LEFT + (cIdx * colWidth)
                                val rect = RectF(xPos, yPos, xPos + colWidth, yPos + maxRowHeight)
                                canvas.drawRect(rect, borderPaint)

                                val layout = cellLayouts[cIdx]
                                canvas.save()
                                canvas.translate(xPos + 4f, yPos + 4f)
                                layout.draw(canvas)
                                canvas.restore()
                            }

                            yPos += maxRowHeight
                        }
                        yPos += 6f
                    }

                    is MdElement.Blockquote -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#57606A")
                            textSize = 10f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        }

                        val quoteWidth = usableWidth - 16
                        val formattedText = formatMarkdownInline(element.text)
                        val layout = createStaticLayout(formattedText, paint, quoteWidth)
                        val layoutHeight = layout.height.toFloat()

                        checkNewPage(layoutHeight + 4f)

                        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.FILL
                        }
                        val barRect = RectF(MARGIN_LEFT, yPos, MARGIN_LEFT + 3f, yPos + layoutHeight)
                        canvas.drawRect(barRect, barPaint)

                        canvas.save()
                        canvas.translate(MARGIN_LEFT + 10f, yPos)
                        layout.draw(canvas)
                        canvas.restore()

                        yPos += layoutHeight + 5f
                    }

                    is MdElement.HorizontalRule -> {
                        yPos += 4f
                        checkNewPage(6f)

                        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                        }
                        canvas.drawLine(MARGIN_LEFT, yPos, MARGIN_LEFT + usableWidth, yPos, linePaint)
                        yPos += 8f
                    }
                }
            }

            pdfDocument.finishPage(currentPage)

            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, baos.toByteArray())
        } finally {
            try { pdfDocument.close() } catch (_: Exception) {}
        }
    }

    private fun parseMarkdown(lines: List<String>): List<MdElement> {
        val elements = mutableListOf<MdElement>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    elements.add(MdElement.CodeBlock(codeLines))
                }

                trimmed.startsWith("# ") -> elements.add(MdElement.Heading(1, trimmed.removePrefix("# ").trim()))
                trimmed.startsWith("## ") -> elements.add(MdElement.Heading(2, trimmed.removePrefix("## ").trim()))
                trimmed.startsWith("### ") -> elements.add(MdElement.Heading(3, trimmed.removePrefix("### ").trim()))
                trimmed.startsWith("#### ") -> elements.add(MdElement.Heading(4, trimmed.removePrefix("#### ").trim()))
                trimmed.startsWith("##### ") -> elements.add(MdElement.Heading(5, trimmed.removePrefix("##### ").trim()))
                trimmed.startsWith("###### ") -> elements.add(MdElement.Heading(6, trimmed.removePrefix("###### ").trim()))

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> elements.add(MdElement.HorizontalRule)

                trimmed.startsWith("> ") -> elements.add(MdElement.Blockquote(trimmed.removePrefix("> ").trim()))

                trimmed.startsWith("|") -> {
                    val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    i++
                    if (i < lines.size && lines[i].trim().startsWith("|") && lines[i].contains("---")) {
                        i++
                    }
                    val tableRows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val rowCells = lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        if (rowCells.isNotEmpty()) tableRows.add(rowCells)
                        i++
                    }
                    i--
                    elements.add(MdElement.Table(headers, tableRows))
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletItems = mutableListOf<String>()
                    while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                        bulletItems.add(lines[i].trim().substring(2).trim())
                        i++
                    }
                    i--
                    elements.add(MdElement.BulletList(bulletItems))
                }

                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val numItems = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s+.*"))) {
                        val content = lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s+"), "")
                        numItems.add(content)
                        i++
                    }
                    i--
                    elements.add(MdElement.NumberedList(numItems))
                }

                trimmed.isEmpty() -> {}

                else -> {
                    elements.add(MdElement.Paragraph(trimmed))
                }
            }
            i++
        }

        return elements
    }

    private fun formatMarkdownInline(input: String): CharSequence {
        if (input.isEmpty()) return ""
        val ssb = SpannableStringBuilder()
        var idx = 0
        val regex = Regex("(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`|\\[.*?\\]\\(.*?\\))")
        val matches = regex.findAll(input)
        for (m in matches) {
            if (m.range.first > idx) {
                ssb.append(input.substring(idx, m.range.first))
            }
            val matchStr = m.value
            val start = ssb.length
            when {
                matchStr.startsWith("**") && matchStr.endsWith("**") && matchStr.length >= 4 -> {
                    val inner = matchStr.substring(2, matchStr.length - 2)
                    ssb.append(inner)
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("*") && matchStr.endsWith("*") && matchStr.length >= 2 -> {
                    val inner = matchStr.substring(1, matchStr.length - 1)
                    ssb.append(inner)
                    ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("`") && matchStr.endsWith("`") && matchStr.length >= 2 -> {
                    val inner = matchStr.substring(1, matchStr.length - 1)
                    ssb.append(inner)
                    ssb.setSpan(TypefaceSpan("monospace"), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("[") && matchStr.contains("](") && matchStr.endsWith(")") -> {
                    val textEnd = matchStr.indexOf("]")
                    val label = matchStr.substring(1, textEnd)
                    ssb.append(label)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#0969DA")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(UnderlineSpan(), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    ssb.append(matchStr)
                }
            }
            idx = m.range.last + 1
        }
        if (idx < input.length) {
            ssb.append(input.substring(idx))
        }
        return ssb
    }

    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, alignment, 1.2f, 0f, false)
        }
    }
}
