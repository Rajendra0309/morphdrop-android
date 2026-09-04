package com.morphdrop.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.morphdrop.app.MainViewModel
import com.morphdrop.app.ui.screens.conversion.ConversionConfigScreen
import com.morphdrop.app.ui.screens.history.HistoryDetailScreen
import com.morphdrop.app.ui.screens.history.HistoryScreen
import com.morphdrop.app.ui.screens.home.HomeScreen
import com.morphdrop.app.ui.screens.ocr.OcrScreen
import com.morphdrop.app.ui.screens.processing.ProcessingScreen
import com.morphdrop.app.ui.screens.result.ResultScreen
import com.morphdrop.app.ui.screens.settings.SettingsScreen
import com.morphdrop.app.ui.screens.welcome.WelcomeScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    startDestination: String = Screen.Home.route
) {
    val topLevelRoutes = listOf(Screen.Home.route, Screen.History.route, Screen.Settings.route)

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToConfig = { conversionTypeId ->
                    if (conversionTypeId == "ocr_text_extractor") {
                        navController.navigate(Screen.Ocr.route)
                    } else {
                        navController.navigate(Screen.ConversionConfig.createRoute(conversionTypeId))
                    }
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                mainViewModel = mainViewModel
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.HistoryDetail.createRoute(id))
                },
                mainViewModel = mainViewModel
            )
        }

        composable(
            route = Screen.HistoryDetail.route,
            arguments = listOf(navArgument("historyId") { type = NavType.LongType })
        ) {
            HistoryDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onCheckForUpdates = { mainViewModel.checkForUpdates(force = true) }
            )
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                    mainViewModel.completeOnboarding()
                }
            )
        }

        composable(
            route = Screen.ConversionConfig.route,
            arguments = listOf(navArgument("conversionTypeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val typeId = backStackEntry.arguments?.getString("conversionTypeId") ?: ""
            if (typeId == "ocr_text_extractor") {
                OcrScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                ConversionConfigScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProcessing = { tId, workId ->
                        navController.navigate(Screen.Processing.createRoute(tId, workId))
                    }
                )
            }
        }

        composable(Screen.Ocr.route) {
            OcrScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("conversionTypeId") { type = NavType.StringType },
                navArgument("workId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val workId = backStackEntry.arguments?.getString("workId") ?: ""
            ProcessingScreen(
                onNavigateToResult = {
                    navController.navigate(Screen.Result.createRoute(workId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument("workId") { type = NavType.StringType }
            )
        ) {
            ResultScreen(
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
