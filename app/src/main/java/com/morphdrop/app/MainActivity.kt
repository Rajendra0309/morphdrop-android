package com.morphdrop.app

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.morphdrop.app.ui.components.MorphDropBottomNavigation
import com.morphdrop.app.ui.components.UpdateDialog
import com.morphdrop.app.ui.navigation.NavGraph
import com.morphdrop.app.ui.navigation.Screen
import com.morphdrop.app.ui.theme.MorphDropTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    private val pendingMarkdownUri = MutableStateFlow<String?>(null)
    private val pendingShortcutRoute = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val shortcutTarget = intent.getStringExtra("extra_navigate_to")
        if (!shortcutTarget.isNullOrBlank()) {
            pendingShortcutRoute.value = resolveShortcutRoute(shortcutTarget)
        }

        val openMarkdown = intent.getStringExtra("extra_open_markdown")
        if (!openMarkdown.isNullOrBlank()) {
            pendingMarkdownUri.value = openMarkdown
        }

        val openHistoryId = intent.getLongExtra("extra_open_history_id", -1L)
        if (openHistoryId != -1L) {
            pendingShortcutRoute.value = Screen.HistoryDetail.createRoute(openHistoryId)
        }

        extractMarkdownUri(intent)?.let { uriStr ->
            pendingMarkdownUri.value = uriStr
        }
    }

    private fun extractMarkdownUri(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        
        val mimeType = intent.type ?: contentResolver.getType(data)
        val path = data.path ?: ""
        
        val isMarkdown = mimeType?.contains("markdown", ignoreCase = true) == true ||
                mimeType?.contains("text/plain", ignoreCase = true) == true ||
                path.endsWith(".md", ignoreCase = true) ||
                data.toString().endsWith(".md", ignoreCase = true)
                
        return if (isMarkdown) data.toString() else null
    }

    private fun resolveShortcutRoute(target: String): String {
        return when (target.trim()) {
            "ocr", "ocr_text_extractor" -> Screen.Ocr.route
            "batch_ocr" -> Screen.BatchOcr.route
            "batch_pdf" -> Screen.BatchPdf.route
            "markdown", "markdown_editor" -> Screen.MarkdownEditor.createRoute(isNew = true)
            "watermark_pdf" -> Screen.PdfWatermark.createRoute()
            "page_numbers_pdf" -> Screen.PdfPageNumbers.createRoute()
            "compress_pdf" -> Screen.PdfCompress.createRoute()
            "rotate_pdf" -> Screen.PdfRotate.createRoute()
            "settings" -> Screen.Settings.route
            "history" -> Screen.History.route
            "config/image_to_pdf", "config/image_pdf", "image_converter" -> Screen.ConversionConfig.createRoute("image_converter")
            "image_to_pdf", "images_to_pdf" -> Screen.ConversionConfig.createRoute("images_to_pdf")
            "pdf_to_images", "pdf_to_image" -> Screen.ConversionConfig.createRoute("pdf_to_images")
            else -> {
                if (!target.startsWith("config/") && !target.contains("/") && !target.contains("?")) {
                    Screen.ConversionConfig.createRoute(target)
                } else {
                    target
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIncomingIntent(intent)

        // Background initialization: sync widgets and clean cache off the main thread for instant startup
        lifecycleScope.launch(Dispatchers.IO) {
            com.morphdrop.app.ui.widget.WidgetUpdateHelper.updateAllWidgets(applicationContext)
            cleanCacheIfOverLimit()
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay
            val maxMode = display.supportedModes.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }

        // Immediate exit to reduce splash screen delay to zero milliseconds.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()


            // Check for updates on start
            LaunchedEffect(Unit) {
                viewModel.checkForUpdates()
            }

            val context = androidx.compose.ui.platform.LocalContext.current

            // Handle update events
            LaunchedEffect(Unit) {
                viewModel.updateEvents.collect { event ->
                    when (event) {
                        is MainViewModel.UpdateEvent.Error -> {
                            android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        MainViewModel.UpdateEvent.UpToDate -> {
                            android.widget.Toast.makeText(context, "App is up to date", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        MainViewModel.UpdateEvent.Checking -> {
                            android.widget.Toast.makeText(context, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is MainViewModel.UpdateEvent.Generic -> {
                            android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val isDarkMode = when (themeMode) {
                com.morphdrop.app.domain.model.ThemeMode.DARK -> true
                com.morphdrop.app.domain.model.ThemeMode.LIGHT -> false
                com.morphdrop.app.domain.model.ThemeMode.SYSTEM -> isSystemDark
            }

            androidx.compose.runtime.LaunchedEffect(themeMode) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
                    val mode = when (themeMode) {
                        com.morphdrop.app.domain.model.ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
                        com.morphdrop.app.domain.model.ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                        com.morphdrop.app.domain.model.ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                    }
                    uiModeManager.setApplicationNightMode(mode)
                }
            }

            androidx.compose.runtime.DisposableEffect(isDarkMode) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkMode) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkMode) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )

                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkMode
                insetsController.isAppearanceLightNavigationBars = !isDarkMode

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        if (!isDarkMode) {
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        } else 0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    var flags = window.decorView.systemUiVisibility
                    flags = if (!isDarkMode) {
                        flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        flags = if (!isDarkMode) {
                            flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        } else {
                            flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                        }
                    }
                    window.decorView.systemUiVisibility = flags
                }

                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                )

                onDispose {}
            }
            val hasSeenWelcome by viewModel.hasSeenWelcome.collectAsState()
            
            val showSearchFab by viewModel.showSearchFab.collectAsState()
            val onSearchFabClick by viewModel.onSearchFabClick.collectAsState()
            val markdownUriEvent by pendingMarkdownUri.collectAsState()

            if (hasSeenWelcome != null) {
                val initialRoute = remember {
                    val initialMarkdown = extractMarkdownUri(intent)
                    if (initialMarkdown != null) {
                        Screen.MarkdownViewer.createRoute(initialMarkdown)
                    } else if (hasSeenWelcome == true) {
                        Screen.Home.route
                    } else {
                        Screen.Welcome.route
                    }
                }
                
                MorphDropTheme(darkTheme = isDarkMode) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: initialRoute
                    
                    val updateInfo by viewModel.updateInfo.collectAsState()

                    // Handle onNewIntent or dynamic markdown opening
                    LaunchedEffect(markdownUriEvent) {
                        markdownUriEvent?.let { uri ->
                            navController.navigate(Screen.MarkdownViewer.createRoute(uri)) {
                                launchSingleTop = true
                            }
                            pendingMarkdownUri.value = null
                        }
                    }

                    // Handle app shortcuts navigation
                    val shortcutRoute by pendingShortcutRoute.collectAsState()
                    LaunchedEffect(shortcutRoute) {
                        shortcutRoute?.let { route ->
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                            pendingShortcutRoute.value = null
                        }
                    }

                    val showBottomNav = currentRoute in listOf(
                        Screen.Home.route,
                        Screen.History.route,
                        Screen.Settings.route
                    )

                    val searchableScreens = listOf(Screen.Home.route, Screen.History.route)
                    val isSearchable = currentRoute in searchableScreens

                    // Reset search FAB when navigating to non-searchable screens
                    LaunchedEffect(currentRoute) {
                        if (!isSearchable) {
                            viewModel.resetSearchFab()
                        }
                    }

                    val downloadProgress by viewModel.downloadProgress.collectAsState()

                    if (updateInfo != null) {
                        UpdateDialog(
                            updateInfo = updateInfo!!,
                            downloadProgress = downloadProgress,
                            onDownload = { viewModel.downloadUpdate(updateInfo!!) },
                            onSkipVersion = { viewModel.skipVersion(updateInfo!!.versionName) },
                            onDismiss = { viewModel.dismissUpdateDialog() }
                        )
                    }

                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = MaterialTheme.colorScheme.background // Stable background to prevent flashes
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(innerPadding)
                        ) {
                            NavGraph(
                                navController = navController, 
                                mainViewModel = viewModel,
                                startDestination = initialRoute
                            )

                            // Removed custom toasts overlay

                            if (showBottomNav) {
                                MorphDropBottomNavigation(
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        if (currentRoute != route) {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    showSearchIcon = isSearchable && showSearchFab,
                                    onSearchClick = {
                                        onSearchFabClick?.invoke()
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            } else {
                // Fallback while loading
                MorphDropTheme(darkTheme = isDarkMode) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {}
                }
            }
        }
    }

    private fun cleanCacheIfOverLimit() {
        try {
            val cache = cacheDir ?: return
            fun getFolderSize(dir: java.io.File): Long {
                var size = 0L
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) getFolderSize(file) else file.length()
                }
                return size
            }

            var totalSize = getFolderSize(cache)
            externalCacheDir?.let { totalSize += getFolderSize(it) }

            val limitBytes = 100 * 1024 * 1024L // 100 MB
            if (totalSize > limitBytes) {
                cache.listFiles()?.forEach { file -> file.deleteRecursively() }
                externalCacheDir?.listFiles()?.forEach { file -> file.deleteRecursively() }
            }
        } catch (_: Exception) {
            // Ignore cache cleanup failure
        }
    }
}
