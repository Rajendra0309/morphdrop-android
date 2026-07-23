package com.morphdrop.app.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ConversionConfig : Screen("config/{conversionTypeId}") {
        fun createRoute(conversionTypeId: String) = "config/$conversionTypeId"
    }
    data object Processing : Screen("processing/{conversionTypeId}") {
        fun createRoute(conversionTypeId: String) = "processing/$conversionTypeId"
    }
    data object Result : Screen("result")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
