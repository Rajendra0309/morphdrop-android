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
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
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

        val rawLines = readTextLines(mdUri)

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

            fun newPage() {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                yPos = MARGIN_TOP
            }

            /**
             * Draws [layout] at a horizontal offset of [xOffset] from the left margin,
             * splitting it across pages by text lines when it is too tall to fit on one
             * page (instead of clipping). [blockTopPadding]/[blockBottomPadding] reserve
             * decoration space (e.g. code-block background) around every page chunk;
             * [onChunk] draws per-chunk decorations and receives the chunk's block
             * bounds plus whether it is the first chunk.
             */
            fun drawLayoutMaybeSplit(
                layout: StaticLayout,
                xOffset: Float = 0f,
                blockTopPadding: Float = 0f,
                blockBottomPadding: Float = 0f,
                spacingAfter: Float = 0f,
                onChunk: ((blockTopY: Float, blockBottomY: Float, isFirstChunk: Boolean) -> Unit)? = null
            ) {
                val totalLines = layout.lineCount
                if (totalLines == 0) {
                    yPos += spacingAfter
                    return
                }
                var firstLine = 0
                var isFirstChunk = true
                while (firstLine < totalLines) {
                    val available = maxBottom - yPos
                    var lastLine = firstLine
                    while (lastLine < totalLines &&
                        (layout.getLineBottom(lastLine) - layout.getLineTop(firstLine)).toFloat() +
                                blockTopPadding + blockBottomPadding <= available
                    ) {
                        lastLine++
                    }
                    if (lastLine == firstLine) {
                        // Not even one line fits in the remaining space: move to a fresh
                        // page, unless already at the top (then draw the line anyway so
                        // we never stall).
                        if (yPos > MARGIN_TOP) {
                            newPage()
                            continue
                        }
                        lastLine = firstLine + 1
                    }
                    val chunkTop = layout.getLineTop(firstLine).toFloat()
                    val chunkHeight = layout.getLineBottom(lastLine - 1).toFloat() - chunkTop
                    val blockTopY = yPos
                    val blockBottomY = yPos + blockTopPadding + chunkHeight + blockBottomPadding
                    onChunk?.invoke(blockTopY, blockBottomY, isFirstChunk)
                    canvas.save()
                    canvas.clipRect(
                        MARGIN_LEFT + xOffset,
                        blockTopY + blockTopPadding,
                        MARGIN_LEFT + xOffset + layout.width,
                        blockTopY + blockTopPadding + chunkHeight
                    )
                    canvas.translate(MARGIN_LEFT + xOffset, blockTopY + blockTopPadding - chunkTop)
                    layout.draw(canvas)
                    canvas.restore()
                    yPos = blockBottomY
                    firstLine = lastLine
                    isFirstChunk = false
                    if (firstLine < totalLines) newPage()
                }
                yPos += spacingAfter
            }

            /**
             * Draws a table row whose height exceeds a full page by slicing every
             * cell's [StaticLayout] line-by-line across pages (instead of clipping).
             * All cells in a row share the same paint, so line metrics are uniform
             * and the tallest cell's layout drives the chunk boundaries.
             */
            fun drawSplitTableRow(
                rIdx: Int,
                cellLayouts: List<StaticLayout>,
                maxCols: Int,
                colWidth: Float,
                headerBg: Paint,
                border: Paint
            ) {
                val refLayout = cellLayouts.maxByOrNull { it.lineCount } ?: return
                val totalLines = refLayout.lineCount
                if (totalLines == 0) return
                var firstLine = 0
                while (firstLine < totalLines) {
                    val available = maxBottom - yPos
                    var lastLine = firstLine
                    while (lastLine < totalLines &&
                        (refLayout.getLineBottom(lastLine) - refLayout.getLineTop(firstLine)).toFloat() + 8f <= available
                    ) {
                        lastLine++
                    }
                    if (lastLine == firstLine) {
                        // Not even one line fits: fresh page, unless already at the
                        // top (then draw the line anyway so we never stall).
                        if (yPos > MARGIN_TOP) {
                            newPage()
                            continue
                        }
                        lastLine = firstLine + 1
                    }
                    val chunkTop = refLayout.getLineTop(firstLine).toFloat()
                    val chunkHeight = refLayout.getLineBottom(lastLine - 1).toFloat() - chunkTop
                    val blockHeight = chunkHeight + 8f
                    val blockTopY = yPos
                    val blockBottomY = yPos + blockHeight

                    if (rIdx == 0) {
                        canvas.drawRect(RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + usableWidth, blockBottomY), headerBg)
                    }
                    for (cIdx in 0 until maxCols) {
                        val xPos = MARGIN_LEFT + (cIdx * colWidth)
                        canvas.drawRect(RectF(xPos, blockTopY, xPos + colWidth, blockBottomY), border)
                        val layout = cellLayouts[cIdx]
                        canvas.save()
                        canvas.clipRect(xPos + 4f, blockTopY + 4f, xPos + colWidth - 4f, blockTopY + 4f + chunkHeight)
                        canvas.translate(xPos + 4f, blockTopY + 4f - chunkTop)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                    yPos = blockBottomY
                    firstLine = lastLine
                    if (firstLine < totalLines) newPage()
                }
            }

            for (element in elements) {
                kotlinx.coroutines.yield() // Allow cooperative cancellation
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

                        checkNewPage(topPadding)
                        yPos += topPadding
                        drawLayoutMaybeSplit(layout, spacingAfter = 4f)

                        if (element.level <= 2) {
                            checkNewPage(6f)
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
                        drawLayoutMaybeSplit(layout, spacingAfter = 5f)
                    }

                    is MdElement.BulletList -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            textSize = 10f
                            typeface = Typeface.DEFAULT
                        }

                        val listWidth = usableWidth - 18

                        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            style = Paint.Style.FILL
                        }

                        for (item in element.items) {
                            val formattedText = formatMarkdownInline(item)
                            val layout = createStaticLayout(formattedText, paint, listWidth)
                            drawLayoutMaybeSplit(
                                layout,
                                xOffset = 18f,
                                spacingAfter = 3f
                            ) { blockTopY, _, isFirstChunk ->
                                // Bullet belongs to the first chunk only when an item
                                // is split across pages.
                                if (isFirstChunk) {
                                    canvas.drawCircle(MARGIN_LEFT + 6f, blockTopY + 6f, 2.2f, bulletPaint)
                                }
                            }
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

                        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#57606A")
                            textSize = 10f
                            typeface = Typeface.DEFAULT
                        }

                        for ((idx, item) in element.items.withIndex()) {
                            val formattedText = formatMarkdownInline(item)
                            val layout = createStaticLayout(formattedText, paint, listWidth)
                            val numStr = "${idx + 1}."
                            drawLayoutMaybeSplit(
                                layout,
                                xOffset = 20f,
                                spacingAfter = 3f
                            ) { blockTopY, _, isFirstChunk ->
                                if (isFirstChunk) {
                                    canvas.drawText(numStr, MARGIN_LEFT, blockTopY + 10f, numPaint)
                                }
                            }
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

                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F6F8FA")
                            style = Paint.Style.FILL
                        }
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }

                        // Background + border follow every page chunk when a long code
                        // block is split across pages.
                        drawLayoutMaybeSplit(
                            layout,
                            xOffset = 8f,
                            blockTopPadding = 6f,
                            blockBottomPadding = 6f,
                            spacingAfter = 6f
                        ) { blockTopY, blockBottomY, _ ->
                            val rect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + usableWidth, blockBottomY)
                            canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
                            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
                        }
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

                            val pageUsableHeight = maxBottom - MARGIN_TOP
                            if (maxRowHeight > pageUsableHeight) {
                                // Single row taller than a page: split it across pages.
                                drawSplitTableRow(rIdx, cellLayouts, maxCols, colWidth.toFloat(), bgPaint, borderPaint)
                            } else {
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

                        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.FILL
                        }
                        drawLayoutMaybeSplit(
                            layout,
                            xOffset = 10f,
                            spacingAfter = 5f
                        ) { blockTopY, blockBottomY, _ ->
                            val barRect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + 3f, blockBottomY)
                            canvas.drawRect(barRect, barPaint)
                        }
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
