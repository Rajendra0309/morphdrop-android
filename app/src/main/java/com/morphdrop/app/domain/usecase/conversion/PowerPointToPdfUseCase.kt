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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

class PowerPointToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    // 16:9 Landscape PDF dimensions in points default
    private val defaultSlideWidth = 960
    private val defaultSlideHeight = 540

    // Standard PowerPoint EMU dimensions (9144000 x 5143500)
    private val defaultEmuWidth = 9144000f
    private val defaultEmuHeight = 5143500f

    suspend operator fun invoke(
        inputUri: Uri,
        outputFileName: String = "ppt_${System.currentTimeMillis()}.pdf"
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
            renderPptxWithPoi(bytes)
        } catch (e: Throwable) {
            // Fallback to Native OOXML Zip Canvas Engine if POI fails
            renderPptxWithZipFallback(bytes)
        }

        FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, pdfBytes)
    }

    // =========================================================================
    // 1. APACHE POI HIGH-FIDELITY PPTX ENGINE
    // =========================================================================
    private fun renderPptxWithPoi(bytes: ByteArray): ByteArray {
        val slideShow = XMLSlideShow(ByteArrayInputStream(bytes))
        val pdfDocument = PdfDocument()

        val pageSize = slideShow.pageSize
        val slideWidth = if (pageSize != null && pageSize.width > 0) pageSize.width else defaultSlideWidth
        val slideHeight = if (pageSize != null && pageSize.height > 0) pageSize.height else defaultSlideHeight

        val slides = slideShow.slides
        for ((index, slide) in slides.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(slideWidth, slideHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            try {
                // Render background color if present, else default white
                var bgDrawn = false
                try {
                    val bg = slide.background
                    if (bg != null) {
                        val bgFillInt = parsePoiColor(bg.fillColor)
                        if (bgFillInt != null) {
                            canvas.drawColor(bgFillInt)
                            bgDrawn = true
                        }
                    }
                } catch (_: Throwable) {}

                if (!bgDrawn) {
                    canvas.drawColor(Color.WHITE)
                }

                // Render shapes recursively with per-shape safety
                for (shape in slide.shapes) {
                    try {
                        renderPoiShape(canvas, shape)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                canvas.drawColor(Color.WHITE)
            } finally {
                pdfDocument.finishPage(page)
            }
        }

        val baos = ByteArrayOutputStream()
        pdfDocument.writeTo(baos)
        pdfDocument.close()
        try { slideShow.close() } catch (_: Exception) {}

        return baos.toByteArray()
    }

    private fun parsePoiColor(colorObj: Any?): Int? {
        if (colorObj == null) return null
        try {
            if (colorObj is java.awt.Color) {
                return Color.argb(colorObj.alpha, colorObj.red, colorObj.green, colorObj.blue)
            }
            if (colorObj is org.apache.poi.sl.usermodel.PaintStyle.SolidPaint) {
                val c = colorObj.solidColor.color
                return Color.argb(c.alpha, c.red, c.green, c.blue)
            }
            if (colorObj is org.apache.poi.sl.usermodel.ColorStyle) {
                val c = colorObj.color
                if (c != null) {
                    return Color.argb(c.alpha, c.red, c.green, c.blue)
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun renderPoiShape(canvas: Canvas, shape: XSLFShape) {
        when (shape) {
            is XSLFPictureShape -> {
                try {
                    val anchor = shape.anchor ?: return
                    val rect = RectF(
                        anchor.x.toFloat(),
                        anchor.y.toFloat(),
                        (anchor.x + anchor.width).toFloat(),
                        (anchor.y + anchor.height).toFloat()
                    )
                    val picData = shape.pictureData?.data
                    if (picData != null) {
                        val bitmap = BitmapFactory.decodeByteArray(picData, 0, picData.size)
                        if (bitmap != null) {
                            canvas.drawBitmap(bitmap, null, rect, null)
                        }
                    }
                } catch (_: Throwable) {}
            }
            is XSLFTable -> {
                try {
                    val anchor = shape.anchor ?: return
                    val left = anchor.x.toFloat()
                    var currentY = anchor.y.toFloat()
                    val totalWidth = anchor.width.toFloat()

                    val borderPaint = Paint().apply {
                        color = Color.parseColor("#CBD5E1")
                        this.style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    val bgPaint = Paint().apply { this.style = Paint.Style.FILL }

                    val numCols = shape.numberOfColumns
                    val colW = if (numCols > 0) totalWidth / numCols.toFloat() else totalWidth

                    for ((rIdx, row) in shape.rows.withIndex()) {
                        val rowHeight = row.height.toFloat().coerceAtLeast(20f)

                        for ((cIdx, cell) in row.cells.withIndex()) {
                            val cLeft = left + (cIdx * colW)
                            val cTop = currentY
                            val cRight = cLeft + colW
                            val cBottom = cTop + rowHeight
                            val rect = RectF(cLeft, cTop, cRight, cBottom)

                            val cellColorInt = parsePoiColor(cell.fillColor)
                            if (rIdx == 0) {
                                bgPaint.color = Color.parseColor("#F1F5F9")
                                canvas.drawRect(rect, bgPaint)
                            } else if (cellColorInt != null) {
                                bgPaint.color = cellColorInt
                                canvas.drawRect(rect, bgPaint)
                            }

                            canvas.drawRect(rect, borderPaint)

                            val text = cell.text ?: ""
                            if (text.isNotBlank()) {
                                val textPaint = TextPaint().apply {
                                    isAntiAlias = true
                                    textSize = 11f
                                    color = if (rIdx == 0) Color.parseColor("#0F172A") else Color.parseColor("#334155")
                                    if (rIdx == 0) isFakeBoldText = true
                                }
                                val layout = StaticLayout.Builder.obtain(
                                    text, 0, text.length, textPaint, (colW - 8f).toInt().coerceAtLeast(10)
                                ).build()

                                canvas.save()
                                canvas.translate(cLeft + 4f, cTop + 4f)
                                layout.draw(canvas)
                                canvas.restore()
                            }
                        }

                        currentY += rowHeight
                    }
                } catch (_: Throwable) {}
            }
            is XSLFTextShape -> {
                try {
                    val anchor = shape.anchor
                    val rect = if (anchor != null) {
                        RectF(
                            anchor.x.toFloat(),
                            anchor.y.toFloat(),
                            (anchor.x + anchor.width).toFloat(),
                            (anchor.y + anchor.height).toFloat()
                        )
                    } else {
                        val textType = try { shape.textType } catch (_: Throwable) { null }
                        val typeName = textType?.name?.lowercase() ?: ""
                        when {
                            typeName.contains("title") -> RectF(40f, 30f, (defaultSlideWidth - 40).toFloat(), 120f)
                            typeName.contains("subtitle") -> RectF(40f, 135f, (defaultSlideWidth - 40).toFloat(), 200f)
                            typeName.contains("body") || typeName.contains("other") -> RectF(40f, 130f, (defaultSlideWidth - 40).toFloat(), (defaultSlideHeight - 40).toFloat())
                            else -> RectF(40f, 40f, (defaultSlideWidth - 40).toFloat(), (defaultSlideHeight - 40).toFloat())
                        }
                    }

                    val fillColorInt = parsePoiColor(shape.fillColor)
                    if (fillColorInt != null) {
                        val fillPaint = Paint().apply {
                            this.style = Paint.Style.FILL
                            color = fillColorInt
                        }
                        canvas.drawRoundRect(rect, 4f, 4f, fillPaint)
                    }

                    val paragraphs = shape.textParagraphs
                    var textY = rect.top + 6f

                    for (p in paragraphs) {
                        val (spans, textPaint, alignment) = buildParagraphSpans(p)
                        if (spans.isEmpty()) {
                            textY += 10f
                            continue
                        }

                        val availW = (rect.width() - 8f).toInt().coerceAtLeast(10)
                        val layout = StaticLayout.Builder.obtain(
                            spans, 0, spans.length, textPaint, availW
                        ).setAlignment(alignment).build()

                        canvas.save()
                        val drawX = when (alignment) {
                            Layout.Alignment.ALIGN_CENTER -> rect.left + (rect.width() - layout.width) / 2f
                            Layout.Alignment.ALIGN_OPPOSITE -> rect.right - layout.width - 4f
                            else -> rect.left + 4f
                        }
                        canvas.translate(drawX, textY)
                        layout.draw(canvas)
                        canvas.restore()

                        textY += layout.height + 4f
                    }
                } catch (_: Throwable) {}
            }
            is XSLFGroupShape -> {
                try {
                    for (subShape in shape.shapes) {
                        renderPoiShape(canvas, subShape)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun buildParagraphSpans(p: XSLFTextParagraph): Triple<CharSequence, TextPaint, Layout.Alignment> {
        val builder = SpannableStringBuilder()
        var defaultFontSize = 14f
        var defaultColor = Color.parseColor("#1F2937")

        val alignment = when (p.textAlign) {
            org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        if (p.isBullet) {
            builder.append("• ")
        }

        val runs = p.textRuns
        if (runs.isNotEmpty()) {
            for (r in runs) {
                val start = builder.length
                val text = r.rawText ?: ""
                builder.append(text)
                val end = builder.length

                val fontSize = try { (r.fontSize ?: 14.0).toFloat() } catch (_: Throwable) { 14f }
                val fontColorInt = parsePoiColor(r.fontColor) ?: defaultColor
                val isBold = try { r.isBold } catch (_: Throwable) { false }
                val isItalic = try { r.isItalic } catch (_: Throwable) { false }

                builder.setSpan(AbsoluteSizeSpan(fontSize.toInt(), true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(fontColorInt), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                var style = Typeface.NORMAL
                if (isBold && isItalic) style = Typeface.BOLD_ITALIC
                else if (isBold) style = Typeface.BOLD
                else if (isItalic) style = Typeface.ITALIC

                if (style != Typeface.NORMAL) {
                    builder.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else {
            builder.append(p.text ?: "")
        }

        val tp = TextPaint().apply {
            isAntiAlias = true
            textSize = defaultFontSize
            color = defaultColor
        }

        return Triple(builder, tp, alignment)
    }

    private fun renderPptxWithZipFallback(bytes: ByteArray): ByteArray {
        val pptData = extractPptxData(bytes)
        val pdfDocument = PdfDocument()

        for ((index, slide) in pptData.slides.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(defaultSlideWidth, defaultSlideHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val engine = SlideZipCanvasRenderer(canvas, slide, pptData.mediaMap)
            engine.renderSlide()

            pdfDocument.finishPage(page)
        }

        val baos = ByteArrayOutputStream()
        pdfDocument.writeTo(baos)
        pdfDocument.close()
        return baos.toByteArray()
    }

    private data class SlideData(val slideXml: String, val relsXml: String)
    private data class PptxData(val slides: List<SlideData>, val mediaMap: Map<String, ByteArray>)

    private fun extractPptxData(bytes: ByteArray): PptxData {
        val slideXmlMap = mutableMapOf<Int, String>()
        val relsXmlMap = mutableMapOf<Int, String>()
        val mediaMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val rawName = entry.name.replace('\\', '/')
                val cleanName = rawName.lowercase()
                when {
                    cleanName.contains("ppt/slides/slide") && cleanName.endsWith(".xml") && !cleanName.contains("_rels") -> {
                        val numDigits = cleanName.substringAfter("slide").filter { it.isDigit() }
                        val num = numDigits.toIntOrNull() ?: (slideXmlMap.size + 1)
                        slideXmlMap[num] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    cleanName.contains("ppt/slides/_rels/slide") && cleanName.endsWith(".xml.rels") -> {
                        val numDigits = cleanName.substringAfter("slide").filter { it.isDigit() }
                        val num = numDigits.toIntOrNull() ?: (relsXmlMap.size + 1)
                        relsXmlMap[num] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    cleanName.contains("ppt/media/") -> {
                        val fileName = rawName.substringAfterLast("/")
                        mediaMap[fileName] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val sortedIndices = slideXmlMap.keys.sorted()
        val slides = sortedIndices.map { idx ->
            SlideData(slideXml = slideXmlMap[idx] ?: "", relsXml = relsXmlMap[idx] ?: "")
        }
        return PptxData(slides, mediaMap)
    }

    private data class Quad(val x: Float, val y: Float, val w: Float, val h: Float)

    private inner class SlideZipCanvasRenderer(
        private val canvas: Canvas,
        private val slide: SlideData,
        private val mediaMap: Map<String, ByteArray>
    ) {
        private val relsMap = parseRels(slide.relsXml)
        private val bgPaint = Paint().apply { style = Paint.Style.FILL }

        fun renderSlide() {
            canvas.drawColor(Color.WHITE)
            if (slide.slideXml.isBlank()) return

            try {
                val db = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                val doc = db.parse(ByteArrayInputStream(slide.slideXml.toByteArray(Charsets.UTF_8)))

                val spTree = doc.getElementsByTagNameNS("*", "spTree").item(0) ?: return

                var child = spTree.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        processElement(child as Element)
                    }
                    child = child.nextSibling
                }
            } catch (_: Exception) {}
        }

        private fun processElement(elem: Element) {
            when (elem.localName) {
                "sp" -> renderShape(elem)
                "pic" -> renderPicture(elem)
                "grpSp" -> renderGroupShape(elem)
                "graphicFrame" -> renderGraphicFrame(elem)
            }
        }

        private fun renderGroupShape(grpElem: Element) {
            var child = grpElem.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    processElement(child as Element)
                }
                child = child.nextSibling
            }
        }

        private fun renderGraphicFrame(frameElem: Element) {
            val (rect, _, _) = parseTransform(frameElem)
            val tbl = getChildElementDeep(frameElem, "tbl") ?: return
            val rows = tbl.getElementsByTagNameNS("*", "tr")
            if (rows.length == 0) return

            var currentY = rect.top
            val borderPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            for (r in 0 until rows.length) {
                val tr = rows.item(r) as Element
                val cells = tr.getElementsByTagNameNS("*", "tc")
                if (cells.length == 0) continue
                val colW = rect.width() / cells.length.toFloat()
                var maxCellH = 20f

                for (c in 0 until cells.length) {
                    val tc = cells.item(c) as Element
                    val cLeft = rect.left + c * colW
                    val cRight = cLeft + colW

                    val txBody = getChildElement(tc, "txBody")
                    val cellText = if (txBody != null) txBody.textContent.trim() else ""
                    val cRect = RectF(cLeft, currentY, cRight, currentY + 25f)
                    
                    if (r == 0) {
                        bgPaint.color = Color.parseColor("#F1F5F9")
                        canvas.drawRect(cRect, bgPaint)
                    }
                    canvas.drawRect(cRect, borderPaint)

                    if (cellText.isNotBlank()) {
                        val tp = TextPaint().apply {
                            isAntiAlias = true
                            textSize = 11f
                            color = Color.parseColor("#1F2937")
                        }
                        val layout = StaticLayout.Builder.obtain(cellText, 0, cellText.length, tp, (colW - 8f).toInt().coerceAtLeast(10)).build()
                        canvas.save()
                        canvas.translate(cLeft + 4f, currentY + 4f)
                        layout.draw(canvas)
                        canvas.restore()
                        if (layout.height + 8f > maxCellH) maxCellH = layout.height + 8f
                    }
                }
                currentY += maxCellH
            }
        }

        private fun renderShape(spElem: Element) {
            val (rect, _, _) = parseTransform(spElem)

            val solidFill = getChildElementDeep(spElem, "solidFill")
            if (solidFill != null) {
                val hex = getSrgbColorHex(solidFill)
                if (hex != null) {
                    try {
                        bgPaint.color = Color.parseColor(hex)
                        canvas.drawRect(rect, bgPaint)
                    } catch (_: Exception) {}
                }
            }

            val txBody = getChildElement(spElem, "txBody") ?: return
            val paragraphs = txBody.getElementsByTagNameNS("*", "p")

            var currentY = rect.top + 6f
            for (i in 0 until paragraphs.length) {
                val pElem = paragraphs.item(i) as Element
                val (text, textPaint, alignment) = parseParagraphStyle(pElem)
                if (text.isBlank()) {
                    currentY += 12f
                    continue
                }

                val availW = (rect.width() - 12f).coerceAtLeast(10f)
                val layout = StaticLayout.Builder.obtain(
                    text, 0, text.length, textPaint, availW.toInt()
                ).setAlignment(alignment).build()

                if (currentY + layout.height <= rect.bottom + 20f) {
                    canvas.save()
                    val drawX = when (alignment) {
                        Layout.Alignment.ALIGN_CENTER -> rect.left + (rect.width() - layout.width) / 2f
                        Layout.Alignment.ALIGN_OPPOSITE -> rect.right - layout.width - 6f
                        else -> rect.left + 6f
                    }
                    canvas.translate(drawX, currentY)
                    layout.draw(canvas)
                    canvas.restore()

                    currentY += layout.height + 4f
                }
            }
        }

        private fun renderPicture(picElem: Element) {
            val (rect, _, _) = parseTransform(picElem)

            val blip = getChildElementDeep(picElem, "blip") ?: return
            val rId = blip.getAttribute("r:embed")
            val fileName = relsMap[rId] ?: return
            val imageBytes = mediaMap[fileName] ?: return
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return

            canvas.drawBitmap(bitmap, null, rect, null)
        }

        private fun parseTransform(elem: Element): Triple<RectF, Float, Float> {
            var xEmu: Float? = null
            var yEmu: Float? = null
            var wEmu: Float? = null
            var hEmu: Float? = null

            val spPr = getChildElement(elem, "spPr") ?: getChildElement(elem, "picPr")
            val xfrm = if (spPr != null) getChildElement(spPr, "xfrm") else getChildElement(elem, "xfrm")

            if (xfrm != null) {
                val off = getChildElement(xfrm, "off")
                if (off != null) {
                    xEmu = off.getAttribute("x").toFloatOrNull()
                    yEmu = off.getAttribute("y").toFloatOrNull()
                }
                val ext = getChildElement(xfrm, "ext")
                if (ext != null) {
                    wEmu = ext.getAttribute("cx").toFloatOrNull()
                    hEmu = ext.getAttribute("cy").toFloatOrNull()
                }
            }

            if (xEmu == null || yEmu == null || wEmu == null || hEmu == null) {
                val nvSpPr = getChildElement(elem, "nvSpPr")
                val nvPr = if (nvSpPr != null) getChildElement(nvSpPr, "nvPr") else null
                val ph = if (nvPr != null) getChildElement(nvPr, "ph") else null
                val phType = ph?.getAttribute("type") ?: ""

                val (defX, defY, defW, defH) = when (phType) {
                    "title", "ctrTitle" -> Quad(457200f, 274320f, 8229600f, 1143000f)
                    "subTitle" -> Quad(457200f, 1600000f, 8229600f, 900000f)
                    "body" -> Quad(457200f, 1400000f, 8229600f, 4500000f)
                    else -> Quad(457200f, 457200f, 8229600f, 4500000f)
                }

                if (xEmu == null) xEmu = defX
                if (yEmu == null) yEmu = defY
                if (wEmu == null) wEmu = defW
                if (hEmu == null) hEmu = defH
            }

            val scaleX = defaultSlideWidth.toFloat() / defaultEmuWidth
            val scaleY = defaultSlideHeight.toFloat() / defaultEmuHeight

            val left = xEmu!! * scaleX
            val top = yEmu!! * scaleY
            val right = (xEmu!! + wEmu!!) * scaleX
            val bottom = (yEmu!! + hEmu!!) * scaleY

            return Triple(RectF(left, top, right, bottom), wEmu!!, hEmu!!)
        }

        private fun parseParagraphStyle(pElem: Element): Triple<CharSequence, TextPaint, Layout.Alignment> {
            val textBuilder = StringBuilder()
            var isBold = false
            var isItalic = false
            var fontColor = Color.parseColor("#1F2937")
            var fontSizePt = 14f

            var align = Layout.Alignment.ALIGN_NORMAL
            val pPr = getChildElement(pElem, "pPr")
            if (pPr != null) {
                val algn = pPr.getAttribute("algn")
                when (algn) {
                    "ctr" -> align = Layout.Alignment.ALIGN_CENTER
                    "r" -> align = Layout.Alignment.ALIGN_OPPOSITE
                }
            }

            val runs = pElem.getElementsByTagNameNS("*", "r")
            for (i in 0 until runs.length) {
                val rElem = runs.item(i) as Element
                val rPr = getChildElement(rElem, "rPr")
                if (rPr != null) {
                    if (rPr.getAttribute("b") == "1") isBold = true
                    if (rPr.getAttribute("i") == "1") isItalic = true

                    val szAttr = rPr.getAttribute("sz")
                    if (szAttr.isNotBlank()) {
                        fontSizePt = (szAttr.toFloatOrNull() ?: 1400f) / 100f
                    }

                    val solidFill = getChildElement(rPr, "solidFill")
                    if (solidFill != null) {
                        val hex = getSrgbColorHex(solidFill)
                        if (hex != null) {
                            try { fontColor = Color.parseColor(hex) } catch (_: Exception) {}
                        }
                    }
                }

                val tElem = getChildElement(rElem, "t")
                if (tElem != null) {
                    textBuilder.append(tElem.textContent)
                }
            }

            val tp = TextPaint().apply {
                isAntiAlias = true
                textSize = fontSizePt * 1.15f
                color = fontColor
                isFakeBoldText = isBold
                if (isItalic) textSkewX = -0.25f
            }

            return Triple(textBuilder.toString(), tp, align)
        }

        private fun getSrgbColorHex(elem: Element): String? {
            val srgbClr = getChildElement(elem, "srgbClr") ?: return null
            val valAttr = srgbClr.getAttribute("val")
            return if (valAttr.length == 6) "#$valAttr" else null
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
