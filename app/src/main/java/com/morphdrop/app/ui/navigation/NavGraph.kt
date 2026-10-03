package com.morphdrop.app.ui.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
import com.morphdrop.app.ui.screens.ocr.BatchOcrScreen
import com.morphdrop.app.ui.screens.ocr.OcrScreen
import com.morphdrop.app.ui.screens.processing.ProcessingScreen
import com.morphdrop.app.ui.screens.result.ResultScreen
import com.morphdrop.app.ui.screens.settings.SettingsScreen
import com.morphdrop.app.ui.screens.welcome.WelcomeScreen
import com.morphdrop.app.ui.screens.markdown.MarkdownViewerScreen
import com.morphdrop.app.ui.screens.markdown.MarkdownEditorScreen
import com.morphdrop.app.ui.screens.pdf.batch.BatchPdfScreen
import com.morphdrop.app.ui.screens.pdf.watermark.PdfWatermarkScreen
import com.morphdrop.app.ui.screens.pdf.pagenumbers.PdfPageNumbersScreen
import com.morphdrop.app.ui.screens.pdf.compress.PdfCompressScreen
import com.morphdrop.app.ui.screens.pdf.rotate.PdfRotateScreen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

@Composable
fun NavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    startDestination: String = Screen.Home.route,
    // Adaptive layout inputs (computed from the window size in MainActivity).
    gridColumns: Int = 2,
    isExpanded: Boolean = false
) {
    val topLevelRoutes = listOf(Screen.Home.route, Screen.History.route, Screen.Settings.route)
    val context = LocalContext.current

    // Back navigation that also works when the current screen is a deep-link
    // start destination with an empty back stack (e.g. Excel opened from
    // Telegram): finishing the activity returns the user to the sender app
    // instead of the back button silently doing nothing.
    val navigateUpOrFinish: () -> Unit = {
        if (!navController.popBackStack()) {
            (context as? Activity)?.finish()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            val isTopLevel = targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes
            if (isTopLevel) {
                fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
            } else {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        },
        exitTransition = {
            val isTopLevel = targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes
            if (isTopLevel) {
                fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            } else {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        },
        popEnterTransition = {
            val isTopLevel = targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes
            if (isTopLevel) {
                fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
            } else {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        },
        popExitTransition = {
            val isTopLevel = targetState.destination.route in topLevelRoutes && initialState.destination.route in topLevelRoutes
            if (isTopLevel) {
                fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            } else {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToConfig = { conversionTypeId ->
                    when (conversionTypeId) {
                        "ocr_text_extractor" -> navController.navigate(Screen.Ocr.route)
                        "batch_ocr" -> navController.navigate(Screen.BatchOcr.route)
                        "batch_pdf" -> navController.navigate(Screen.BatchPdf.route)
                        "markdown_editor" -> navController.navigate(Screen.MarkdownEditor.createRoute(isNew = true))
                        "watermark_pdf" -> navController.navigate(Screen.PdfWatermark.createRoute())
                        "page_numbers_pdf" -> navController.navigate(Screen.PdfPageNumbers.createRoute())
                        "compress_pdf" -> navController.navigate(Screen.PdfCompress.createRoute())
                        "rotate_pdf" -> navController.navigate(Screen.PdfRotate.createRoute())
                        else -> navController.navigate(Screen.ConversionConfig.createRoute(conversionTypeId))
                    }
                },
                // Long-press quick conversion: same destinations, but the config
                // screen auto-starts processing with the saved preset once files
                // are picked. Tools with dedicated screens fall back to normal.
                onQuickConvert = { conversionTypeId ->
                    when (conversionTypeId) {
                        "ocr_text_extractor" -> navController.navigate(Screen.Ocr.route)
                        "batch_ocr" -> navController.navigate(Screen.BatchOcr.route)
                        "batch_pdf" -> navController.navigate(Screen.BatchPdf.route)
                        "markdown_editor" -> navController.navigate(Screen.MarkdownEditor.createRoute(isNew = true))
                        "watermark_pdf" -> navController.navigate(Screen.PdfWatermark.createRoute())
                        "page_numbers_pdf" -> navController.navigate(Screen.PdfPageNumbers.createRoute())
                        "compress_pdf" -> navController.navigate(Screen.PdfCompress.createRoute())
                        "rotate_pdf" -> navController.navigate(Screen.PdfRotate.createRoute())
                        else -> navController.navigate(
                            Screen.ConversionConfig.createRoute(conversionTypeId, autostart = true)
                        )
                    }
                },
                onOpenRoute = { route -> navController.navigate(route) },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                mainViewModel = mainViewModel,
                gridColumns = gridColumns,
                hasNavigationRail = isExpanded
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = navigateUpOrFinish,
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.HistoryDetail.createRoute(id))
                },
                onTrySampleConvert = { uri ->
                    navController.navigate(
                        Screen.ConversionConfig.createRoute("pdf_to_images", uri = uri)
                    )
                },
                mainViewModel = mainViewModel,
                isExpanded = isExpanded
            )
        }

        composable(
            route = Screen.HistoryDetail.route,
            arguments = listOf(navArgument("historyId") { type = NavType.LongType })
        ) {
            HistoryDetailScreen(
                onNavigateBack = navigateUpOrFinish
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = navigateUpOrFinish,
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
            arguments = listOf(
                navArgument("conversionTypeId") { type = NavType.StringType },
                navArgument("uri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("autostart") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val typeId = backStackEntry.arguments?.getString("conversionTypeId") ?: ""
            val initialUri = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotBlank() && it != "{uri}" }
            val autoStart = backStackEntry.arguments?.getString("autostart") == "true"
            if (typeId == "ocr_text_extractor") {
                OcrScreen(
                    onNavigateBack = navigateUpOrFinish
                )
            } else {
                ConversionConfigScreen(
                    initialUri = initialUri,
                    autoStart = autoStart,
                    onNavigateBack = navigateUpOrFinish,
                    onNavigateToProcessing = { tId, workId ->
                        navController.navigate(Screen.Processing.createRoute(tId, workId))
                    }
                )
            }
        }

        composable(Screen.Ocr.route) {
            OcrScreen(
                onNavigateBack = navigateUpOrFinish,
                onNavigateToBatchOcr = { navController.navigate(Screen.BatchOcr.route) }
            )
        }

        composable(Screen.BatchOcr.route) {
            BatchOcrScreen(
                onNavigateBack = navigateUpOrFinish
            )
        }

        composable(Screen.BatchPdf.route) {
            BatchPdfScreen(
                onNavigateBack = navigateUpOrFinish
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
            val processingTypeId = backStackEntry.arguments?.getString("conversionTypeId") ?: ""
            ProcessingScreen(
                onNavigateToResult = {
                    navController.navigate(Screen.Result.createRoute(workId, processingTypeId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument("workId") { type = NavType.StringType },
                navArgument("typeId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val resultTypeId = backStackEntry.arguments?.getString("typeId")?.takeIf { it.isNotBlank() && it != "{typeId}" }
            ResultScreen(
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onConvertAnother = if (resultTypeId != null) {
                    {
                        navController.navigate(Screen.ConversionConfig.createRoute(resultTypeId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                } else null
            )
        }

        composable(
            route = Screen.MarkdownViewer.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            MarkdownViewerScreen(
                uriString = uriString,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        // If opened externally and there's no backstack, go to Home or finish
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0)
                        }
                    }
                },
                onNavigateToEditor = { editUri ->
                    navController.navigate(Screen.MarkdownEditor.createRoute(uri = editUri))
                }
            )
        }

        composable(
            route = Screen.MarkdownEditor.route,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isNew") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotEmpty() }
            val isNew = backStackEntry.arguments?.getBoolean("isNew") ?: false
            MarkdownEditorScreen(
                uriString = uriString,
                isNew = isNew,
                onNavigateBack = navigateUpOrFinish
            )
        }

        composable(
            route = Screen.PdfWatermark.route,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotBlank() && it != "{uri}" }
            PdfWatermarkScreen(
                initialUri = uriString?.let { Uri.parse(it) },
                onNavigateBack = navigateUpOrFinish
            )
        }

        composable(
            route = Screen.PdfPageNumbers.route,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotBlank() && it != "{uri}" }
            PdfPageNumbersScreen(
                initialUri = uriString?.let { Uri.parse(it) },
                onNavigateBack = navigateUpOrFinish
            )
        }

        composable(
            route = Screen.PdfCompress.route,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotBlank() && it != "{uri}" }
            PdfCompressScreen(
                initialUri = uriString?.let { Uri.parse(it) },
                onNavigateBack = navigateUpOrFinish
            )
        }

        composable(
            route = Screen.PdfRotate.route,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")?.takeIf { it.isNotBlank() && it != "{uri}" }
            PdfRotateScreen(
                initialUri = uriString?.let { Uri.parse(it) },
                onNavigateBack = navigateUpOrFinish
            )
        }
    }
}
