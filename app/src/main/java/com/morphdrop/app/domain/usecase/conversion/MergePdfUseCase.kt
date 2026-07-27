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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class MergePdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    sealed class MergeException(message: String) : Exception(message) {
        object EmptyList : MergeException("No PDF files selected to merge")
        class CorruptFile(val uri: Uri) : MergeException("Corrupt or invalid PDF file: $uri")
        class PasswordProtected(val uri: Uri) : MergeException("PDF is password-protected: $uri")
        object DiskFull : MergeException("Insufficient storage space to save merged file")
        class GeneralIO(message: String) : MergeException(message)
    }

    suspend operator fun invoke(
        pdfUris: List<Uri>,
        outputFileName: String = "merged_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }

        if (pdfUris.isEmpty()) throw MergeException.EmptyList

        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        val mergedDoc = PDDocument()
        val openSourceDocs = mutableListOf<PDDocument>()

        try {
            for (uri in pdfUris) {
                val bytes = try {
                    val inputStream = FileHelper.readFileFromUri(context, uri)
                    val b = inputStream.readBytes()
                    try { inputStream.close() } catch (_: Exception) {}
                    b
                } catch (e: Throwable) {
                    throw MergeException.CorruptFile(uri)
                }

                val sourceDoc = try {
                    PDDocument.load(ByteArrayInputStream(bytes))
                } catch (e: Throwable) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        throw MergeException.PasswordProtected(uri)
                    }
                    throw MergeException.CorruptFile(uri)
                }

                openSourceDocs.add(sourceDoc)

                if (sourceDoc.isEncrypted) {
                    throw MergeException.PasswordProtected(uri)
                }
                for (i in 0 until sourceDoc.numberOfPages) {
                    mergedDoc.importPage(sourceDoc.getPage(i))
                }
            }

            if (mergedDoc.numberOfPages == 0) throw MergeException.EmptyList

            val baos = ByteArrayOutputStream()
            try {
                mergedDoc.save(baos)
                FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, baos.toByteArray())
            } catch (e: java.io.IOException) {
                if (e.message?.contains("ENOSPC", ignoreCase = true) == true) {
                    throw MergeException.DiskFull
                }
                throw MergeException.GeneralIO(e.message ?: "Failed to save merged PDF")
            }
        } finally {
            for (doc in openSourceDocs) {
                try { doc.close() } catch (_: Throwable) {}
            }
            try { mergedDoc.close() } catch (_: Throwable) {}
        }
    }
}
