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

class RotatePdfPagesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        rotationDegrees: Int,
        targetPages: List<Int>? = null,
        outputFileName: String = "rotated_${System.currentTimeMillis()}.pdf",
        subFolder: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = PDDocument.load(inputStream)

        try {
            val totalPages = document.numberOfPages
            for (i in 0 until totalPages) {
                kotlinx.coroutines.yield()
                val pageNumber = i + 1
                if (targetPages == null || targetPages.contains(pageNumber)) {
                    val page = document.getPage(i)
                    val currentRotation = page.rotation
                    page.rotation = (currentRotation + rotationDegrees) % 360
                }
            }

            val baos = ByteArrayOutputStream()
            document.save(baos)
            val bytes = baos.toByteArray()

            val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
                outputFileName
            } else {
                "$outputFileName.pdf"
            }

            if (!subFolder.isNullOrBlank()) {
                FileHelper.saveToDirectory(context, subFolder, sanitizedFileName, bytes)
            } else {
                FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, bytes)
            }
        } finally {
            document.close()
            inputStream.close()
        }
    }
}
