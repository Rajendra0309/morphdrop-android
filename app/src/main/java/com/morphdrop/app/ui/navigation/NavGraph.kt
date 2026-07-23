package com.morphdrop.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
            HomeScreen(
                onNavigateToConfig = { conversionTypeId ->
                    navController.navigate(Screen.ConversionConfig.createRoute(conversionTypeId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(
            route = Screen.ConversionConfig.route,
            arguments = listOf(navArgument("conversionTypeId") { type = NavType.StringType })
        ) {
            ConversionConfigScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { conversionTypeId ->
                    navController.navigate(Screen.Processing.createRoute(conversionTypeId))
                }
            )
        }
        composable(
            route = Screen.Processing.route,
            arguments = listOf(navArgument("conversionTypeId") { type = NavType.StringType })
        ) {
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
