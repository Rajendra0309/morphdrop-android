package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.pdf.PdfLineStripper
import com.morphdrop.app.data.pdf.readingOrder
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringWriter
import javax.inject.Inject

/**
 * Extracts the embedded text layer of a PDF into a plain text file.
 *
 * This is the fast path for digital PDFs: no OCR is involved. Lines are
 * extracted with their geometry and put in reading order, so two-column
 * pages read down one column and then the other instead of interleaving.
 * Scanned PDFs have no text layer; those fail with [NoTextLayerException]
 * so the UI can point the user at the OCR tool instead of silently
 * producing an empty file.
 */
class PdfToTextUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class NoTextLayerException :
        Exception("This PDF has no readable text — it looks like a scanned document. Use Extract Text (OCR) for scanned files.")

    class PasswordProtectedException :
        Exception("This PDF is password-protected. Unlock it first, then try again.")

    class CorruptFileException :
        Exception("This file appears to be damaged or is not a valid PDF.")

    suspend operator fun invoke(
        pdfUri: Uri,
        outputFileName: String = "pdf_to_text_${System.currentTimeMillis()}.txt",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".txt", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.txt"
        }

        onProgress(10)
        val text = extractText(pdfUri, onProgress)
        if (text.isBlank()) {
            throw NoTextLayerException()
        }

        onProgress(90)
        val savedUri = FileHelper.saveToFile(
            context,
            settingsRepository,
            sanitizedFileName,
            text.toByteArray(Charsets.UTF_8)
        )
        onProgress(100)
        savedUri
    }

    private fun extractText(pdfUri: Uri, onProgress: (Int) -> Unit): String {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = try {
            inputStream.use { PDDocument.load(it) }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PasswordProtectedException()
        } catch (e: java.io.IOException) {
            throw CorruptFileException()
        }

        return document.use { doc ->
            if (doc.isEncrypted) {
                throw PasswordProtectedException()
            }
            val stripper = PdfLineStripper()
            stripper.startPage = 1
            stripper.endPage = doc.numberOfPages
            try {
                stripper.writeText(doc, StringWriter())
            } catch (e: java.io.IOException) {
                throw CorruptFileException()
            }
            onProgress(85)

            // Every original page keeps its place: a page without a text
            // layer gets a marker instead of silently vanishing.
            val linesByPage = stripper.lines.groupBy { it.pageIndex }
            var hasText = false
            val pageTexts = (0 until doc.numberOfPages).map { pageIndex ->
                val pageLines = linesByPage[pageIndex].orEmpty()
                if (pageLines.isEmpty()) {
                    "[Page ${pageIndex + 1}: no readable text — this page may be a scanned image. " +
                        "Use Extract Text (OCR) to read it.]"
                } else {
                    hasText = true
                    readingOrder(pageLines).joinToString("\n") { it.text }
                }
            }
            if (!hasText) "" else pageTexts.joinToString("\n\n") + "\n"
        }
    }
}
