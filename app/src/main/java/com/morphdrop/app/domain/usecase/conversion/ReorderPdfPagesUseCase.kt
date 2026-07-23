package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ReorderPdfPagesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    class InvalidPageOrderException : Exception("Provided page order is invalid or empty")

    suspend operator fun invoke(
        pdfUri: Uri,
        newOrder: List<Int>,
        outputFileName: String = "reordered_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (newOrder.isEmpty()) throw InvalidPageOrderException()

        PDFBoxResourceLoader.init(context)

        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val sourceDoc = PDDocument.load(inputStream)
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
            FileHelper.saveToCache(context, outputFileName, baos.toByteArray())
        } finally {
            newDoc.close()
            sourceDoc.close()
            inputStream.close()
        }
    }
}
