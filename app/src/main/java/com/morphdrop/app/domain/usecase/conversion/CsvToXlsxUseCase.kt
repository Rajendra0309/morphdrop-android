package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.spreadsheet.SpreadsheetTables
import com.morphdrop.app.data.spreadsheet.XlsxWriter
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Converts a CSV file into a real .xlsx spreadsheet using the hand-rolled
 * OOXML writer. Cells that are plainly numbers become numeric cells;
 * everything else is stored as text.
 */
class CsvToXlsxUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        csvUri: Uri,
        outputFileName: String = "spreadsheet_${System.currentTimeMillis()}.xlsx",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".xlsx", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.xlsx"
        }

        onProgress(10)
        val bytes = FileHelper.readFileFromUri(context, csvUri).use { it.readBytes() }
        onProgress(40)

        val fileName = FileHelper.getFileName(context, csvUri).lowercase()
        val delimiter = if (fileName.endsWith(".tsv")) '\t' else ','
        // Strip a BOM so it never becomes part of the first cell.
        val content = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val table = SpreadsheetTables.parseDelimited(content, delimiter)
        if (table.isEmpty()) {
            throw IllegalArgumentException("This file has no rows to convert. Please choose a non-empty CSV file.")
        }
        onProgress(70)

        val uri = FileHelper.saveToFile(
            context,
            settingsRepository,
            sanitizedFileName,
            XlsxWriter.write(table)
        )
        onProgress(100)
        uri
    }
}
