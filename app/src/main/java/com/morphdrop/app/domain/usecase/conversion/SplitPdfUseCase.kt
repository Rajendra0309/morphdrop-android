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
        pageOrder: List<Int>,
        selectedPages: Set<Int>,
        rotations: Map<Int, Int>,
        splitMode: String, // "selection", "every_n", "all"
        splitEveryN: Int = 1,
        outputFolderName: String? = null
    ): List<Uri> = withContext(Dispatchers.IO) {
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

        val outputUris = mutableListOf<Uri>()

        try {
            if (sourceDoc.isEncrypted) throw SplitException.PasswordProtected()
            
            val baseFolder = settingsRepository.outputFolderName.first()
            val chosenFolder = outputFolderName ?: "split_pdf_${System.currentTimeMillis()}"
            val folderName = "$baseFolder/$chosenFolder"
            FileHelper.createOutputDirectory(folderName)

            // Step 1: Filter and prepare the virtual pages based on workbench state
            val activePages = pageOrder.filter { selectedPages.contains(it) }
            if (activePages.isEmpty()) throw SplitException.InvalidRange()

            // Step 2: Determine groups of pages for each output file
            val pageGroups = when (splitMode) {
                "selection" -> listOf(activePages) // All selected pages go into ONE file
                "every_n" -> activePages.chunked(splitEveryN) // Split every N pages
                "all" -> activePages.chunked(1) // Every page is a separate file
                else -> listOf(activePages)
            }

            // Step 3: Generate the files
            for ((groupIndex, group) in pageGroups.withIndex()) {
                kotlinx.coroutines.yield()
                val splitDoc = PDDocument()
                try {
                    for (pageIndex in group) {
                        kotlinx.coroutines.yield()
                        val page = sourceDoc.getPage(pageIndex)
                        val rotation = rotations[pageIndex] ?: 0
                        if (rotation != 0) {
                            page.rotation = (page.rotation + rotation) % 360
                        }
                        splitDoc.importPage(page)
                    }
                    
                    val baos = ByteArrayOutputStream()
                    splitDoc.save(baos)
                    
                    val fileName = if (pageGroups.size == 1) {
                        "extracted_selection_${System.currentTimeMillis()}.pdf"
                    } else {
                        "split_part_${groupIndex + 1}.pdf"
                    }
                    
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
