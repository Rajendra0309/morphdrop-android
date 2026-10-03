package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ReorderPdfPagesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class InvalidPageOrderException : Exception("Provided page order is invalid or empty")

    suspend operator fun invoke(
        pdfUri: Uri,
        newOrder: List<Int>,
        outputFileName: String = "reordered_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (newOrder.isEmpty()) throw InvalidPageOrderException()

        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }

        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val sourceDoc = try {
            PDDocument.load(inputStream)
        } catch (e: Exception) {
            try { inputStream.close() } catch (_: Exception) {}
            throw e
        }
        val newDoc = PDDocument()

        try {
            val totalPages = sourceDoc.numberOfPages
            for (pageIndex in newOrder) {
                if (pageIndex in 0 until totalPages) {
                    newDoc.importPage(sourceDoc.getPage(pageIndex))
                }
            }

            if (newDoc.numberOfPages == 0) throw InvalidPageOrderException()

            val baos = ByteArrayOutputStream()
            newDoc.save(baos)
            FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, baos.toByteArray())
        } finally {
            newDoc.close()
            sourceDoc.close()
            inputStream.close()
        }
    }
}
