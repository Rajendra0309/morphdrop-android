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

class PdfPageEditorUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class InvalidPageOrderException : Exception("Provided page order is invalid or empty")

    suspend operator fun invoke(
        pdfUri: Uri,
        newOrder: List<Int>, // Indices 0-based
        rotations: Map<Int, Int>, // Index to degrees
        outputFileName: String = "edited_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
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
            if (newOrder.isEmpty() || newOrder.none { it in 0 until totalPages }) {
                throw InvalidPageOrderException()
            }

            // Bake form-field values into the page content (flatten) BEFORE
            // importing pages, so field appearances survive in the output.
            // Assigning the raw AcroForm across documents is unsafe (it shares
            // COS objects between documents and leaves orphan field references
            // for pages that were not imported); a failure here must not break
            // the edit itself.
            try {
                sourceDoc.documentCatalog.acroForm?.flatten()
            } catch (_: Exception) {}

            for (index in newOrder) {
                kotlinx.coroutines.yield()
                if (index in 0 until totalPages) {
                    val page = sourceDoc.getPage(index)
                    val rotation = rotations[index] ?: 0
                    // Rotate the imported copy, never the cached source page.
                    val importedPage = newDoc.importPage(page)
                    if (rotation != 0) {
                        importedPage.rotation = Math.floorMod(importedPage.rotation + rotation, 360)
                    }
                }
            }

            val baos = ByteArrayOutputStream()
            newDoc.save(baos)
            FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        } finally {
            newDoc.close()
            sourceDoc.close()
            inputStream.close()
        }
    }
}
