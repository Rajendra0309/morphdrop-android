package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class PdfToWordUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        pageRange: IntRange? = null,
        outputFileName: String = "pdf_to_word_${System.currentTimeMillis()}.docx"
    ): Uri = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)

        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = PDDocument.load(inputStream)
        val wordDoc = XWPFDocument()

        try {
            val stripper = PDFTextStripper()
            if (pageRange != null) {
                stripper.startPage = pageRange.first.coerceAtLeast(1)
                stripper.endPage = pageRange.last.coerceAtMost(document.numberOfPages)
            }

            val extractedText = stripper.getText(document)
            val lines = extractedText.split("\n")

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val p = wordDoc.createParagraph()
                    val run = p.createRun()
                    run.setText(trimmed)
                    run.fontSize = 11
                    run.fontFamily = "Calibri"
                }
            }

            val baos = ByteArrayOutputStream()
            wordDoc.write(baos)
            FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        } finally {
            document.close()
            wordDoc.close()
            inputStream.close()
        }
    }
}
