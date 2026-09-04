package com.morphdrop.app

import android.animation.ObjectAnimator
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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay
            val maxMode = display.supportedModes.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }

        
        val isLowEnd = (getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice

        // Clean app cache on startup if > 100MB
        cleanCacheIfOverLimit()

        // Removed aggressive ACCESS_MEDIA_LOCATION request on startup

        // Immediate exit for all devices to reduce splash screen delay to zero.
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
                enableEdgeToEdge()
                onDispose {}
            }
            val hasSeenWelcome by viewModel.hasSeenWelcome.collectAsState()
            
            val showSearchFab by viewModel.showSearchFab.collectAsState()
            val onSearchFabClick by viewModel.onSearchFabClick.collectAsState()

            if (hasSeenWelcome != null) {
                val initialRoute = remember {
                    if (hasSeenWelcome == true) Screen.Home.route else Screen.Welcome.route
                }
                
                MorphDropTheme(darkTheme = isDarkMode) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: initialRoute
                    
                    val updateInfo by viewModel.updateInfo.collectAsState()

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

                    if (updateInfo != null) {
                        UpdateDialog(
                            updateInfo = updateInfo!!,
                            onDownload = { viewModel.downloadUpdate(updateInfo!!) },
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
