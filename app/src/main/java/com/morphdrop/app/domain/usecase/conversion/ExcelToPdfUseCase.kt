package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.pdf.TablePdfWriter
import com.morphdrop.app.data.spreadsheet.SpreadsheetTables
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ExcelToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(
        xlsxUri: Uri,
        outputFileName: String = "excel_to_pdf_${System.currentTimeMillis()}.pdf",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        onProgress(10)
        val inputStream = FileHelper.readFileFromUri(context, xlsxUri)
        val bytes = inputStream.readBytes()
        try { inputStream.close() } catch (_: Exception) {}
        onProgress(30)

        val fileName = FileHelper.getFileName(context, xlsxUri).lowercase()
        val isCsv = fileName.endsWith(".csv")
        val isTsv = fileName.endsWith(".tsv")

        if (isCsv) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = ",")
        }

        if (isTsv) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = "\t")
        }

        if (fileName.endsWith(".xls")) {
            throw IllegalArgumentException("Legacy binary Excel (.xls) format is not supported. Please convert or save the spreadsheet as modern .xlsx or .csv format.")
        }

        // Native OOXML ZIP parser for .xlsx (lightweight, zero external dependencies)
        val (table, parseError) = SpreadsheetTables.parseXlsx(bytes)
        if (table.isNotEmpty()) {
            return@withContext renderTableToPdf(table, sanitizedFileName, onProgress)
        }

        // Fall back to delimited text parsing for .tsv or .txt files
        if (fileName.endsWith(".tsv")) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = "\t")
        }
        if (fileName.endsWith(".txt")) {
            val sample = String(bytes.take(2048).toByteArray(), Charsets.UTF_8)
            val delimiter = if (sample.contains("\t")) "\t" else ","
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = delimiter)
        }

        throw IllegalArgumentException(
            "Unable to parse spreadsheet. Please ensure the file is a valid, unencrypted .xlsx document.",
            parseError
        )
    }

    private suspend fun convertCsvToPdf(
        bytes: ByteArray,
        outputFileName: String,
        onProgress: (Int) -> Unit,
        delimiter: String = ","
    ): Uri {
        // Strip a BOM so it never becomes part of the first cell, then parse with a
        // proper quoted-field parser: handles "Smith, John",42, embedded newlines in
        // quoted fields, and "" escaped quotes. Intentional blank rows are preserved.
        val contentStr = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val table = SpreadsheetTables.parseDelimited(contentStr, delimiter.firstOrNull() ?: ',')
        return renderTableToPdf(table, outputFileName, onProgress)
    }

    private suspend fun renderTableToPdf(
        table: List<List<String>>,
        outputFileName: String,
        onProgress: (Int) -> Unit
    ): Uri {
        // PDFBox writer: the standard fonts keep a correct text layer
        // (the platform PdfDocument renderer encoded '.' as NUL).
        val baos = ByteArrayOutputStream()
        TablePdfWriter.write(table, baos)
        onProgress(95)
        val uri = FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        onProgress(100)
        return uri
    }
}
