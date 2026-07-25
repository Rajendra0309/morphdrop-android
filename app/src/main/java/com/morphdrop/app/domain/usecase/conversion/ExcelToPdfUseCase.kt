package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ExcelToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val MARGIN = 40f
        private const val FONT_SIZE = 9f
        private const val HEADER_FONT_SIZE = 10f
        private const val ROW_HEIGHT = 18f
    }

    suspend operator fun invoke(
        xlsxUri: Uri,
        outputFileName: String = "excel_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)

        val inputStream = FileHelper.readFileFromUri(context, xlsxUri)
        val workbook = XSSFWorkbook(inputStream)
        val pdfDoc = PDDocument()
        val formatter = DataFormatter()

        try {
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex) ?: continue
                if (sheet.physicalNumberOfRows == 0) continue

                var page = PDPage(PDRectangle.A4)
                pdfDoc.addPage(page)
                var contentStream = PDPageContentStream(pdfDoc, page)
                var yPos = PDRectangle.A4.height - MARGIN

                // Render Sheet Name Header
                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                contentStream.newLineAtOffset(MARGIN, yPos)
                contentStream.showText("Sheet: ${sheet.sheetName}")
                contentStream.endText()
                yPos -= 25f

                // Find max column index
                var maxColIndex = 0
                for (row in sheet) {
                    if (row.lastCellNum > maxColIndex) {
                        maxColIndex = row.lastCellNum.toInt()
                    }
                }
                if (maxColIndex == 0) {
                    contentStream.close()
                    continue
                }

                val usableWidth = PDRectangle.A4.width - 2 * MARGIN
                val colWidth = (usableWidth / maxColIndex).coerceAtMost(120f)

                for (row in sheet) {
                    if (yPos - ROW_HEIGHT < MARGIN) {
                        contentStream.close()
                        page = PDPage(PDRectangle.A4)
                        pdfDoc.addPage(page)
                        contentStream = PDPageContentStream(pdfDoc, page)
                        yPos = PDRectangle.A4.height - MARGIN
                    }

                    val isHeaderRow = row.rowNum == 0
                    val font = if (isHeaderRow) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                    val fontSize = if (isHeaderRow) HEADER_FONT_SIZE else FONT_SIZE

                    for (colIndex in 0 until maxColIndex) {
                        val cell = row.getCell(colIndex)
                        val text = if (cell != null) {
                            if (cell.cellType == CellType.FORMULA) {
                                try {
                                    formatter.formatCellValue(cell)
                                } catch (_: Exception) {
                                    cell.toString()
                                }
                            } else {
                                formatter.formatCellValue(cell)
                            }
                        } else ""

                        val xPos = MARGIN + (colIndex * colWidth)
                        val maxChars = (colWidth / (fontSize * 0.5f)).toInt().coerceAtLeast(1)
                        val truncatedText = if (text.length > maxChars) text.take(maxChars - 1) + "…" else text

                        contentStream.beginText()
                        contentStream.setFont(font, fontSize)
                        contentStream.newLineAtOffset(xPos, yPos)
                        contentStream.showText(truncatedText)
                        contentStream.endText()
                    }

                    yPos -= ROW_HEIGHT
                }

                contentStream.close()
            }

            if (pdfDoc.numberOfPages == 0) {
                val emptyPage = PDPage(PDRectangle.A4)
                pdfDoc.addPage(emptyPage)
            }

            val baos = ByteArrayOutputStream()
            pdfDoc.save(baos)
            FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        } finally {
            pdfDoc.close()
            workbook.close()
            inputStream.close()
        }
    }
}
