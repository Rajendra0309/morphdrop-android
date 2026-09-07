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
}
