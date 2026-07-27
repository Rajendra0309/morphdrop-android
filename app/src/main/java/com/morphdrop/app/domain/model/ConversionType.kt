package com.morphdrop.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Transform
import androidx.compose.ui.graphics.vector.ImageVector

data class ConversionType(
    val id: String,
    val name: String,
    val description: String,
    val inputType: FileType,
    val outputType: FileType,
    val icon: ImageVector,
    val isFavorite: Boolean = false,
    val category: String,
    val isMultiFileAllowed: Boolean = false
) {
    companion object {
        const val CATEGORY_CONVERSIONS = "Conversions"
        const val CATEGORY_PDF_TOOLS = "PDF Tools"
        const val CATEGORY_IMAGE_TOOLS = "Image Tools"
        const val CATEGORY_UNOPTIMIZED = "Unoptimized (Experimental)"

        val defaultList = listOf(
            // Conversions
            ConversionType(
                id = "pdf_to_images",
                name = "PDF to Images",
                description = "Extract pages from PDF to PNG/JPG images",
                inputType = FileType.PDF,
                outputType = FileType.PNG,
                icon = Icons.Default.Image,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "images_to_pdf",
                name = "Images to PDF",
                description = "Combine PNG, JPG, or WEBP into a PDF",
                inputType = FileType.PNG,
                outputType = FileType.PDF,
                icon = Icons.Default.PictureAsPdf,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = true
            ),
            ConversionType(
                id = "word_to_pdf",
                name = "Word to PDF",
                description = "Convert DOCX documents into PDF (Experimental formatting)",
                inputType = FileType.DOCX,
                outputType = FileType.PDF,
                icon = Icons.Default.Description,
                category = CATEGORY_UNOPTIMIZED,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "excel_to_pdf",
                name = "Excel to PDF",
                description = "Convert XLSX spreadsheets to PDF format",
                inputType = FileType.XLSX,
                outputType = FileType.PDF,
                icon = Icons.Default.TableChart,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "ppt_to_pdf",
                name = "PPT to PDF",
                description = "Convert PPTX slide decks into PDF (Experimental multi-slide)",
                inputType = FileType.PPTX,
                outputType = FileType.PDF,
                icon = Icons.Default.Slideshow,
                category = CATEGORY_UNOPTIMIZED,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "txt_to_pdf",
                name = "Text to PDF",
                description = "Convert TXT text files into formatted PDF",
                inputType = FileType.TXT,
                outputType = FileType.PDF,
                icon = Icons.Default.PictureAsPdf,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "md_to_pdf",
                name = "Markdown to PDF",
                description = "Convert Markdown MD files into formatted PDF",
                inputType = FileType.MD,
                outputType = FileType.PDF,
                icon = Icons.Default.Description,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),

            // PDF Tools
            ConversionType(
                id = "merge_pdf",
                name = "Merge PDFs",
                description = "Combine multiple PDF documents into one",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.AutoMirrored.Filled.MergeType,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = true
            ),
            ConversionType(
                id = "split_pdf",
                name = "Split PDF",
                description = "Separate pages or split PDF into multiple files",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.AutoMirrored.Filled.CallSplit,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "compress_pdf",
                name = "Compress PDF",
                description = "Reduce PDF file size while keeping clear quality",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.Default.Compress,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "protect_pdf",
                name = "Protect PDF",
                description = "Add password encryption to your PDF document",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.Default.Lock,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "page_editor",
                name = "Organize PDF",
                description = "Reorder, rotate, and delete pages from PDF",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.Default.Transform,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),

            // Image Tools
            ConversionType(
                id = "image_converter",
                name = "Image Converter",
                description = "Convert between PNG, JPG, WEBP, and BMP formats",
                inputType = FileType.PNG,
                outputType = FileType.JPG,
                icon = Icons.Default.Transform,
                category = CATEGORY_IMAGE_TOOLS,
                isMultiFileAllowed = true
            ),
            ConversionType(
                id = "compress_images",
                name = "Compress Images",
                description = "Compress images with quality control",
                inputType = FileType.JPG,
                outputType = FileType.JPG,
                icon = Icons.Default.Compress,
                category = CATEGORY_IMAGE_TOOLS,
                isMultiFileAllowed = true
            )
        )
    }
}
