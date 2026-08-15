package com.morphdrop.app.ui.navigation

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
    data object MergePdf : Screen("merge_pdf")
    data object SplitPdf : Screen("split_pdf")
    data object PdfPageEditor : Screen("page_editor")
    data object History : Screen("history")
    data object HistoryDetail : Screen("history_detail/{historyId}") {
        fun createRoute(historyId: Long) = "history_detail/$historyId"
    }
    data object Settings : Screen("settings")
    data object Welcome : Screen("welcome")
}
