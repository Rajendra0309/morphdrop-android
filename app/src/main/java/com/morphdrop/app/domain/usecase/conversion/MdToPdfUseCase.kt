package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
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

    data class ListItem(
        val text: String,
        val level: Int = 0,
        val isTask: Boolean = false,
        val isChecked: Boolean = false
    )

    data class NumberedListItem(
        val number: Int,
        val text: String,
        val level: Int = 0
    )

    private sealed class MdElement {
        data class Heading(val level: Int, val text: String) : MdElement()
        data class Paragraph(val text: String) : MdElement()
        data class BulletList(val items: List<ListItem>) : MdElement()
        data class NumberedList(val items: List<NumberedListItem>) : MdElement()
        data class CodeBlock(val language: String?, val lines: List<String>) : MdElement()
        data class MathBlock(val formula: String) : MdElement()
        data class Table(
            val headers: List<String>,
            val rows: List<List<String>>,
            val alignments: List<Layout.Alignment>
        ) : MdElement()
        data class Blockquote(val lines: List<String>) : MdElement()
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

            fun drawSplitTableRow(
                rIdx: Int,
                cellLayouts: List<StaticLayout>,
                maxCols: Int,
                colWidths: List<Float>,
                colOffsets: List<Float>,
                rowBg: Paint,
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
                        (refLayout.getLineBottom(lastLine) - refLayout.getLineTop(firstLine)).toFloat() + 10f <= available
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
                    val chunkTop = refLayout.getLineTop(firstLine).toFloat()
                    val chunkHeight = refLayout.getLineBottom(lastLine - 1).toFloat() - chunkTop
                    val blockHeight = chunkHeight + 10f
                    val blockTopY = yPos
                    val blockBottomY = yPos + blockHeight

                    canvas.drawRect(RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + usableWidth, blockBottomY), rowBg)
                    for (cIdx in 0 until maxCols) {
                        val xPos = MARGIN_LEFT + colOffsets[cIdx]
                        val cWidth = colWidths[cIdx]
                        canvas.drawRect(RectF(xPos, blockTopY, xPos + cWidth, blockBottomY), border)
                        val layout = cellLayouts[cIdx]
                        canvas.save()
                        canvas.clipRect(xPos + 6f, blockTopY + 5f, xPos + cWidth - 6f, blockTopY + 5f + chunkHeight)
                        canvas.translate(xPos + 6f, blockTopY + 5f - chunkTop)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                    yPos = blockBottomY
                    firstLine = lastLine
                    if (firstLine < totalLines) newPage()
                }
            }

            for (element in elements) {
                kotlinx.coroutines.yield()
                when (element) {
                    is MdElement.Heading -> {
                        val textSizePt = when (element.level) {
                            1 -> 20f
                            2 -> 16f
                            3 -> 13.5f
                            4 -> 11.5f
                            5 -> 10.5f
                            else -> 9.5f
                        }

                        val topPadding = when (element.level) {
                            1 -> 14f
                            2 -> 11f
                            3 -> 8f
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
                                strokeWidth = if (element.level == 1) 1.2f else 0.6f
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

                        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1F2328")
                            style = Paint.Style.FILL
                        }

                        val checkBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#57606A")
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                        }

                        val checkFilledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#0969DA")
                            style = Paint.Style.FILL
                        }

                        val checkMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = 1.2f
                            strokeCap = Paint.Cap.ROUND
                        }

                        for (item in element.items) {
                            val indentOffset = item.level * 16f
                            val textLeftOffset = indentOffset + 16f
                            val listWidth = (usableWidth - textLeftOffset).toInt().coerceAtLeast(1)

                            val formattedText = formatMarkdownInline(item.text)
                            val layout = createStaticLayout(formattedText, paint, listWidth)

                            drawLayoutMaybeSplit(
                                layout,
                                xOffset = textLeftOffset,
                                spacingAfter = 3f
                            ) { blockTopY, _, isFirstChunk ->
                                if (isFirstChunk) {
                                    if (item.isTask) {
                                        val boxX = MARGIN_LEFT + indentOffset + 2f
                                        val boxY = blockTopY + 2f
                                        val boxRect = RectF(boxX, boxY, boxX + 9f, boxY + 9f)
                                        if (item.isChecked) {
                                            canvas.drawRoundRect(boxRect, 2f, 2f, checkFilledPaint)
                                            val path = Path().apply {
                                                moveTo(boxX + 2f, boxY + 4.5f)
                                                lineTo(boxX + 4f, boxY + 6.8f)
                                                lineTo(boxX + 7.2f, boxY + 2.5f)
                                            }
                                            canvas.drawPath(path, checkMarkPaint)
                                        } else {
                                            canvas.drawRoundRect(boxRect, 2f, 2f, checkBorderPaint)
                                        }
                                    } else {
                                        when (item.level % 3) {
                                            0 -> {
                                                bulletPaint.style = Paint.Style.FILL
                                                canvas.drawCircle(MARGIN_LEFT + indentOffset + 5f, blockTopY + 6f, 2.2f, bulletPaint)
                                            }
                                            1 -> {
                                                bulletPaint.style = Paint.Style.STROKE
                                                bulletPaint.strokeWidth = 0.8f
                                                canvas.drawCircle(MARGIN_LEFT + indentOffset + 5f, blockTopY + 6f, 2.0f, bulletPaint)
                                            }
                                            else -> {
                                                bulletPaint.style = Paint.Style.FILL
                                                val sq = RectF(MARGIN_LEFT + indentOffset + 3.5f, blockTopY + 4.5f, MARGIN_LEFT + indentOffset + 6.5f, blockTopY + 7.5f)
                                                canvas.drawRect(sq, bulletPaint)
                                            }
                                        }
                                    }
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

                        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#57606A")
                            textSize = 10f
                            typeface = Typeface.DEFAULT
                        }

                        for (item in element.items) {
                            val indentOffset = item.level * 16f
                            val textLeftOffset = indentOffset + 22f
                            val listWidth = (usableWidth - textLeftOffset).toInt().coerceAtLeast(1)

                            val formattedText = formatMarkdownInline(item.text)
                            val layout = createStaticLayout(formattedText, paint, listWidth)
                            val marker = when (item.level % 3) {
                                1 -> "${('a'.code + ((item.number - 1) % 26)).toChar()}."
                                2 -> "${toRoman(item.number)}."
                                else -> "${item.number}."
                            }

                            drawLayoutMaybeSplit(
                                layout,
                                xOffset = textLeftOffset,
                                spacingAfter = 3f
                            ) { blockTopY, _, isFirstChunk ->
                                if (isFirstChunk) {
                                    canvas.drawText(marker, MARGIN_LEFT + indentOffset, blockTopY + 10f, numPaint)
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

                        val langPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#656D76")
                            textSize = 7.5f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }

                        val combinedCode = element.lines.joinToString("\n")
                        val codeWidth = usableWidth - 20
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

                        val headerHeight = if (!element.language.isNullOrBlank()) 14f else 0f

                        drawLayoutMaybeSplit(
                            layout,
                            xOffset = 10f,
                            blockTopPadding = 6f + headerHeight,
                            blockBottomPadding = 6f,
                            spacingAfter = 6f
                        ) { blockTopY, blockBottomY, isFirstChunk ->
                            val rect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + usableWidth, blockBottomY)
                            canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
                            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

                            if (isFirstChunk && !element.language.isNullOrBlank()) {
                                canvas.drawText(
                                    element.language.uppercase(),
                                    MARGIN_LEFT + usableWidth - 10f - langPaint.measureText(element.language.uppercase()),
                                    blockTopY + 11f,
                                    langPaint
                                )
                            }
                        }
                    }

                    is MdElement.MathBlock -> {
                        val mathPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#0550AE")
                            textSize = 11f
                            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                        }

                        val formattedFormula = formatMathExpression(element.formula)
                        val mathWidth = usableWidth - 28
                        val layout = createStaticLayout(
                            formattedFormula,
                            mathPaint,
                            mathWidth,
                            alignment = Layout.Alignment.ALIGN_CENTER
                        )

                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F6F8FA")
                            style = Paint.Style.FILL
                        }
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }
                        val accentBarPaint = Paint().apply {
                            color = Color.parseColor("#0969DA")
                            style = Paint.Style.FILL
                        }

                        drawLayoutMaybeSplit(
                            layout,
                            xOffset = 14f,
                            blockTopPadding = 8f,
                            blockBottomPadding = 8f,
                            spacingAfter = 6f
                        ) { blockTopY, blockBottomY, _ ->
                            val rect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + usableWidth, blockBottomY)
                            canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
                            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

                            val accentRect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + 3.5f, blockBottomY)
                            canvas.drawRoundRect(accentRect, 2f, 2f, accentBarPaint)
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
                        val alignments = (0 until maxCols).map { idx ->
                            element.alignments.getOrElse(idx) { Layout.Alignment.ALIGN_NORMAL }
                        }

                        // Compute dynamic proportional column widths based on cell text lengths
                        val colContentWeights = FloatArray(maxCols) { 1f }
                        for (row in allRows) {
                            for (c in 0 until maxCols) {
                                val cellLen = if (c < row.size) row[c].length else 0
                                colContentWeights[c] = maxOf(colContentWeights[c], cellLen.toFloat().coerceAtLeast(3f))
                            }
                        }
                        val totalWeight = colContentWeights.sum()
                        val colWidths = mutableListOf<Float>()
                        var accumulatedOffset = 0f
                        val colOffsets = mutableListOf<Float>()

                        for (c in 0 until maxCols) {
                            colOffsets.add(accumulatedOffset)
                            val proportionalWidth = if (totalWeight > 0) {
                                (usableWidth * (colContentWeights[c] / totalWeight)).coerceAtLeast(35f)
                            } else {
                                usableWidth.toFloat() / maxCols
                            }
                            colWidths.add(proportionalWidth)
                            accumulatedOffset += proportionalWidth
                        }

                        // Normalize to exactly fill usableWidth
                        val sumWidths = colWidths.sum()
                        if (sumWidths > 0) {
                            val scale = usableWidth.toFloat() / sumWidths
                            accumulatedOffset = 0f
                            for (c in 0 until maxCols) {
                                colOffsets[c] = accumulatedOffset
                                colWidths[c] = colWidths[c] * scale
                                accumulatedOffset += colWidths[c]
                            }
                            colWidths[maxCols - 1] = (usableWidth - colOffsets[maxCols - 1]).coerceAtLeast(10f)
                        }

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

                        val headerBg = Paint().apply {
                            color = Color.parseColor("#F2F4F7")
                            style = Paint.Style.FILL
                        }

                        val evenRowBg = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }

                        val oddRowBg = Paint().apply {
                            color = Color.parseColor("#F8F9FA")
                            style = Paint.Style.FILL
                        }

                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#D0D7DE")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.6f
                        }

                        for ((rIdx, row) in allRows.withIndex()) {
                            val currentPaint = if (rIdx == 0) headerPaint else cellPaint
                            val currentRowBg = when {
                                rIdx == 0 -> headerBg
                                rIdx % 2 == 1 -> oddRowBg
                                else -> evenRowBg
                            }
                            var maxRowHeight = 20f

                            val cellLayouts = mutableListOf<StaticLayout>()
                            for (cIdx in 0 until maxCols) {
                                val cellText = if (cIdx < row.size) row[cIdx] else ""
                                val formatted = formatMarkdownInline(cellText)
                                val cellInnerWidth = (colWidths[cIdx] - 12f).toInt().coerceAtLeast(1)
                                val align = alignments[cIdx]
                                val layout = createStaticLayout(formatted, currentPaint, cellInnerWidth, alignment = align)
                                cellLayouts.add(layout)
                                val h = layout.height.toFloat() + 10f
                                if (h > maxRowHeight) maxRowHeight = h
                            }

                            val pageUsableHeight = maxBottom - MARGIN_TOP
                            if (maxRowHeight > pageUsableHeight) {
                                drawSplitTableRow(rIdx, cellLayouts, maxCols, colWidths, colOffsets, currentRowBg, borderPaint)
                            } else {
                                checkNewPage(maxRowHeight)
                                val rowRect = RectF(MARGIN_LEFT, yPos, MARGIN_LEFT + usableWidth, yPos + maxRowHeight)
                                canvas.drawRect(rowRect, currentRowBg)

                                for (cIdx in 0 until maxCols) {
                                    val xPos = MARGIN_LEFT + colOffsets[cIdx]
                                    val cWidth = colWidths[cIdx]
                                    val cellRect = RectF(xPos, yPos, xPos + cWidth, yPos + maxRowHeight)
                                    canvas.drawRect(cellRect, borderPaint)

                                    val layout = cellLayouts[cIdx]
                                    canvas.save()
                                    canvas.translate(xPos + 6f, yPos + 5f)
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

                        val quoteWidth = usableWidth - 20
                        val combinedQuote = element.lines.joinToString("\n")
                        val formattedText = formatMarkdownInline(combinedQuote)
                        val layout = createStaticLayout(formattedText, paint, quoteWidth)

                        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#0969DA")
                            style = Paint.Style.FILL
                        }

                        val bgPaint = Paint().apply {
                            color = Color.parseColor("#F8FAFC")
                            style = Paint.Style.FILL
                        }

                        drawLayoutMaybeSplit(
                            layout,
                            xOffset = 12f,
                            blockTopPadding = 5f,
                            blockBottomPadding = 5f,
                            spacingAfter = 6f
                        ) { blockTopY, blockBottomY, _ ->
                            val bgRect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + usableWidth, blockBottomY)
                            canvas.drawRoundRect(bgRect, 2f, 2f, bgPaint)
                            val barRect = RectF(MARGIN_LEFT, blockTopY, MARGIN_LEFT + 3.5f, blockBottomY)
                            canvas.drawRoundRect(barRect, 1.5f, 1.5f, barPaint)
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
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            when {
                trimmed.startsWith("```") -> {
                    val language = trimmed.removePrefix("```").trim().ifBlank { null }
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    elements.add(MdElement.CodeBlock(language, codeLines))
                }

                trimmed.startsWith("$$") -> {
                    if (trimmed.length >= 4 && trimmed.endsWith("$$")) {
                        // Single-line math block
                        val formula = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                        elements.add(MdElement.MathBlock(formula))
                    } else {
                        // Multi-line math block
                        val startIdx = i
                        val mathLines = mutableListOf<String>()
                        val firstContent = trimmed.removePrefix("$$").trim()
                        if (firstContent.isNotEmpty()) mathLines.add(firstContent)
                        i++
                        var closed = false
                        while (i < lines.size) {
                            val cur = lines[i].trim()
                            if (cur.endsWith("$$")) {
                                val lastContent = cur.removeSuffix("$$").trim()
                                if (lastContent.isNotEmpty()) mathLines.add(lastContent)
                                closed = true
                                break
                            } else {
                                mathLines.add(cur)
                                i++
                            }
                        }
                        if (closed) {
                            elements.add(MdElement.MathBlock(mathLines.joinToString("\n")))
                        } else {
                            // If unclosed, fall back to paragraphs
                            for (lineIdx in startIdx until lines.size) {
                                val fallbackText = lines[lineIdx].trim()
                                if (fallbackText.isNotEmpty()) {
                                    elements.add(MdElement.Paragraph(fallbackText))
                                }
                            }
                        }
                    }
                }

                trimmed.startsWith("# ") -> elements.add(MdElement.Heading(1, trimmed.removePrefix("# ").trim()))
                trimmed.startsWith("## ") -> elements.add(MdElement.Heading(2, trimmed.removePrefix("## ").trim()))
                trimmed.startsWith("### ") -> elements.add(MdElement.Heading(3, trimmed.removePrefix("### ").trim()))
                trimmed.startsWith("#### ") -> elements.add(MdElement.Heading(4, trimmed.removePrefix("#### ").trim()))
                trimmed.startsWith("##### ") -> elements.add(MdElement.Heading(5, trimmed.removePrefix("##### ").trim()))
                trimmed.startsWith("###### ") -> elements.add(MdElement.Heading(6, trimmed.removePrefix("###### ").trim()))

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> elements.add(MdElement.HorizontalRule)

                trimmed.startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        val cleaned = lines[i].trim().replaceFirst(Regex("^>+\\s?"), "")
                        quoteLines.add(cleaned)
                        i++
                    }
                    i--
                    elements.add(MdElement.Blockquote(quoteLines))
                }

                trimmed.startsWith("|") -> {
                    val headers = parseTableRow(trimmed)
                    i++
                    val alignments = mutableListOf<Layout.Alignment>()
                    if (i < lines.size && lines[i].trim().startsWith("|") && lines[i].contains("---")) {
                        alignments.addAll(parseTableAlignments(lines[i].trim()))
                        i++
                    }
                    val tableRows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val rowCells = parseTableRow(lines[i].trim())
                        if (rowCells.isNotEmpty()) tableRows.add(rowCells)
                        i++
                    }
                    i--
                    elements.add(MdElement.Table(headers, tableRows, alignments))
                }

                isBulletListItem(rawLine) -> {
                    val bulletItems = mutableListOf<ListItem>()
                    while (i < lines.size && isBulletListItem(lines[i])) {
                        bulletItems.add(parseBulletItem(lines[i]))
                        i++
                    }
                    i--
                    elements.add(MdElement.BulletList(bulletItems))
                }

                isNumberedListItem(rawLine) -> {
                    val numItems = mutableListOf<NumberedListItem>()
                    while (i < lines.size && isNumberedListItem(lines[i])) {
                        numItems.add(parseNumberedItem(lines[i]))
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

    private fun isBulletListItem(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
    }

    private fun parseBulletItem(line: String): ListItem {
        val indent = line.takeWhile { it == ' ' || it == '\t' }.length
        val level = (indent / 2).coerceAtMost(4)
        val content = line.trim().substring(2).trim()

        return when {
            content.startsWith("[ ] ") -> ListItem(text = content.removePrefix("[ ] ").trim(), level = level, isTask = true, isChecked = false)
            content.startsWith("[x] ", ignoreCase = true) -> ListItem(text = content.substring(4).trim(), level = level, isTask = true, isChecked = true)
            else -> ListItem(text = content, level = level, isTask = false, isChecked = false)
        }
    }

    private fun isNumberedListItem(line: String): Boolean {
        return line.trim().matches(Regex("^\\d+\\.\\s+.*"))
    }

    private fun parseNumberedItem(line: String): NumberedListItem {
        val indent = line.takeWhile { it == ' ' || it == '\t' }.length
        val level = (indent / 2).coerceAtMost(4)
        val match = Regex("^(\\d+)\\.\\s+(.*)").find(line.trim())
        val number = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val text = match?.groupValues?.get(2)?.trim() ?: line.trim()
        return NumberedListItem(number = number, text = text, level = level)
    }

    private fun parseTableRow(line: String): List<String> {
        val stripped = line.trim().removePrefix("|").removeSuffix("|")
        return stripped.split("|").map { it.trim() }
    }

    private fun parseTableAlignments(delimiterLine: String): List<Layout.Alignment> {
        val cols = parseTableRow(delimiterLine)
        return cols.map { col ->
            val trimmed = col.trim()
            val startsWithColon = trimmed.startsWith(":")
            val endsWithColon = trimmed.endsWith(":")
            when {
                startsWithColon && endsWithColon -> Layout.Alignment.ALIGN_CENTER
                endsWithColon -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
        }
    }

    private fun toRoman(n: Int): String {
        val romans = listOf(
            10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i"
        )
        var num = n.coerceIn(1, 39)
        val sb = StringBuilder()
        for ((value, sym) in romans) {
            while (num >= value) {
                sb.append(sym)
                num -= value
            }
        }
        return sb.toString()
    }

    private fun formatMathExpression(input: String): String {
        var result = input
        // Greek lowercase
        result = result
            .replace(Regex("\\\\alpha\\b"), "α")
            .replace(Regex("\\\\beta\\b"), "β")
            .replace(Regex("\\\\gamma\\b"), "γ")
            .replace(Regex("\\\\delta\\b"), "δ")
            .replace(Regex("\\\\epsilon\\b"), "ε")
            .replace(Regex("\\\\zeta\\b"), "ζ")
            .replace(Regex("\\\\eta\\b"), "η")
            .replace(Regex("\\\\theta\\b"), "θ")
            .replace(Regex("\\\\iota\\b"), "ι")
            .replace(Regex("\\\\kappa\\b"), "κ")
            .replace(Regex("\\\\lambda\\b"), "λ")
            .replace(Regex("\\\\mu\\b"), "μ")
            .replace(Regex("\\\\nu\\b"), "ν")
            .replace(Regex("\\\\xi\\b"), "ξ")
            .replace(Regex("\\\\pi\\b"), "π")
            .replace(Regex("\\\\rho\\b"), "ρ")
            .replace(Regex("\\\\sigma\\b"), "σ")
            .replace(Regex("\\\\tau\\b"), "τ")
            .replace(Regex("\\\\phi\\b"), "φ")
            .replace(Regex("\\\\chi\\b"), "χ")
            .replace(Regex("\\\\psi\\b"), "ψ")
            .replace(Regex("\\\\omega\\b"), "ω")

        // Greek uppercase
        result = result
            .replace(Regex("\\\\Gamma\\b"), "Γ")
            .replace(Regex("\\\\Delta\\b"), "Δ")
            .replace(Regex("\\\\Theta\\b"), "Θ")
            .replace(Regex("\\\\Lambda\\b"), "Λ")
            .replace(Regex("\\\\Xi\\b"), "Ξ")
            .replace(Regex("\\\\Pi\\b"), "Π")
            .replace(Regex("\\\\Sigma\\b"), "Σ")
            .replace(Regex("\\\\Phi\\b"), "Φ")
            .replace(Regex("\\\\Psi\\b"), "Ψ")
            .replace(Regex("\\\\Omega\\b"), "Ω")

        // Math operators and relations
        result = result
            .replace(Regex("\\\\times\\b"), "×")
            .replace(Regex("\\\\div\\b"), "÷")
            .replace(Regex("\\\\pm\\b"), "±")
            .replace(Regex("\\\\mp\\b"), "∓")
            .replace(Regex("\\\\cdot\\b"), "·")
            .replace(Regex("\\\\leq?\\b"), "≤")
            .replace(Regex("\\\\geq?\\b"), "≥")
            .replace(Regex("\\\\neq?\\b"), "≠")
            .replace(Regex("\\\\approx\\b"), "≈")
            .replace(Regex("\\\\equiv\\b"), "≡")
            .replace(Regex("\\\\in\\b"), "∈")
            .replace(Regex("\\\\notin\\b"), "∉")
            .replace(Regex("\\\\subset\\b"), "⊂")
            .replace(Regex("\\\\subseteq\\b"), "⊆")
            .replace(Regex("\\\\infty\\b"), "∞")
            .replace(Regex("\\\\partial\\b"), "∂")
            .replace(Regex("\\\\nabla\\b"), "∇")
            .replace(Regex("\\\\sum\\b"), "∑")
            .replace(Regex("\\\\prod\\b"), "∏")
            .replace(Regex("\\\\int\\b"), "∫")
            .replace(Regex("\\\\forall\\b"), "∀")
            .replace(Regex("\\\\exists\\b"), "∃")
            .replace(Regex("\\\\rightarrow\\b|\\\\to\\b"), "→")
            .replace(Regex("\\\\leftarrow\\b"), "←")
            .replace(Regex("\\\\leftrightarrow\\b"), "↔")
            .replace(Regex("\\\\Rightarrow\\b"), "⇒")
            .replace(Regex("\\\\Leftarrow\\b"), "⇐")
            .replace(Regex("\\\\Leftrightarrow\\b"), "⇔")

        // Fractions \frac{A}{B} -> (A) / (B)
        result = result.replace(Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}"), "($1) / ($2)")
        // Square root \sqrt{A} -> √(A)
        result = result.replace(Regex("\\\\sqrt\\{([^}]+)\\}"), "√($1)")

        // Superscripts
        val supMap = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
            'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ'
        )
        result = result.replace(Regex("\\^\\{([0-9+\\-=()nix]+)\\}|\\^([0-9+\\-=()nix])")) { match ->
            val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
            content.map { supMap[it] ?: it }.joinToString("")
        }

        // Subscripts
        val subMap = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
            '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
            'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'i' to 'ᵢ',
            'j' to 'ⱼ', 'k' to 'ₖ', 'n' to 'ₙ', 'm' to 'ₘ'
        )
        result = result.replace(Regex("_\\{([0-9+\\-=()aeoxijknm]+)\\}|_([0-9+\\-=()aeoxijknm])")) { match ->
            val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
            content.map { subMap[it] ?: it }.joinToString("")
        }

        return result
    }

    private fun formatMarkdownInline(input: String): CharSequence {
        if (input.isEmpty()) return ""
        val ssb = SpannableStringBuilder()
        var idx = 0
        val regex = Regex("(\\*\\*\\*.*?\\*\\*\\*|___.*?___|\\*\\*.*?\\*\\*|\\b__.*?__\\b|~~.*?~~|==.*?==|\\*.*?\\*|\\b_.*?_\\b|`.*?`|\\$[^\\s$](?:[^$\\n]*?[^\\s$])?\\$|\\[.*?\\]\\(.*?\\))")
        val matches = regex.findAll(input)
        for (m in matches) {
            if (m.range.first > idx) {
                ssb.append(input.substring(idx, m.range.first))
            }
            val matchStr = m.value
            val start = ssb.length
            when {
                (matchStr.startsWith("***") && matchStr.endsWith("***") && matchStr.length >= 6) ||
                (matchStr.startsWith("___") && matchStr.endsWith("___") && matchStr.length >= 6) -> {
                    val inner = matchStr.substring(3, matchStr.length - 3)
                    ssb.append(inner)
                    ssb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                (matchStr.startsWith("**") && matchStr.endsWith("**") && matchStr.length >= 4) ||
                (matchStr.startsWith("__") && matchStr.endsWith("__") && matchStr.length >= 4) -> {
                    val inner = matchStr.substring(2, matchStr.length - 2)
                    ssb.append(inner)
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("~~") && matchStr.endsWith("~~") && matchStr.length >= 4 -> {
                    val inner = matchStr.substring(2, matchStr.length - 2)
                    ssb.append(inner)
                    ssb.setSpan(StrikethroughSpan(), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("==") && matchStr.endsWith("==") && matchStr.length >= 4 -> {
                    val inner = matchStr.substring(2, matchStr.length - 2)
                    ssb.append(inner)
                    ssb.setSpan(BackgroundColorSpan(Color.parseColor("#FFF3A3")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#1F2328")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                (matchStr.startsWith("*") && matchStr.endsWith("*") && matchStr.length >= 2) ||
                (matchStr.startsWith("_") && matchStr.endsWith("_") && matchStr.length >= 2) -> {
                    val inner = matchStr.substring(1, matchStr.length - 1)
                    ssb.append(inner)
                    ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("`") && matchStr.endsWith("`") && matchStr.length >= 2 -> {
                    val inner = matchStr.substring(1, matchStr.length - 1)
                    ssb.append(inner)
                    ssb.setSpan(TypefaceSpan("monospace"), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(BackgroundColorSpan(Color.parseColor("#EFF1F3")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#CF222E")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                matchStr.startsWith("$") && matchStr.endsWith("$") && matchStr.length >= 2 -> {
                    val mathContent = matchStr.substring(1, matchStr.length - 1)
                    val formattedMath = formatMathExpression(mathContent)
                    ssb.append(formattedMath)
                    ssb.setSpan(TypefaceSpan("serif"), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#0550AE")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width.coerceAtLeast(1), alignment, 1.25f, 0f, false)
        }
    }
}
