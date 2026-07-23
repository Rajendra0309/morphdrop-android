package com.morphdrop.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ConversionConfig : Screen("config")
    object Processing : Screen("processing")
    object Result : Screen("result")
    object History : Screen("history")
    object Settings : Screen("settings")
}
