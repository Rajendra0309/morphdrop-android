package com.morphdrop.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Transform
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
                icon = Icons.Outlined.Collections,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "images_to_pdf",
                name = "Images to PDF",
                description = "Combine PNG, JPG, or WEBP into a PDF",
                inputType = FileType.PNG,
                outputType = FileType.PDF,
                icon = Icons.Outlined.PictureAsPdf,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = true
            ),
            ConversionType(
                id = "word_to_pdf",
                name = "Word to PDF",
                description = "Convert DOCX documents into PDF (Experimental formatting)",
                inputType = FileType.DOCX,
                outputType = FileType.PDF,
                icon = Icons.Outlined.Description,
                category = CATEGORY_UNOPTIMIZED,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "excel_to_pdf",
                name = "Excel to PDF",
                description = "Convert XLSX spreadsheets to PDF format",
                inputType = FileType.XLSX,
                outputType = FileType.PDF,
                icon = Icons.Outlined.TableChart,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "ppt_to_pdf",
                name = "PPT to PDF",
                description = "Convert PPTX slide decks into PDF (Experimental multi-slide)",
                inputType = FileType.PPTX,
                outputType = FileType.PDF,
                icon = Icons.Outlined.Slideshow,
                category = CATEGORY_UNOPTIMIZED,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "txt_to_pdf",
                name = "Text to PDF",
                description = "Convert TXT text files into formatted PDF",
                inputType = FileType.TXT,
                outputType = FileType.PDF,
                icon = Icons.AutoMirrored.Outlined.Article,
                category = CATEGORY_CONVERSIONS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "md_to_pdf",
                name = "Markdown to PDF",
                description = "Convert Markdown MD files into formatted PDF",
                inputType = FileType.MD,
                outputType = FileType.PDF,
                icon = Icons.Outlined.EditNote,
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
                icon = Icons.AutoMirrored.Outlined.MergeType,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = true
            ),
            ConversionType(
                id = "split_pdf",
                name = "Split PDF",
                description = "Separate pages or split PDF into multiple files",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.AutoMirrored.Outlined.CallSplit,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "compress_pdf",
                name = "Compress PDF",
                description = "Reduce PDF file size while keeping clear quality",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.Outlined.Compress,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "protect_pdf",
                name = "Protect PDF",
                description = "Add password encryption to your PDF document",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.Outlined.Lock,
                category = CATEGORY_PDF_TOOLS,
                isMultiFileAllowed = false
            ),
            ConversionType(
                id = "page_editor",
                name = "Organize PDF",
                description = "Reorder, rotate, and delete pages from PDF",
                inputType = FileType.PDF,
                outputType = FileType.PDF,
                icon = Icons.Outlined.Transform,
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
                icon = Icons.Outlined.Transform,
                category = CATEGORY_IMAGE_TOOLS,
                isMultiFileAllowed = true
            ),
            ConversionType(
                id = "compress_images",
                name = "Compress Images",
                description = "Compress images with quality control",
                inputType = FileType.JPG,
                outputType = FileType.JPG,
                icon = Icons.Outlined.Compress,
                category = CATEGORY_IMAGE_TOOLS,
                isMultiFileAllowed = true
            )
        )
    }
}
