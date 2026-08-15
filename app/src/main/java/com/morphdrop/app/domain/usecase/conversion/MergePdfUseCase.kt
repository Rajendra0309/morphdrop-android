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

data class MergePdfItem(
    val uri: Uri,
    val pageIndex: Int,
    val rotation: Int = 0
)

class MergePdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    sealed class MergeException(message: String) : Exception(message) {
        object EmptyList : MergeException("No PDF pages selected to merge")
        class CorruptFile(val uri: Uri) : MergeException("Corrupt or invalid PDF file: $uri")
        class PasswordProtected(val uri: Uri) : MergeException("PDF is password-protected: $uri")
        object DiskFull : MergeException("Insufficient storage space to save merged file")
        class GeneralIO(message: String) : MergeException(message)
    }

    suspend operator fun invoke(
        items: List<MergePdfItem>,
        outputFileName: String = "merged_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }

        if (items.isEmpty()) throw MergeException.EmptyList

        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        val mergedDoc = PDDocument()
        
        // Cache opened documents to avoid reloading the same file multiple times
        val openedDocs = mutableMapOf<Uri, PDDocument>()

        try {
            for (item in items) {
                kotlinx.coroutines.yield()
                
                val sourceDoc = openedDocs.getOrPut(item.uri) {
                    val inputStream = try {
                        FileHelper.readFileFromUri(context, item.uri)
                    } catch (e: Exception) {
                        throw MergeException.CorruptFile(item.uri)
                    }
                    
                    try {
                        PDDocument.load(inputStream).also {
                            if (it.isEncrypted) throw MergeException.PasswordProtected(item.uri)
                        }
                    } catch (e: Exception) {
                        if (e is MergeException.PasswordProtected) throw e
                        throw MergeException.CorruptFile(item.uri)
                    } finally {
                        inputStream.close()
                    }
                }

                if (item.pageIndex in 0 until sourceDoc.numberOfPages) {
                    val page = sourceDoc.getPage(item.pageIndex)
                    if (item.rotation != 0) {
                        page.rotation = (page.rotation + item.rotation) % 360
                    }
                    mergedDoc.importPage(page)
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
            openedDocs.values.forEach { it.close() }
            try { mergedDoc.close() } catch (_: Throwable) {}
        }
    }

    suspend fun legacy(uris: List<Uri>, outputFileName: String): Uri {
        // This is only for backward compatibility if needed, but we should use the new one.
        // I'll implement it by mapping all pages of each URI.
        val items = mutableListOf<MergePdfItem>()
        for (uri in uris) {
            val inputStream = FileHelper.readFileFromUri(context, uri)
            val doc = PDDocument.load(inputStream)
            for (i in 0 until doc.numberOfPages) {
                items.add(MergePdfItem(uri, i))
            }
            doc.close()
            inputStream.close()
        }
        return invoke(items, outputFileName)
    }
}
