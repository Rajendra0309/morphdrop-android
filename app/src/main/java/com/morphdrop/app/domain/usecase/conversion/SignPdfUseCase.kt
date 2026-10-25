package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Stamps a signature image onto PDF pages with PDFBox. The signature is
 * a transparent PNG (drawn in the app or picked from photos), drawn at
 * a chosen corner or the centre, sized as a percentage of page width,
 * on one page or all pages. Page content itself is never modified —
 * the signature is appended as an overlay.
 */
class SignPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val SCOPE_THIS_PAGE = "this_page"
        const val SCOPE_FIRST = "first"
        const val SCOPE_LAST = "last"
        const val SCOPE_ALL = "all"

        const val POS_BOTTOM_RIGHT = "bottom_right"
        const val POS_BOTTOM_LEFT = "bottom_left"
        const val POS_TOP_RIGHT = "top_right"
        const val POS_TOP_LEFT = "top_left"
        const val POS_CENTER = "center"

        private const val MARGIN = 36f // points from the page edge
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        signatureFilePath: String,
        scope: String,
        pageIndex: Int,
        position: String,
        widthPercent: Int,
        outputFileName: String = "signed_${System.currentTimeMillis()}.pdf",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        onProgress(10)
        val signatureFile = File(signatureFilePath)
        val signatureBitmap = BitmapFactory.decodeFile(signatureFile.absolutePath)
            ?: throw IllegalArgumentException("The signature image could not be read. Please draw or pick it again.")
        val pdfBytes = FileHelper.readFileFromUri(context, pdfUri).use { it.readBytes() }
        onProgress(30)

        val output = ByteArrayOutputStream()
        PDDocument.load(pdfBytes).use { document ->
            val pageCount = document.numberOfPages
            if (pageCount <= 0) {
                throw IllegalArgumentException("This PDF has no pages to sign.")
            }
            val image = LosslessFactory.createFromImage(document, signatureBitmap)
            val targets: List<Int> = when (scope) {
                SCOPE_ALL -> (0 until pageCount).toList()
                SCOPE_FIRST -> listOf(0)
                SCOPE_LAST -> listOf(pageCount - 1)
                else -> listOf(pageIndex.coerceIn(0, pageCount - 1))
            }
            val fraction = widthPercent.coerceIn(5, 90) / 100f
            for (index in targets) {
                val page = document.getPage(index)
                val box = page.mediaBox
                val drawWidth = box.width * fraction
                val drawHeight = drawWidth * signatureBitmap.height / signatureBitmap.width.coerceAtLeast(1)
                val x = when (position) {
                    POS_BOTTOM_LEFT, POS_TOP_LEFT -> box.lowerLeftX + MARGIN
                    POS_CENTER -> box.lowerLeftX + (box.width - drawWidth) / 2f
                    else -> box.lowerLeftX + box.width - drawWidth - MARGIN
                }
                val y = when (position) {
                    POS_TOP_LEFT, POS_TOP_RIGHT -> box.lowerLeftY + box.height - drawHeight - MARGIN
                    POS_CENTER -> box.lowerLeftY + (box.height - drawHeight) / 2f
                    else -> box.lowerLeftY + MARGIN
                }
                PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true
                ).use { stream ->
                    stream.drawImage(image, x, y, drawWidth, drawHeight)
                }
            }
            onProgress(80)
            document.save(output)
        }
        signatureBitmap.recycle()
        onProgress(95)

        val uri = FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, output.toByteArray())
        onProgress(100)
        uri
    }
}
