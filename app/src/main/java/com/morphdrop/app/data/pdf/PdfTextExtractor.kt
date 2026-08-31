package com.morphdrop.app.data.pdf

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import android.graphics.Bitmap

data class PdfWord(
    val text: String,
    val bounds: RectF // Normalized bounds (0f to 1f) relative to the page width/height
)

data class PdfLink(
    val uri: String,
    val bounds: RectF
)

data class PdfPageData(
    val words: List<PdfWord>,
    val links: List<PdfLink>
)

class PdfTextExtractor(private val context: Context) {

    suspend fun extractDataFromPage(uri: Uri, pageIndex: Int, password: String = "", bitmap: Bitmap? = null): PdfPageData = withContext(Dispatchers.IO) {
        val words = mutableListOf<PdfWord>()
        val links = mutableListOf<PdfLink>()
        var document: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            document = if (password.isNotEmpty()) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }

            val page = document.getPage(pageIndex)
            val cropBox = page.cropBox
            val pageWidth = cropBox.width
            val pageHeight = cropBox.height

            // Extract Links
            val annotations = page.annotations
            for (annotation in annotations) {
                if (annotation is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink) {
                    val action = annotation.action
                    if (action is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI) {
                        val rect = annotation.rectangle
                        if (rect != null) {
                            // PDF coordinates: origin is bottom-left. Android: origin is top-left.
                            val normLeft = (rect.lowerLeftX - cropBox.lowerLeftX) / pageWidth
                            val normBottom = (rect.lowerLeftY - cropBox.lowerLeftY) / pageHeight
                            val normRight = (rect.upperRightX - cropBox.lowerLeftX) / pageWidth
                            val normTop = (rect.upperRightY - cropBox.lowerLeftY) / pageHeight
                            
                            val bounds = RectF(
                                normLeft,
                                1f - normTop,
                                normRight,
                                1f - normBottom
                            )
                            links.add(PdfLink(action.uri, bounds))
                        }
                    }
                }
            }

            val rotation = page.rotation
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation.toFloat(), 0.5f, 0.5f)

            // Extract Text
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: MutableList<TextPosition>?) {
                    if (textPositions == null || textPositions.isEmpty()) return
                    
                    var currentWord = StringBuilder()
                    var currentWordBounds: RectF? = null
                    var prevPos: TextPosition? = null

                    for (pos in textPositions) {
                        val charString = pos.unicode
                        
                        // Check distance from previous char to detect spaces
                        if (prevPos != null) {
                            val distance = pos.xDirAdj - (prevPos.xDirAdj + prevPos.widthDirAdj)
                            // If distance > a fraction of font width, it's a space
                            if (distance > pos.widthOfSpace * 0.5f || charString.isBlank()) {
                                if (currentWord.isNotEmpty() && currentWordBounds != null) {
                                    words.add(PdfWord(currentWord.toString(), currentWordBounds!!))
                                    currentWord.clear()
                                    currentWordBounds = null
                                }
                            }
                        }

                        if (charString.isNotBlank()) {
                            val x = pos.xDirAdj
                            val y = pos.yDirAdj - pos.heightDir
                            val width = pos.widthDirAdj
                            val height = pos.heightDir

                            val normX = x / pageWidth
                            val normY = y / pageHeight
                            val normWidth = width / pageWidth
                            val normHeight = height / pageHeight

                            val charBounds = RectF(
                                normX,
                                normY,
                                normX + normWidth,
                                normY + normHeight
                            )
                            
                            // Apply page rotation to match visual coordinates
                            matrix.mapRect(charBounds)

                            currentWord.append(charString)
                            if (currentWordBounds == null) {
                                currentWordBounds = RectF(charBounds)
                            } else {
                                currentWordBounds!!.union(charBounds)
                            }
                        }
                        prevPos = pos
                    }
                    
                    if (currentWord.isNotEmpty() && currentWordBounds != null) {
                        words.add(PdfWord(currentWord.toString(), currentWordBounds!!))
                    }
                }
            }
            
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.sortByPosition = true
            
            val dummyStream = ByteArrayOutputStream()
            val writer: Writer = OutputStreamWriter(dummyStream)
            stripper.writeText(document, writer)
            writer.close()
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document?.close()
        }
        
        // ML Kit Fallback for Scanned Documents
        if (words.isEmpty() && bitmap != null) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizers = listOf(
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
                    TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
                    TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
                    TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
                    TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                )

                for (recognizer in recognizers) {
                    val mlText = recognizer.process(image).await()
                    if (mlText.textBlocks.isNotEmpty()) {
                        for (block in mlText.textBlocks) {
                            for (line in block.lines) {
                                for (element in line.elements) {
                                    val bbox = element.boundingBox
                                    if (bbox != null) {
                                        val normRect = RectF(
                                            bbox.left.toFloat() / bitmap.width,
                                            bbox.top.toFloat() / bitmap.height,
                                            bbox.right.toFloat() / bitmap.width,
                                            bbox.bottom.toFloat() / bitmap.height
                                        )
                                        words.add(PdfWord(element.text, normRect))
                                    }
                                }
                            }
                        }
                        if (words.isNotEmpty()) {
                            break // Stop once we find text in a language model
                        }
                    }
                }
                recognizers.forEach { it.close() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        PdfPageData(words, links)
    }
}
