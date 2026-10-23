package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.pdf.PdfLineStripper
import com.morphdrop.app.data.pdf.PdfTableBuilder
import com.morphdrop.app.data.spreadsheet.SpreadsheetTables
import com.morphdrop.app.data.spreadsheet.XlsxWriter
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Extracts a table from a text-based PDF into CSV or XLSX. Rows and
 * columns are reconstructed from the position of the text (see
 * [PdfTableBuilder]); scanned PDFs have no text to reconstruct and are
 * rejected with a pointer to the OCR tools.
 */
class PdfToSpreadsheetUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    enum class Format(val extension: String) { CSV("csv"), XLSX("xlsx") }

    suspend operator fun invoke(
        pdfUri: Uri,
        format: Format,
        outputFileName: String = "pdf_table_${System.currentTimeMillis()}.${format.extension}",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".${format.extension}", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.${format.extension}"
        }

        onProgress(15)
        val table = FileHelper.readFileFromUri(context, pdfUri).use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PdfLineStripper()
                stripper.getText(document)
                val pages = stripper.lines
                    .groupBy { it.pageIndex }
                    .toSortedMap()
                    .values
                    .toList()
                PdfTableBuilder.build(pages)
            }
        }
        onProgress(70)

        if (table.isEmpty() || table.all { row -> row.all { it.isBlank() } }) {
            throw IllegalArgumentException(
                "No table text found in this PDF. It may be a scan — try OCR to Searchable PDF or Extract Text (OCR) first."
            )
        }

        val bytes = when (format) {
            Format.CSV -> SpreadsheetTables.toCsv(table).toByteArray(Charsets.UTF_8)
            Format.XLSX -> XlsxWriter.write(table)
        }
        val uri = FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, bytes)
        onProgress(100)
        uri
    }
}
