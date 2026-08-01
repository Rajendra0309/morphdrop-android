package com.morphdrop.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.morphdrop.app.ui.screens.pdf.MergePdfScreen
import com.morphdrop.app.ui.screens.pdf.PdfPageEditorScreen
import com.morphdrop.app.ui.screens.pdf.PdfPasswordScreen
import com.morphdrop.app.ui.screens.pdf.SplitPdfScreen
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
    val routeToOrder = mapOf(
        Screen.Home.route to 0,
        Screen.History.route to 1,
        Screen.Settings.route to 2
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            val fromIndex = routeToOrder[initialState.destination.route] ?: -1
            val toIndex = routeToOrder[targetState.destination.route] ?: -1

            if (fromIndex != -1 && toIndex != -1) {
                if (toIndex > fromIndex) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(400))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(400))
                }
            } else {
                fadeIn(animationSpec = tween(400))
            }
        },
        exitTransition = {
            val fromIndex = routeToOrder[initialState.destination.route] ?: -1
            val toIndex = routeToOrder[targetState.destination.route] ?: -1

            if (fromIndex != -1 && toIndex != -1) {
                if (toIndex > fromIndex) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(400))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(400))
                }
            } else {
                fadeOut(animationSpec = tween(400))
            }
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400))
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
                onNavigateBack = { navController.popBackStack() }
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
        ) {
            ConversionConfigScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { typeId, workId ->
                    navController.navigate(Screen.Processing.createRoute(typeId, workId))
                }
            )
        }

        composable(Screen.MergePdf.route) {
            MergePdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { typeId, workId ->
                    navController.navigate(Screen.Processing.createRoute(typeId, workId))
                }
            )
        }

        composable(Screen.SplitPdf.route) {
            SplitPdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { typeId, workId ->
                    navController.navigate(Screen.Processing.createRoute(typeId, workId))
                }
            )
        }

        composable(Screen.PdfPassword.route) {
            PdfPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { typeId, workId ->
                    navController.navigate(Screen.Processing.createRoute(typeId, workId))
                }
            )
        }

        composable(Screen.PdfPageEditor.route) {
            PdfPageEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { typeId, workId ->
                    navController.navigate(Screen.Processing.createRoute(typeId, workId))
                }
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
