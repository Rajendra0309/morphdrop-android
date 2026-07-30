package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class SplitPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    sealed class SplitException(message: String) : Exception(message) {
        class InvalidRange : SplitException("No valid page ranges provided")
        class CorruptPdf : SplitException("PDF file is corrupt or unreadable")
        class PasswordProtected : SplitException("PDF is password-protected")
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        pageRanges: List<IntRange>,
        outputFolderName: String? = null
    ): List<Uri> = withContext(Dispatchers.IO) {
        if (pageRanges.isEmpty()) throw SplitException.InvalidRange()

        val outputUris = mutableListOf<Uri>()

        val inputStream = try {
            FileHelper.readFileFromUri(context, pdfUri)
        } catch (e: Exception) {
            throw SplitException.CorruptPdf()
        }

        val sourceDoc = try {
            PDDocument.load(inputStream)
        } catch (e: Exception) {
            inputStream.close()
            if (e.message?.contains("password", ignoreCase = true) == true) {
                throw SplitException.PasswordProtected()
            }
            throw SplitException.CorruptPdf()
        }

        try {
            if (sourceDoc.isEncrypted) throw SplitException.PasswordProtected()
            val totalPages = sourceDoc.numberOfPages

            val baseFolder = settingsRepository.outputFolderName.first()
            val chosenFolder = outputFolderName ?: "split_pdf_${System.currentTimeMillis()}"
            val folderName = "$baseFolder/$chosenFolder"
            FileHelper.createOutputDirectory(context, folderName)

            for ((index, range) in pageRanges.withIndex()) {
                val start = (range.first - 1).coerceAtLeast(0)
                val end = (range.last - 1).coerceAtMost(totalPages - 1)
                if (start > end) continue

                val splitDoc = PDDocument()
                try {
                    for (i in start..end) {
                        splitDoc.importPage(sourceDoc.getPage(i))
                    }
                    val baos = ByteArrayOutputStream()
                    splitDoc.save(baos)
                    val fileName = "split_part_${index + 1}_pages_${start + 1}-${end + 1}.pdf"
                    val savedUri = FileHelper.saveToDirectory(context, folderName, fileName, baos.toByteArray())
                    outputUris.add(savedUri)
                } finally {
                    splitDoc.close()
                }
            }
        } finally {
            sourceDoc.close()
            inputStream.close()
        }

        if (outputUris.isEmpty()) throw SplitException.InvalidRange()
        outputUris
    }
}
