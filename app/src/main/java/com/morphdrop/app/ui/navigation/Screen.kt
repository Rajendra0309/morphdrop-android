package com.morphdrop.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ConversionConfig : Screen("config/{conversionTypeId}") {
        fun createRoute(conversionTypeId: String) = "config/$conversionTypeId"
    }
    data object Processing : Screen("processing/{conversionTypeId}/{workId}") {
        fun createRoute(conversionTypeId: String, workId: String) = "processing/$conversionTypeId/$workId"
    }
    data object Result : Screen("result/{workId}") {
        fun createRoute(workId: String) = "result/$workId"
    }
    data object History : Screen("history")
    data object HistoryDetail : Screen("history_detail/{historyId}") {
        fun createRoute(historyId: Long) = "history_detail/$historyId"
    }
    data object Settings : Screen("settings")
    data object Welcome : Screen("welcome")
    data object Ocr : Screen("ocr")
    data object BatchOcr : Screen("batch_ocr")
    data object MarkdownViewer : Screen("markdown_viewer?uri={uri}") {
        fun createRoute(uri: String) = "markdown_viewer?uri=${Uri.encode(uri)}"
    }
    data object MarkdownEditor : Screen("markdown_editor?uri={uri}&isNew={isNew}") {
        fun createRoute(uri: String? = null, isNew: Boolean = false): String {
            val encodedUri = uri?.let { Uri.encode(it) } ?: ""
            return "markdown_editor?uri=$encodedUri&isNew=$isNew"
        }
    }
    data object BatchPdf : Screen("batch_pdf")
    data object PdfWatermark : Screen("pdf_watermark?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri.isNullOrBlank()) "pdf_watermark" else "pdf_watermark?uri=${Uri.encode(uri)}"
        }
    }
    data object PdfPageNumbers : Screen("pdf_page_numbers?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri.isNullOrBlank()) "pdf_page_numbers" else "pdf_page_numbers?uri=${Uri.encode(uri)}"
        }
    }
    data object PdfCompress : Screen("pdf_compress?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri.isNullOrBlank()) "pdf_compress" else "pdf_compress?uri=${Uri.encode(uri)}"
        }
    }
    data object PdfRotate : Screen("pdf_rotate?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri.isNullOrBlank()) "pdf_rotate" else "pdf_rotate?uri=${Uri.encode(uri)}"
        }
    }
}
