package com.morphdrop.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
        startDestination = startDestination,
        enterTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            
            if (targetRoute in topLevelRoutes && initialRoute in topLevelRoutes) {
                // Seamless crossfade for navbar tab switching
                fadeIn(animationSpec = tween(250))
            } else {
                // Native-like sliding for deep navigation
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            }
        },
        exitTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            
            if (targetRoute in topLevelRoutes && initialRoute in topLevelRoutes) {
                // Use a very short fade out to avoid "gap" flashes
                fadeOut(animationSpec = tween(150))
            } else {
                // Scale out + slide for native feel
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + scaleOut(targetScale = 0.9f, animationSpec = tween(450)) + fadeOut(animationSpec = tween(300))
            }
        },
        popEnterTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            
            if (targetRoute in topLevelRoutes && initialRoute in topLevelRoutes) {
                fadeIn(animationSpec = tween(250))
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + scaleIn(initialScale = 0.9f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(300))
            }
        },
        popExitTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            
            if (targetRoute in topLevelRoutes && initialRoute in topLevelRoutes) {
                fadeOut(animationSpec = tween(150))
            } else {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            }
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToConfig = { conversionTypeId ->
                    navController.navigate(Screen.ConversionConfig.createRoute(conversionTypeId))
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
                onCheckForUpdates = { mainViewModel.checkForUpdates(force = true) },
                mainViewModel = mainViewModel
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
