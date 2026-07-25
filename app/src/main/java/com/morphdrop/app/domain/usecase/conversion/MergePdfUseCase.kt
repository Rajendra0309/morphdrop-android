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

class MergePdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    sealed class MergeException(message: String) : Exception(message) {
        class EmptyList : MergeException("No PDF files selected to merge")
        class CorruptFile(val uri: Uri) : MergeException("Corrupt or invalid PDF file: $uri")
        class PasswordProtected(val uri: Uri) : MergeException("PDF is password-protected: $uri")
        class DiskFull : MergeException("Insufficient storage space to save merged file")
        class GeneralIO(message: String) : MergeException(message)
    }

    suspend operator fun invoke(
        pdfUris: List<Uri>,
        outputFileName: String = "merged_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (pdfUris.isEmpty()) throw MergeException.EmptyList()

        PDFBoxResourceLoader.init(context)
        val mergedDoc = PDDocument()

        try {
            for (uri in pdfUris) {
                val inputStream = try {
                    FileHelper.readFileFromUri(context, uri)
                } catch (e: Exception) {
                    throw MergeException.CorruptFile(uri)
                }

                val sourceDoc = try {
                    PDDocument.load(inputStream)
                } catch (e: Exception) {
                    inputStream.close()
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        throw MergeException.PasswordProtected(uri)
                    }
                    throw MergeException.CorruptFile(uri)
                }

                try {
                    if (sourceDoc.isEncrypted) {
                        throw MergeException.PasswordProtected(uri)
                    }
                    for (i in 0 until sourceDoc.numberOfPages) {
                        mergedDoc.importPage(sourceDoc.getPage(i))
                    }
                } finally {
                    sourceDoc.close()
                    inputStream.close()
                }
            }

            if (mergedDoc.numberOfPages == 0) throw MergeException.EmptyList()

            val baos = ByteArrayOutputStream()
            try {
                mergedDoc.save(baos)
                val folderName = settingsRepository.outputFolderName.first()
                FileHelper.saveToFile(context, folderName, outputFileName, baos.toByteArray())
            } catch (e: java.io.IOException) {
                if (e.message?.contains("ENOSPC", ignoreCase = true) == true) {
                    throw MergeException.DiskFull()
                }
                throw MergeException.GeneralIO(e.message ?: "Failed to save merged PDF")
            }
        } finally {
            mergedDoc.close()
        }
    }
}
