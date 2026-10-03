package com.morphdrop.app

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.morphdrop.app.ui.components.MorphDropBottomNavigation
import com.morphdrop.app.ui.components.MorphDropNavigationRail
import com.morphdrop.app.ui.components.UpdateDialog
import com.morphdrop.app.ui.components.WhatsNewDialog
import com.morphdrop.app.ui.navigation.NavGraph
import com.morphdrop.app.ui.navigation.Screen
import com.morphdrop.app.ui.theme.MorphDropTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            return
        }

        val openMarkdown = intent.getStringExtra("extra_open_markdown")
        if (!openMarkdown.isNullOrBlank()) {
            pendingMarkdownUri.value = openMarkdown
            return
        }

        val openHistoryId = intent.getLongExtra("extra_open_history_id", -1L)
        if (openHistoryId != -1L) {
            pendingShortcutRoute.value = Screen.HistoryDetail.createRoute(openHistoryId)
            return
        }

        val incomingUris = extractIncomingUris(intent)
        if ((intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            incomingUris.forEach { u ->
                runCatching {
                    contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        val firstUri = incomingUris.firstOrNull()

        lifecycleScope.launch(Dispatchers.IO) {
            val rawMimeType = intent.type
            val resolvedMimeType = if (rawMimeType.isNullOrBlank() || rawMimeType == "*/*" || rawMimeType == "application/octet-stream") {
                firstUri?.let { runCatching { contentResolver.getType(it) }.getOrNull() } ?: rawMimeType
            } else {
                rawMimeType
            }
            val fileName = firstUri?.let { getUriFileName(it) } ?: ""
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val isPdf = resolvedMimeType?.equals("application/pdf", ignoreCase = true) == true || ext == "pdf"

            if (isPdf && firstUri != null) {
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    val pdfIntent = Intent(this@MainActivity, PdfViewerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = firstUri
                        putExtra("pdf_uri", firstUri)
                        clipData = intent.clipData ?: android.content.ClipData.newRawUri("PDF", firstUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(pdfIntent)
                }
                return@launch
            }

            val route = resolveIncomingFileRoute(intent, incomingUris)
            if (route != null) {
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    pendingShortcutRoute.value = route
                }
            }
        }
    }

    private fun extractIncomingUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val action = intent.action
        val uris = mutableListOf<Uri>()

        if (action == Intent.ACTION_VIEW) {
            intent.data?.let { uris.add(it) }
        } else if (action == Intent.ACTION_SEND) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                uris.add(streamUri)
            } else {
                intent.data?.let { uris.add(it) }
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!streamUris.isNullOrEmpty()) {
                uris.addAll(streamUris)
            }
        }

        // Also check ClipData if available
        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                val itemUri = clipData.getItemAt(i).uri
                if (itemUri != null && itemUri !in uris) {
                    uris.add(itemUri)
                }
            }
        }

        return uris
    }

    private fun getUriFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment ?: ""
    }

    private fun copySharedUriToCache(uri: Uri): Uri? {
        return runCatching {
            val rawName = getUriFileName(uri).substringAfterLast('/').substringAfterLast('\\')
            val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "shared_file" }
            val uniqueSubdir = java.io.File(
                cacheDir,
                "shared_incoming/${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
            ).apply { mkdirs() }
            val dest = java.io.File(uniqueSubdir, safeName)
            val allowedPrefix = uniqueSubdir.canonicalPath.let { if (it.endsWith(java.io.File.separator)) it else it + java.io.File.separator }
            if (!dest.canonicalPath.startsWith(allowedPrefix)) {
                return null
            }
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                dest
            )
        }.getOrNull()
    }

    private fun resolveIncomingFileRoute(intent: Intent?, incomingUris: List<Uri>): String? {
        if (incomingUris.isEmpty()) {
            val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val sharedDir = java.io.File(filesDir, "shared_text").apply { mkdirs() }
                val sharedFile = java.io.File(sharedDir, "shared_text_${System.currentTimeMillis()}.md")
                runCatching {
                    sharedFile.writeText(sharedText)
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        sharedFile
                    )
                    return Screen.MarkdownViewer.createRoute(fileUri.toString())
                }
            }
            return null
        }

        val firstIncomingUri = incomingUris.first()
        val rawMimeType = intent?.type
        val mimeType = if (rawMimeType.isNullOrBlank() || rawMimeType == "*/*" || rawMimeType == "application/octet-stream") {
            runCatching { contentResolver.getType(firstIncomingUri) }.getOrNull() ?: rawMimeType
        } else {
            rawMimeType
        }
        val fileName = getUriFileName(firstIncomingUri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        // 1. PDF -> Handled synchronously before this method
        if (mimeType?.equals("application/pdf", ignoreCase = true) == true || ext == "pdf") {
            return null
        }

        // Determine target route type before handling storage
        val isExcel = ext in listOf("xlsx", "xls", "csv") || 
            mimeType in listOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv",
                "application/csv",
                "text/comma-separated-values"
            )
        val isMarkdown = ext in listOf("md", "markdown") || mimeType?.contains("markdown", ignoreCase = true) == true
        val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp")
        val isImages = ext in imageExtensions || (mimeType?.startsWith("image/") == true && ext !in listOf("gif", "heic", "svg"))
        val isTxt = mimeType?.equals("text/plain", ignoreCase = true) == true || ext == "txt"

        // 2. Markdown -> Direct viewer, no background WorkManager conversion needed
        if (isMarkdown) {
            return Screen.MarkdownViewer.createRoute(firstIncomingUri.toString())
        }

        // 3. Fallback to Metadata Editor for non-conversion types, direct view without cache copy
        val needsWorkManager = isExcel || isImages || isTxt
        if (!needsWorkManager) {
            return Screen.ConversionConfig.createRoute("metadata_editor", firstIncomingUri.toString())
        }

        // Durable read access: only take a persistable URI grant when the sender
        // actually granted FLAG_GRANT_PERSISTABLE_URI_PERMISSION. ACTION_SEND/VIEW
        // senders essentially never do, so otherwise copy the shared content into
        // app-private cache — the WorkManager conversion must survive process death.
        val hasPersistableGrant = intent != null &&
                (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        val uris = if (hasPersistableGrant) {
            incomingUris.forEach { u ->
                runCatching {
                    contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            incomingUris
        } else {
            incomingUris.map { uri -> copySharedUriToCache(uri) ?: uri }
        }

        val firstUri = uris.first()

        // 4. Route to respective WorkManager conversion
        if (isExcel) {
            return Screen.ConversionConfig.createRoute("excel_to_pdf", firstUri.toString())
        }
        if (isImages) {
            val joinedUris = uris.joinToString("|") { it.toString() }
            return Screen.ConversionConfig.createRoute("images_to_pdf", joinedUris)
        }
        if (isTxt) {
            return Screen.ConversionConfig.createRoute("txt_to_pdf", firstUri.toString())
        }

        return Screen.ConversionConfig.createRoute("metadata_editor", firstUri.toString())
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

        // Only handle the launch intent on first creation; onNewIntent() handles
        // subsequent intents. Otherwise rotation would re-fire side effects
        // (duplicate shared_text files, stacked PdfViewerActivity instances).
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

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
            val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()


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

            val markdownUriEvent by pendingMarkdownUri.collectAsState()

            if (hasSeenWelcome != null) {
                val initialRoute = remember {
                    val incomingRoute = pendingShortcutRoute.value
                    if (incomingRoute != null) {
                        pendingShortcutRoute.value = null
                        incomingRoute
                    } else if (hasSeenWelcome == true) {
                        Screen.Home.route
                    } else {
                        Screen.Welcome.route
                    }
                }
                
                MorphDropTheme(darkTheme = isDarkMode, dynamicColor = dynamicColorEnabled) {
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

                    // Handle app shortcuts navigation. The route originates from an
                    // exported activity's intent extra, so validate before navigating:
                    // fall back to Home instead of crashing on a malformed route.
                    val shortcutRoute by pendingShortcutRoute.collectAsState()
                    LaunchedEffect(shortcutRoute) {
                        shortcutRoute?.let { route ->
                            runCatching {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }.onFailure {
                                runCatching {
                                    navController.navigate(Screen.Home.route) {
                                        launchSingleTop = true
                                    }
                                }
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

                    // Scroll-driven search button beside the navbar (restored): hide it
                    // when leaving the searchable screens so it never lingers.
                    val showSearchFab by viewModel.showSearchFab.collectAsState()
                    val onSearchFabClick by viewModel.onSearchFabClick.collectAsState()
                    LaunchedEffect(currentRoute) {
                        if (!isSearchable) {
                            viewModel.resetSearchFab()
                        }
                    }

                    val downloadProgress by viewModel.downloadProgress.collectAsState()
                    val whatsNewInfo by viewModel.whatsNewInfo.collectAsState()

                    if (updateInfo != null && currentRoute != Screen.Welcome.route) {
                        UpdateDialog(
                            updateInfo = updateInfo!!,
                            downloadProgress = downloadProgress,
                            onDownload = { viewModel.downloadUpdate(updateInfo!!) },
                            onSkipVersion = { viewModel.skipVersion(updateInfo!!.versionName) },
                            onDismiss = { viewModel.dismissUpdateDialog() }
                        )
                    } else if (whatsNewInfo != null && currentRoute == Screen.Home.route) {
                        WhatsNewDialog(
                            whatsNewInfo = whatsNewInfo!!,
                            onDismiss = { viewModel.dismissWhatsNewDialog() }
                        )
                    }

                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = MaterialTheme.colorScheme.background // Stable background to prevent flashes
                    ) { innerPadding ->
                        // Adaptive navigation: navigation rail on large screens
                        // (tablets, Chromebooks, unfolded foldables), bottom pill otherwise.
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(innerPadding)
                        ) {
                            val isExpanded = maxWidth >= 840.dp
                            // Tool grid density follows the window width.
                            val gridColumns = when {
                                maxWidth >= 840.dp -> 4
                                maxWidth >= 600.dp -> 3
                                else -> 2
                            }

                            val onTopLevelNavigate: (String) -> Unit = { route ->
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxSize()) {
                                if (isExpanded && showBottomNav) {
                                    MorphDropNavigationRail(
                                        currentRoute = currentRoute,
                                        onNavigate = onTopLevelNavigate,
                                        showSearchIcon = isSearchable && showSearchFab,
                                        onSearchClick = {
                                            onSearchFabClick?.invoke()
                                        }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    NavGraph(
                                        navController = navController,
                                        mainViewModel = viewModel,
                                        startDestination = initialRoute,
                                        gridColumns = gridColumns,
                                        isExpanded = isExpanded
                                    )

                                    if (!isExpanded && showBottomNav) {
                                        MorphDropBottomNavigation(
                                            currentRoute = currentRoute,
                                            onNavigate = onTopLevelNavigate,
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
                    }
                }
            } else {
                // Fallback while loading
                MorphDropTheme(darkTheme = isDarkMode, dynamicColor = dynamicColorEnabled) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {}
                }
            }
        }
    }

    /**
     * Keeps cache under 100 MB. When over the limit, deletes the oldest files
     * first until we're back under 80 MB (hysteresis, so one big conversion
     * doesn't trigger a cleanup on every launch). Files touched in the last
     * 15 minutes are spared — they may belong to a conversion still in flight.
     */
    private fun cleanCacheIfOverLimit() {
        try {
            val cache = cacheDir ?: return
            val cacheDirs = listOfNotNull(cache, externalCacheDir)

            fun collectFiles(dir: java.io.File, out: MutableList<java.io.File>) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) collectFiles(file, out) else out.add(file)
                }
            }
            val allFiles = mutableListOf<java.io.File>()
            cacheDirs.forEach { collectFiles(it, allFiles) }

            var totalSize = allFiles.sumOf { it.length() }
            val limitBytes = 100 * 1024 * 1024L // 100 MB
            if (totalSize <= limitBytes) return

            val targetBytes = 80 * 1024 * 1024L // 80 MB
            val inFlightCutoff = System.currentTimeMillis() - 15 * 60 * 1000L
            val sharedIncomingCutoff = System.currentTimeMillis() - 48 * 60 * 60 * 1000L // 48 hours to match worker retry window
            val sharedIncomingDir = java.io.File(cache, "shared_incoming")
            val sharedIncomingCanonical = runCatching {
                sharedIncomingDir.canonicalPath.let { if (it.endsWith(java.io.File.separator)) it else it + java.io.File.separator }
            }.getOrNull()
            for (file in allFiles.sortedBy { it.lastModified() }) {
                if (totalSize <= targetBytes) break // back under the limit; proceed to prune empty dirs
                if (file.lastModified() >= inFlightCutoff) continue // spare: may be in use
                val isSharedIncoming = sharedIncomingCanonical != null && file.canonicalPath.startsWith(sharedIncomingCanonical)
                if (isSharedIncoming && file.lastModified() >= sharedIncomingCutoff) continue // spare recent shared inputs
                val size = file.length()
                if (file.delete()) totalSize -= size
            }

            // Prune directories left empty by the eviction (never the roots).
            fun pruneEmpty(dir: java.io.File, isRoot: Boolean) {
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory) pruneEmpty(child, isRoot = false)
                }
                if (!isRoot && dir.listFiles()?.isEmpty() == true) dir.delete()
            }
            cacheDirs.forEach { pruneEmpty(it, isRoot = true) }
        } catch (_: Exception) {
            // Ignore cache cleanup failure
        }
    }
}
