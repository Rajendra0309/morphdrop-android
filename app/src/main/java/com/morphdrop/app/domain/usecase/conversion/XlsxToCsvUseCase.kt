package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.spreadsheet.SpreadsheetTables
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Converts the first worksheet of an .xlsx file into a CSV file.
 *
 * Uses the shared spreadsheet table engine (the same parser Excel to PDF
 * uses), then writes the table out with proper CSV quoting. Formulas are
 * exported as their last calculated values, which is what the file stores.
 */
class XlsxToCsvUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        xlsxUri: Uri,
        outputFileName: String = "spreadsheet_${System.currentTimeMillis()}.csv",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".csv", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.csv"
        }

        onProgress(10)
        val bytes = FileHelper.readFileFromUri(context, xlsxUri).use { it.readBytes() }
        onProgress(40)

        val fileName = FileHelper.getFileName(context, xlsxUri).lowercase()
        if (fileName.endsWith(".xls")) {
            throw IllegalArgumentException(
                "Legacy binary Excel (.xls) format is not supported. " +
                    "Please save the spreadsheet as .xlsx first."
            )
        }

        val (table, parseError) = SpreadsheetTables.parseXlsx(bytes)
        if (table.isEmpty()) {
            throw IllegalArgumentException(
                "Unable to parse spreadsheet. Please ensure the file is a valid, unencrypted .xlsx document.",
                parseError
            )
        }
        onProgress(70)

        val csv = SpreadsheetTables.toCsv(table)
        val uri = FileHelper.saveToFile(
            context,
            settingsRepository,
            sanitizedFileName,
            csv.toByteArray(Charsets.UTF_8)
        )
        onProgress(100)
        uri
    }
}
