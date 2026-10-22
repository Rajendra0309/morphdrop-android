package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.StringWriter
import javax.inject.Inject

/**
 * Extracts the embedded text layer of a PDF into a plain text file.
 *
 * This is the fast path for digital PDFs: no OCR is involved, so layout
 * order comes straight from the document. Scanned PDFs have no text layer;
 * those fail with [NoTextLayerException] so the UI can point the user at
 * the OCR tool instead of silently producing an empty file.
 */
class PdfToTextUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class NoTextLayerException :
        Exception("This PDF has no readable text — it looks like a scanned document. Use Extract Text (OCR) for scanned files.")

    class PasswordProtectedException :
        Exception("This PDF is password-protected. Unlock it first, then try again.")

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

    private suspend fun extractText(pdfUri: Uri, onProgress: (Int) -> Unit): String {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = try {
            inputStream.use { PDDocument.load(it) }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PasswordProtectedException()
        }

        return document.use { doc ->
            if (doc.isEncrypted) {
                throw PasswordProtectedException()
            }
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                wordSeparator = " "
            }
            val pageCount = doc.numberOfPages.coerceAtLeast(1)
            val builder = StringBuilder()
            for (page in 1..doc.numberOfPages) {
                yield() // Support cancellation on long documents
                stripper.startPage = page
                stripper.endPage = page
                val writer = StringWriter()
                stripper.writeText(doc, writer)
                val pageText = writer.toString().trim()
                if (pageText.isNotEmpty()) {
                    if (builder.isNotEmpty()) builder.append("\n\n")
                    builder.append(pageText)
                }
                onProgress(10 + ((page.toFloat() / pageCount) * 75).toInt())
            }
            builder.toString()
        }
    }
}
