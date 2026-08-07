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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.morphdrop.app.ui.components.MorphDropBottomNavigation
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
        
        val isLowEnd = (getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice


        // Immediate exit for all devices to reduce splash screen delay to zero.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()

            // Notify ViewModel of system theme changes to handle override resets
            LaunchedEffect(isSystemDark) {
                viewModel.onSystemThemeChanged(isSystemDark)
            }

            val isDarkMode = when (themeMode) {
                com.morphdrop.app.domain.model.ThemeMode.DARK -> true
                com.morphdrop.app.domain.model.ThemeMode.LIGHT -> false
                com.morphdrop.app.domain.model.ThemeMode.SYSTEM -> isSystemDark
            }

            androidx.compose.runtime.DisposableEffect(isDarkMode) {
                enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDarkMode },
                    navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDarkMode }
                )
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavGraph(
                            navController = navController, 
                            mainViewModel = viewModel,
                            startDestination = initialRoute
                        )

                        if (showBottomNav) {
                            MorphDropBottomNavigation(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(Screen.Home.route) {
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
}
