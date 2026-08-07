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
    suspend operator fun invoke(
        pdfUri: Uri,
        newOrder: List<Int>, // Indices 0-based
        rotations: Map<Int, Int>, // Index to degrees
        outputFileName: String = "edited_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val sourceDoc = PDDocument.load(inputStream)
        val newDoc = PDDocument()

        try {
            val totalPages = sourceDoc.numberOfPages
            for (index in newOrder) {
                kotlinx.coroutines.yield()
                if (index in 0 until totalPages) {
                    val page = sourceDoc.getPage(index)
                    val rotation = rotations[index] ?: 0
                    if (rotation != 0) {
                        page.rotation = (page.rotation + rotation) % 360
                    }
                    newDoc.importPage(page)
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
