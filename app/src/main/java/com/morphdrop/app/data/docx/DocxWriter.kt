package com.morphdrop.app.data.docx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One block of document content, in reading order. */
sealed interface DocxBlock {
    data class Heading(val level: Int, val text: String) : DocxBlock
    data class Paragraph(val text: String) : DocxBlock
    data object PageBreak : DocxBlock

    /** An embedded image; [bytes] is the encoded PNG or JPEG data. */
    class Image(
        val bytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int,
        val extension: String
    ) : DocxBlock {
        override fun equals(other: Any?): Boolean =
            other is Image && widthPx == other.widthPx && heightPx == other.heightPx &&
                extension == other.extension && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + widthPx
            result = 31 * result + heightPx
            result = 31 * result + extension.hashCode()
            return result
        }
    }
}

/**
 * Minimal hand-rolled Word (.docx) writer.
 *
 * A .docx file is a zip of XML parts; this writes just enough of the
 * Office Open XML package for Word and compatible editors (Google Docs,
 * LibreOffice, WPS) to open the result: content types, the package
 * relationship, the document body and a small styles part with
 * Normal / Heading 1 / Heading 2. No external dependency is involved,
 * which keeps the app fully offline like the rest of the converters.
 */
object DocxWriter {

    /** EMU per inch (Office Open XML drawing units). */
    private const val EMU_PER_INCH = 914400L

    /** Images are never wider than the text column (6in on A4 with margins). */
    private const val MAX_IMAGE_WIDTH_EMU = 6L * EMU_PER_INCH

    fun write(blocks: List<DocxBlock>): ByteArray {
        val images = blocks.filterIsInstance<DocxBlock.Image>()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putPart("[Content_Types].xml", contentTypes(images))
            zip.putPart("_rels/.rels", PACKAGE_RELS)
            zip.putPart("word/document.xml", documentXml(blocks))
            zip.putPart("word/styles.xml", STYLES_XML)
            zip.putPart("word/_rels/document.xml.rels", documentRels(images))
            images.forEachIndexed { index, image ->
                zip.putNextEntry(ZipEntry("word/media/image${index + 1}.${image.extension}"))
                zip.write(image.bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.putPart(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun documentXml(blocks: List<DocxBlock>): String {
        val body = StringBuilder()
        var imageIndex = 0
        for (block in blocks) {
            when (block) {
                is DocxBlock.Heading -> {
                    val style = if (block.level <= 1) "Heading1" else "Heading2"
                    body.append("<w:p><w:pPr><w:pStyle w:val=\"").append(style)
                        .append("\"/></w:pPr><w:r><w:t xml:space=\"preserve\">")
                        .append(escapeXml(block.text))
                        .append("</w:t></w:r></w:p>")
                }
                is DocxBlock.Paragraph -> {
                    body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                        .append(escapeXml(block.text))
                        .append("</w:t></w:r></w:p>")
                }
                DocxBlock.PageBreak -> {
                    body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
                is DocxBlock.Image -> {
                    imageIndex++
                    body.append(imageParagraph(block, imageIndex))
                }
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
            """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
            """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
            """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
            "<w:body>" + body +
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>""" +
            "</w:body></w:document>"
    }

    /** Inline drawing paragraph for one embedded image, sized to fit the page. */
    private fun imageParagraph(image: DocxBlock.Image, id: Int): String {
        // Natural size at 96 dpi, shrunk proportionally to the column width.
        var cx = image.widthPx.toLong() * EMU_PER_INCH / 96
        var cy = image.heightPx.toLong() * EMU_PER_INCH / 96
        if (cx > MAX_IMAGE_WIDTH_EMU && cx > 0) {
            cy = cy * MAX_IMAGE_WIDTH_EMU / cx
            cx = MAX_IMAGE_WIDTH_EMU
        }
        val relId = "rId${id + 1}"
        return "<w:p><w:r><w:drawing>" +
            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"$id\" name=\"Image $id\"/>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"image$id.${image.extension}\"/>" +
            "<pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$relId\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    }

    /** Escapes XML markup and drops characters that XML 1.0 forbids outright. */
    private fun escapeXml(text: String): String {
        val result = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> result.append("&amp;")
                '<' -> result.append("&lt;")
                '>' -> result.append("&gt;")
                '"' -> result.append("&quot;")
                '\'' -> result.append("&apos;")
                else -> if (ch.code == 0x9 || ch.code == 0xA || ch.code == 0xD || ch.code >= 0x20) {
                    result.append(ch)
                }
            }
        }
        return result.toString()
    }

    private fun contentTypes(images: List<DocxBlock.Image>): String {
        val extensions = images.map { it.extension.lowercase() }.toSet()
        val defaults = StringBuilder()
        if ("png" in extensions) {
            defaults.append("""<Default Extension="png" ContentType="image/png"/>""")
        }
        if ("jpg" in extensions || "jpeg" in extensions) {
            defaults.append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
            defaults.append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            defaults +
            """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""" +
            "</Types>"
    }

    private fun documentRels(images: List<DocxBlock.Image>): String {
        val imageRels = StringBuilder()
        images.forEachIndexed { index, image ->
            imageRels.append("<Relationship Id=\"rId").append(index + 2)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image")
                .append(index + 1).append('.').append(image.extension).append("\"/>")
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            imageRels +
            "</Relationships>"
    }

    private const val PACKAGE_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
            "</Relationships>"

    private const val STYLES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            """<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault>""" +
            """<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
            """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""" +
            """<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>""" +
            """<w:pPr><w:keepNext/><w:spacing w:before="480" w:after="120"/><w:outlineLevel w:val="0"/></w:pPr>""" +
            """<w:rPr><w:b/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style>""" +
            """<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>""" +
            """<w:pPr><w:keepNext/><w:spacing w:before="360" w:after="120"/><w:outlineLevel w:val="1"/></w:pPr>""" +
            """<w:rPr><w:b/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style>""" +
            "</w:styles>"
}
