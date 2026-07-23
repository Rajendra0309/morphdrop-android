package com.morphdrop.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.morphdrop.app.ui.screens.conversion.ConversionConfigScreen
import com.morphdrop.app.ui.screens.history.HistoryScreen
import com.morphdrop.app.ui.screens.home.HomeScreen
import com.morphdrop.app.ui.screens.processing.ProcessingScreen
import com.morphdrop.app.ui.screens.result.ResultScreen
import com.morphdrop.app.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.ConversionConfig.route) {
            ConversionConfigScreen()
        }
        composable(Screen.Processing.route) {
            ProcessingScreen()
        }
        composable(Screen.Result.route) {
            ResultScreen()
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
