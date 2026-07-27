package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

class WordToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    // Standard A4 page size in points (1/72 inch): 595 x 842 pt
    private val pageWidth = 595
    private val pageHeight = 842

    // Standard 0.75-inch margins (54 pt)
    private val marginLeft = 54f
    private val marginRight = 54f
    private val marginTop = 54f
    private val marginBottom = 54f
    private val contentWidth = pageWidth - (marginLeft + marginRight) // 487 pt
    private val contentHeight = pageHeight - (marginTop + marginBottom)

    suspend operator fun invoke(
        inputUri: Uri,
        outputFileName: String = "word_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        val inputStream = FileHelper.readFileFromUri(context, inputUri)
        val bytes = inputStream.readBytes()
        try { inputStream.close() } catch (_: Exception) {}

        val pdfBytes = try {
            renderDocxWithPoi(bytes)
        } catch (e: Throwable) {
            // Fallback to Native OOXML Zip Canvas Engine if POI fails
            renderDocxWithZipFallback(bytes)
        }

        FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, pdfBytes)
    }

    // =========================================================================
    // 1. APACHE POI HIGH-FIDELITY ENGINE
    // =========================================================================
    private fun renderDocxWithPoi(bytes: ByteArray): ByteArray {
        val document = XWPFDocument(ByteArrayInputStream(bytes))
        val pdfDocument = PdfDocument()

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas

        var currentY = marginTop

        fun checkNewPage(neededHeight: Float): Canvas {
            if (currentY + neededHeight > pageHeight - marginBottom) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                canvas.drawColor(Color.WHITE)
                currentY = marginTop
            }
            return canvas
        }

        // Draw background for first page
        canvas.drawColor(Color.WHITE)

        val listCounters = mutableMapOf<String, Int>()
        val elements = document.bodyElements
        for (element in elements) {
            when (element) {
                is XWPFParagraph -> {
                    currentY = renderPoiParagraph(canvas, element, currentY, listCounters) { heightNeeded ->
                        checkNewPage(heightNeeded)
                    }
                }
                is XWPFTable -> {
                    currentY = renderPoiTable(canvas, element, currentY) { heightNeeded ->
                        checkNewPage(heightNeeded)
                    }
                }
            }
        }

        pdfDocument.finishPage(currentPage)

        val baos = ByteArrayOutputStream()
        pdfDocument.writeTo(baos)
        pdfDocument.close()
        try { document.close() } catch (_: Exception) {}

        return baos.toByteArray()
    }

    private fun renderPoiParagraph(
        initialCanvas: Canvas,
        paragraph: XWPFParagraph,
        startY: Float,
        listCounters: MutableMap<String, Int>,
        onPageCheck: (Float) -> Canvas
    ): Float {
        var canvas = initialCanvas
        var currentY = startY

        val text = paragraph.text
        val runs = paragraph.runs

        // Render any embedded pictures in runs first
        for (run in runs) {
            val embeddedPictures = run.embeddedPictures
            for (pic in embeddedPictures) {
                val picData = pic.pictureData?.data ?: continue
                val bitmap = BitmapFactory.decodeByteArray(picData, 0, picData.size) ?: continue

                val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
                val targetW = Math.min(contentWidth, bitmap.width.toFloat() * 0.75f)
                val targetH = targetW * aspect

                canvas = onPageCheck(targetH + 12f)
                val left = marginLeft + (contentWidth - targetW) / 2f
                canvas.drawBitmap(bitmap, null, RectF(left, currentY, left + targetW, currentY + targetH), null)
                currentY += targetH + 12f
            }
        }

        if (text.isNullOrBlank() && runs.isEmpty()) {
            return currentY + 6f
        }

        // Spacing before / after (in dxa: 20 dxa = 1 pt)
        val spaceBefore = (paragraph.spacingBefore / 20f).coerceAtLeast(0f)
        val spaceAfter = (paragraph.spacingAfter / 20f).coerceAtLeast(0f)

        currentY += spaceBefore

        // Alignment
        val alignment = when (paragraph.alignment) {
            ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            ParagraphAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        // Style & Headings
        val style = paragraph.style?.lowercase() ?: ""
        val isHeading = style.contains("heading") || style.contains("title")

        // Build Spans for rich formatting
        val builder = SpannableStringBuilder()

        val numId = try {
            paragraph.numID?.toString() ?: paragraph.javaClass.getMethod("getCTP").invoke(paragraph)?.let { ctp ->
                ctp.javaClass.getMethod("getPPr").invoke(ctp)?.let { ppr ->
                    ppr.javaClass.getMethod("getNumPr").invoke(ppr)?.let { numpr ->
                        numpr.javaClass.getMethod("getNumId").invoke(numpr)?.let { numid ->
                            numid.javaClass.getMethod("getVal").invoke(numid)?.toString()
                        }
                    }
                }
            }
        } catch (_: Throwable) { null }

        val numIlvl = try {
            paragraph.numIlvl?.toInt() ?: paragraph.javaClass.getMethod("getCTP").invoke(paragraph)?.let { ctp ->
                ctp.javaClass.getMethod("getPPr").invoke(ctp)?.let { ppr ->
                    ppr.javaClass.getMethod("getNumPr").invoke(ppr)?.let { numpr ->
                        numpr.javaClass.getMethod("getIlvl").invoke(numpr)?.let { ilvl ->
                            (ilvl.javaClass.getMethod("getVal").invoke(ilvl) as? Number)?.toInt()
                        }
                    }
                }
            } ?: 0
        } catch (_: Throwable) { 0 }

        val numFmt = try { paragraph.numFmt } catch (_: Throwable) { null }
        val isList = numId != null || numFmt != null || style.contains("list") || style.contains("bullet")

        var listIndent = 0f
        if (isList) {
            listIndent = (numIlvl * 18f) + 16f
            val rawText = (text ?: "").trim()
            val alreadyHasPrefix = rawText.startsWith("•") || rawText.startsWith("◦") || rawText.startsWith("▪") ||
                    rawText.matches(Regex("^\\d+[\\.\\)]\\s+.*")) || rawText.matches(Regex("^[a-zA-Z][\\.\\)]\\s+.*"))

            if (!alreadyHasPrefix) {
                val isBullet = numFmt == "bullet" || numFmt == null && (style.contains("bullet") || numId != null)
                if (isBullet) {
                    val bulletSymbol = when (numIlvl % 3) {
                        0 -> "•  "
                        1 -> "◦  "
                        else -> "▪  "
                    }
                    builder.append(bulletSymbol)
                } else {
                    val key = numId ?: "default_list"
                    val count = (listCounters[key] ?: 0) + 1
                    listCounters[key] = count
                    builder.append("$count.  ")
                }
            }
        }

        var baseFontSize = when {
            style.contains("title") -> 24f
            style.contains("heading 1") || style.contains("heading1") -> 20f
            style.contains("heading 2") || style.contains("heading2") -> 16f
            style.contains("heading 3") || style.contains("heading3") -> 13.5f
            else -> 11f
        }

        var baseColor = when {
            style.contains("title") -> Color.parseColor("#0F172A")
            style.contains("heading 1") || style.contains("heading1") -> Color.parseColor("#1E3A8A")
            style.contains("heading 2") || style.contains("heading2") -> Color.parseColor("#1E40AF")
            style.contains("heading 3") || style.contains("heading3") -> Color.parseColor("#334155")
            else -> Color.parseColor("#1F2937")
        }

        for (run in runs) {
            val runText = run.text() ?: ""
            if (runText.isEmpty()) continue

            val start = builder.length
            builder.append(runText)
            val end = builder.length

            if (run.isBold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
            }
            if (run.isItalic) {
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)
            }
            if (run.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE) {
                builder.setSpan(UnderlineSpan(), start, end, 0)
            }
            if (run.isStrikeThrough) {
                builder.setSpan(StrikethroughSpan(), start, end, 0)
            }

            // Color
            val runColor = run.color
            if (!runColor.isNullOrBlank()) {
                try {
                    val colorInt = Color.parseColor("#$runColor")
                    builder.setSpan(ForegroundColorSpan(colorInt), start, end, 0)
                } catch (_: Exception) {}
            }

            // Highlight background
            try {
                val method = run.javaClass.getMethod("getTextHighlightColor")
                val highlightObj = method.invoke(run)
                if (highlightObj != null) {
                    val hlStr = highlightObj.toString().lowercase()
                    val hlColor = when {
                        hlStr.contains("yellow") -> Color.YELLOW
                        hlStr.contains("green") -> Color.GREEN
                        hlStr.contains("cyan") -> Color.CYAN
                        hlStr.contains("magenta") -> Color.MAGENTA
                        hlStr.contains("gray") -> Color.LTGRAY
                        else -> Color.TRANSPARENT
                    }
                    if (hlColor != Color.TRANSPARENT) {
                        builder.setSpan(BackgroundColorSpan(hlColor), start, end, 0)
                    }
                }
            } catch (_: Throwable) {}

            // Font size
            val fontPt = run.fontSizeAsDouble
            if (fontPt != null && fontPt > 0) {
                builder.setSpan(RelativeSizeSpan((fontPt.toFloat() / baseFontSize)), start, end, 0)
            }
        }

        if (builder.isEmpty()) {
            return currentY
        }

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = baseFontSize
            color = baseColor
            if (isHeading) isFakeBoldText = true
        }

        val rawIndentLeft = try { (paragraph.indentationLeft / 20f).coerceAtLeast(0f) } catch (_: Throwable) { 0f }
        val indentLeft = if (isList) listIndent else rawIndentLeft
        val availWidth = (contentWidth - indentLeft).toInt().coerceAtLeast(10)

        val layout = StaticLayout.Builder.obtain(builder, 0, builder.length, textPaint, availWidth)
            .setAlignment(alignment)
            .setLineSpacing(2f, 1.15f)
            .build()

        canvas = onPageCheck(layout.height + spaceAfter + 4f)

        // Draw heading 1 left accent bar
        if (style.contains("heading 1") || style.contains("heading1")) {
            val barPaint = Paint().apply {
                color = Color.parseColor("#2563EB")
                this.style = Paint.Style.FILL
            }
            canvas.drawRect(marginLeft, currentY, marginLeft + 4f, currentY + layout.height, barPaint)
        }

        canvas.save()
        val drawX = marginLeft + indentLeft + if (style.contains("heading 1") || style.contains("heading1")) 8f else 0f
        canvas.translate(drawX, currentY)
        layout.draw(canvas)
        canvas.restore()

        val defaultAfter = if (isHeading) 10f else if (isList) 3f else 6f
        val finalAfter = if (spaceAfter > 0f) spaceAfter else defaultAfter
        return currentY + layout.height + finalAfter
    }

    private fun renderPoiTable(
        initialCanvas: Canvas,
        table: XWPFTable,
        startY: Float,
        onPageCheck: (Float) -> Canvas
    ): Float {
        var canvas = initialCanvas
        var currentY = startY

        val rows = table.rows
        if (rows.isEmpty()) return currentY

        val maxCols = rows.maxOfOrNull { it.tableCells.size } ?: 1

        // Calculate column widths based on cell widths or fallback to equal distribution
        val colWidths = FloatArray(maxCols) { contentWidth / maxCols.toFloat() }
        try {
            val firstRowCells = rows.firstOrNull()?.tableCells
            if (firstRowCells != null && firstRowCells.isNotEmpty()) {
                val explicitWidths = firstRowCells.map { cell ->
                    val w = try { cell.width } catch (_: Throwable) { -1 }
                    if (w > 0) w.toFloat() else -1f
                }
                val totalExplicit = explicitWidths.filter { it > 0 }.sum()
                if (totalExplicit > 0) {
                    for (i in 0 until maxCols) {
                        val w = if (i < explicitWidths.size && explicitWidths[i] > 0) explicitWidths[i] else (totalExplicit / maxCols)
                        colWidths[i] = (w / totalExplicit) * contentWidth
                    }
                }
            }
        } catch (_: Throwable) {}

        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val cellBgPaint = Paint().apply { style = Paint.Style.FILL }

        for ((rowIndex, row) in rows.withIndex()) {
            val cells = row.tableCells

            // Build layout for each cell with formatted paragraph runs
            val cellLayouts = mutableListOf<Pair<StaticLayout, Float>>()
            var maxCellHeight = 26f

            for ((colIndex, cell) in cells.withIndex()) {
                val cWidth = if (colIndex < colWidths.size) colWidths[colIndex] else (contentWidth / maxCols)
                val cellBuilder = SpannableStringBuilder()

                val paragraphs = try { cell.paragraphs } catch (_: Throwable) { emptyList() }
                for (p in paragraphs) {
                    val runs = try { p.runs } catch (_: Throwable) { emptyList() }
                    for (run in runs) {
                        val runText = run.text() ?: ""
                        if (runText.isEmpty()) continue
                        val start = cellBuilder.length
                        cellBuilder.append(runText)
                        val end = cellBuilder.length

                        if (run.isBold) cellBuilder.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                        if (run.isItalic) cellBuilder.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)

                        val colorStr = try { run.color } catch (_: Throwable) { null }
                        if (!colorStr.isNullOrBlank()) {
                            try {
                                cellBuilder.setSpan(ForegroundColorSpan(Color.parseColor("#$colorStr")), start, end, 0)
                            } catch (_: Exception) {}
                        }
                    }
                    cellBuilder.append("\n")
                }

                val trimmedText = if (cellBuilder.endsWith("\n")) cellBuilder.subSequence(0, cellBuilder.length - 1) else cellBuilder
                val fallbackText = try { cell.text ?: "" } catch (_: Throwable) { "" }
                val cellText = if (trimmedText.isEmpty()) fallbackText else trimmedText

                val textPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 10.5f
                    color = if (rowIndex == 0) Color.parseColor("#0F172A") else Color.parseColor("#334155")
                    if (rowIndex == 0) isFakeBoldText = true
                }

                val availW = (cWidth - 12f).toInt().coerceAtLeast(10)
                val layout = StaticLayout.Builder.obtain(
                    cellText, 0, cellText.length, textPaint, availW
                ).build()

                val cellH = layout.height + 12f
                if (cellH > maxCellHeight) maxCellHeight = cellH

                var currentLeft = 0f
                for (k in 0 until colIndex) {
                    currentLeft += if (k < colWidths.size) colWidths[k] else (contentWidth / maxCols)
                }
                cellLayouts.add(Pair(layout, currentLeft))
            }

            canvas = onPageCheck(maxCellHeight + 2f)

            // Draw cells background, borders, and text
            for ((colIndex, cell) in cells.withIndex()) {
                var left = marginLeft
                for (k in 0 until colIndex) {
                    left += if (k < colWidths.size) colWidths[k] else (contentWidth / maxCols)
                }
                val cWidth = if (colIndex < colWidths.size) colWidths[colIndex] else (contentWidth / maxCols)

                val top = currentY
                val right = left + cWidth
                val bottom = top + maxCellHeight
                val rect = RectF(left, top, right, bottom)

                // Background shading safely wrapped in try-catch
                val cellColorHex = try { cell.color } catch (_: Throwable) { null }
                if (rowIndex == 0) {
                    canvas.drawRect(rect, headerBgPaint)
                } else if (!cellColorHex.isNullOrBlank() && cellColorHex != "auto") {
                    try {
                        cellBgPaint.color = Color.parseColor("#$cellColorHex")
                        canvas.drawRect(rect, cellBgPaint)
                    } catch (_: Exception) {}
                }

                // Cell border
                canvas.drawRect(rect, borderPaint)

                // Render text
                if (colIndex < cellLayouts.size) {
                    val (layout, _) = cellLayouts[colIndex]
                    canvas.save()
                    canvas.translate(left + 6f, top + 6f)
                    layout.draw(canvas)
                    canvas.restore()
                }
            }

            currentY += maxCellHeight
        }

        return currentY + 12f
    }

    // =========================================================================
    // 2. FALLBACK OOXML ZIP CANVAS ENGINE
    // =========================================================================
    private fun renderDocxWithZipFallback(bytes: ByteArray): ByteArray {
        var docXml = ""
        var relsXml = ""
        val mediaMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "word/document.xml" -> docXml = readEntryBytes(zip).toString(Charsets.UTF_8)
                    name == "word/_rels/document.xml.rels" -> relsXml = readEntryBytes(zip).toString(Charsets.UTF_8)
                    name.startsWith("word/media/") -> {
                        val fileName = name.substringAfter("word/media/")
                        mediaMap[fileName] = readEntryBytes(zip)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val pdfDocument = PdfDocument()
        val engine = CanvasZipRenderEngine(pdfDocument, mediaMap, relsXml)
        engine.renderDocument(docXml)

        val baos = ByteArrayOutputStream()
        pdfDocument.writeTo(baos)
        pdfDocument.close()
        return baos.toByteArray()
    }

    private fun readEntryBytes(zip: ZipInputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var len: Int
        while (zip.read(buffer).also { len = it } != -1) {
            baos.write(buffer, 0, len)
        }
        return baos.toByteArray()
    }

    private inner class CanvasZipRenderEngine(
        private val pdfDocument: PdfDocument,
        private val mediaMap: Map<String, ByteArray>,
        private val relsXml: String
    ) {
        private val relsMap = parseRels(relsXml)
        private var pageNumber = 1
        private var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        private var currentPage = pdfDocument.startPage(pageInfo)
        private var canvas: Canvas = currentPage.canvas
        private var currentY = marginTop

        fun renderDocument(docXmlStr: String) {
            canvas.drawColor(Color.WHITE)
            if (docXmlStr.isBlank()) {
                pdfDocument.finishPage(currentPage)
                return
            }

            try {
                val db = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                val doc = db.parse(ByteArrayInputStream(docXmlStr.toByteArray(Charsets.UTF_8)))
                val body = doc.getElementsByTagNameNS("*", "body").item(0) ?: return

                var child = body.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        when (child.localName) {
                            "p" -> renderParagraph(child as Element)
                            "tbl" -> renderTable(child as Element)
                        }
                    }
                    child = child.nextSibling
                }
            } catch (_: Exception) {}

            pdfDocument.finishPage(currentPage)
        }

        private fun checkNewPage(neededHeight: Float) {
            if (currentY + neededHeight > pageHeight - marginBottom) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                canvas.drawColor(Color.WHITE)
                currentY = marginTop
            }
        }

        private fun renderParagraph(pElem: Element) {
            val textBuilder = StringBuilder()
            var isBold = false
            var isItalic = false
            var fontColor = Color.parseColor("#1F2937")
            var fontSizePt = 11f
            var styleName = ""
            var rEmbedId: String? = null

            val pPr = getChildElement(pElem, "pPr")
            if (pPr != null) {
                val pStyle = getChildElement(pPr, "pStyle")
                if (pStyle != null) {
                    styleName = pStyle.getAttribute("w:val").lowercase()
                }
            }

            var align = Layout.Alignment.ALIGN_NORMAL
            if (pPr != null) {
                val jc = getChildElement(pPr, "jc")
                if (jc != null) {
                    when (jc.getAttribute("w:val")) {
                        "center" -> align = Layout.Alignment.ALIGN_CENTER
                        "right" -> align = Layout.Alignment.ALIGN_OPPOSITE
                    }
                }
            }

            val runs = pElem.getElementsByTagNameNS("*", "r")
            for (i in 0 until runs.length) {
                val rElem = runs.item(i) as Element
                val rPr = getChildElement(rElem, "rPr")
                if (rPr != null) {
                    if (getChildElement(rPr, "b") != null) isBold = true
                    if (getChildElement(rPr, "i") != null) isItalic = true

                    val colorElem = getChildElement(rPr, "color")
                    if (colorElem != null) {
                        val valHex = colorElem.getAttribute("w:val")
                        if (valHex.length == 6) {
                            try { fontColor = Color.parseColor("#$valHex") } catch (_: Exception) {}
                        }
                    }

                    val szElem = getChildElement(rPr, "sz")
                    if (szElem != null) {
                        val halfPts = szElem.getAttribute("w:val").toFloatOrNull() ?: 22f
                        fontSizePt = halfPts / 2f
                    }
                }

                val tElem = getChildElement(rElem, "t")
                if (tElem != null) {
                    textBuilder.append(tElem.textContent)
                }

                val blip = getChildElementDeep(rElem, "blip")
                if (blip != null) {
                    rEmbedId = blip.getAttribute("r:embed")
                }
            }

            if (rEmbedId != null) {
                val fileName = relsMap[rEmbedId]
                if (fileName != null) {
                    val imageBytes = mediaMap[fileName]
                    if (imageBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
                            val targetW = Math.min(contentWidth, bitmap.width.toFloat() * 0.75f)
                            val targetH = targetW * aspect

                            checkNewPage(targetH + 12f)
                            val left = marginLeft + (contentWidth - targetW) / 2f
                            canvas.drawBitmap(bitmap, null, RectF(left, currentY, left + targetW, currentY + targetH), null)
                            currentY += targetH + 12f
                        }
                    }
                }
            }

            val text = textBuilder.toString()
            if (text.isBlank()) {
                currentY += 6f
                return
            }

            val isHeading = styleName.contains("heading") || styleName.contains("title")
            if (isHeading) {
                isBold = true
                if (styleName.contains("1") || styleName.contains("title")) fontSizePt = 20f
                else if (styleName.contains("2")) fontSizePt = 16f
                else fontSizePt = 13.5f
                fontColor = Color.parseColor("#1E3A8A")
            }

            val textPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = fontSizePt
                color = fontColor
                isFakeBoldText = isBold
                if (isItalic) textSkewX = -0.25f
            }

            val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth.toInt())
                .setAlignment(align)
                .setLineSpacing(2f, 1.15f)
                .build()

            checkNewPage(layout.height + 10f)

            if (styleName.contains("heading1") || styleName.contains("heading 1")) {
                val barPaint = Paint().apply {
                    color = Color.parseColor("#2563EB")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(marginLeft, currentY, marginLeft + 4f, currentY + layout.height, barPaint)
            }

            canvas.save()
            val drawX = if (styleName.contains("heading1") || styleName.contains("heading 1")) marginLeft + 8f else marginLeft
            canvas.translate(drawX, currentY)
            layout.draw(canvas)
            canvas.restore()

            currentY += layout.height + if (isHeading) 10f else 4f
        }

        private fun renderTable(tblElem: Element) {
            val rows = tblElem.getElementsByTagNameNS("*", "tr")
            if (rows.length == 0) return

            // Calculate actual max column count dynamically
            var maxCols = 1
            for (r in 0 until rows.length) {
                val tr = rows.item(r) as Element
                var cellCount = 0
                var child = tr.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE && child.localName == "tc") {
                        cellCount++
                    }
                    child = child.nextSibling
                }
                if (cellCount > maxCols) maxCols = cellCount
            }

            // Inspect tblGrid for explicit column widths in twips
            val colWidths = FloatArray(maxCols) { contentWidth / maxCols.toFloat() }
            val tblGrid = getChildElement(tblElem, "tblGrid")
            if (tblGrid != null) {
                val gridCols = tblGrid.getElementsByTagNameNS("*", "gridCol")
                var totalTwips = 0f
                val twipWidths = FloatArray(maxCols)
                for (g in 0 until gridCols.length.coerceAtMost(maxCols)) {
                    val gElem = gridCols.item(g) as Element
                    val wAttr = gElem.getAttribute("w:w").toFloatOrNull() ?: 0f
                    twipWidths[g] = wAttr
                    totalTwips += wAttr
                }
                if (totalTwips > 0) {
                    for (c in 0 until maxCols) {
                        val w = if (c < gridCols.length && twipWidths[c] > 0) twipWidths[c] else (totalTwips / maxCols)
                        colWidths[c] = (w / totalTwips) * contentWidth
                    }
                }
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val headerBg = Paint().apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            val cellBg = Paint().apply { style = Paint.Style.FILL }

            for (r in 0 until rows.length) {
                val trElem = rows.item(r) as Element
                val cellNodes = mutableListOf<Element>()
                var child = trElem.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE && child.localName == "tc") {
                        cellNodes.add(child as Element)
                    }
                    child = child.nextSibling
                }

                var maxCellHeight = 24f
                val layouts = mutableListOf<Pair<StaticLayout, Float>>()

                for ((c, tcElem) in cellNodes.withIndex()) {
                    val cWidth = if (c < colWidths.size) colWidths[c] else (contentWidth / maxCols)
                    val text = tcElem.textContent ?: ""
                    val textPaint = TextPaint().apply {
                        isAntiAlias = true
                        textSize = 10.5f
                        color = Color.parseColor("#1F2937")
                        if (r == 0) isFakeBoldText = true
                    }
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (cWidth - 12f).toInt().coerceAtLeast(10)).build()
                    if (layout.height + 12f > maxCellHeight) maxCellHeight = layout.height + 12f

                    var currentLeft = 0f
                    for (k in 0 until c) {
                        currentLeft += if (k < colWidths.size) colWidths[k] else (contentWidth / maxCols)
                    }
                    layouts.add(Pair(layout, currentLeft))
                }

                checkNewPage(maxCellHeight + 2f)

                for ((c, tcElem) in cellNodes.withIndex()) {
                    var left = marginLeft
                    for (k in 0 until c) {
                        left += if (k < colWidths.size) colWidths[k] else (contentWidth / maxCols)
                    }
                    val cWidth = if (c < colWidths.size) colWidths[c] else (contentWidth / maxCols)

                    val top = currentY
                    val right = left + cWidth
                    val bottom = top + maxCellHeight
                    val rect = RectF(left, top, right, bottom)

                    // Check cell background shading
                    val tcPr = getChildElement(tcElem, "tcPr")
                    var cellFillHex: String? = null
                    if (tcPr != null) {
                        val shd = getChildElement(tcPr, "shd")
                        if (shd != null) {
                            val fill = shd.getAttribute("w:fill")
                            if (fill.length == 6 && fill != "auto") cellFillHex = "#$fill"
                        }
                    }

                    if (r == 0) {
                        canvas.drawRect(rect, headerBg)
                    } else if (cellFillHex != null) {
                        try {
                            cellBg.color = Color.parseColor(cellFillHex)
                            canvas.drawRect(rect, cellBg)
                        } catch (_: Exception) {}
                    }

                    canvas.drawRect(rect, borderPaint)

                    if (c < layouts.size) {
                        val (layout, _) = layouts[c]
                        canvas.save()
                        canvas.translate(left + 6f, top + 6f)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                }

                currentY += maxCellHeight
            }

            currentY += 12f
        }

        private fun getChildElement(parent: Element, name: String): Element? {
            var child = parent.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE && child.localName == name) {
                    return child as Element
                }
                child = child.nextSibling
            }
            return null
        }

        private fun getChildElementDeep(parent: Element, name: String): Element? {
            val list = parent.getElementsByTagNameNS("*", name)
            return if (list.length > 0) list.item(0) as Element else null
        }

        private fun parseRels(relsXmlStr: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            if (relsXmlStr.isBlank()) return map
            try {
                val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val doc = db.parse(ByteArrayInputStream(relsXmlStr.toByteArray(Charsets.UTF_8)))
                val rels = doc.getElementsByTagName("Relationship")
                for (i in 0 until rels.length) {
                    val elem = rels.item(i) as Element
                    val id = elem.getAttribute("Id")
                    val target = elem.getAttribute("Target")
                    if (target.contains("media/")) {
                        map[id] = target.substringAfter("media/")
                    }
                }
            } catch (_: Exception) {}
            return map
        }
    }
}

