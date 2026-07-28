package com.morphdrop.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.morphdrop.app.ui.screens.conversion.ConversionConfigScreen
import com.morphdrop.app.ui.screens.history.HistoryScreen
import com.morphdrop.app.ui.screens.home.HomeScreen
import com.morphdrop.app.ui.screens.pdf.MergePdfScreen
import com.morphdrop.app.ui.screens.pdf.PdfPageEditorScreen
import com.morphdrop.app.ui.screens.pdf.PdfPasswordScreen
import com.morphdrop.app.ui.screens.pdf.SplitPdfScreen
import com.morphdrop.app.ui.screens.processing.ProcessingScreen
import com.morphdrop.app.ui.screens.result.ResultScreen
import com.morphdrop.app.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToConfig = { conversionTypeId ->
                    val route = when (conversionTypeId) {
                        "merge_pdf" -> Screen.MergePdf.route
                        "split_pdf" -> Screen.SplitPdf.route
                        "protect_pdf" -> Screen.PdfPassword.route
                        "page_editor" -> Screen.PdfPageEditor.route
                        else -> Screen.ConversionConfig.createRoute(conversionTypeId)
                    }
                    navController.navigate(route)
                }
            )
        }
        composable(
            route = Screen.ConversionConfig.route,
            arguments = listOf(navArgument("conversionTypeId") { type = NavType.StringType })
        ) {
            ConversionConfigScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { conversionTypeId, workId ->
                    navController.navigate(Screen.Processing.createRoute(conversionTypeId, workId))
                }
            )
        }
        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("conversionTypeId") { type = NavType.StringType },
                navArgument("workId") { type = NavType.StringType }
            )
        ) {
            ProcessingScreen(
                onNavigateToResult = {
                    val workId = navController.currentBackStackEntry?.arguments?.getString("workId") ?: ""
                    navController.navigate(Screen.Result.createRoute(workId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Screen.Result.route) {
            ResultScreen(
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.MergePdf.route) {
            MergePdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { workId ->
                    navController.navigate(Screen.Processing.createRoute("merge_pdf", workId))
                }
            )
        }
        composable(Screen.SplitPdf.route) {
            SplitPdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { workId ->
                    navController.navigate(Screen.Processing.createRoute("split_pdf", workId))
                }
            )
        }
        composable(Screen.PdfPageEditor.route) {
            PdfPageEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { workId ->
                    navController.navigate(Screen.Processing.createRoute("page_editor", workId))
                }
            )
        }
        composable(Screen.PdfPassword.route) {
            PdfPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { workId ->
                    navController.navigate(Screen.Processing.createRoute("protect_pdf", workId))
                }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
