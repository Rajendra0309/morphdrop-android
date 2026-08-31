package com.morphdrop.app.ui.screens.pdf

import com.morphdrop.app.ui.screens.pdf.AnnotationTool
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.foundation.magnifier
import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.SearchMatch
import com.morphdrop.app.ui.components.PdfFastScroller
import com.morphdrop.app.ui.components.ZoomableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val pdfUiState by viewModel.pdfUiState.collectAsState()
    val listState = rememberLazyListState()
    val visiblePages by viewModel.visiblePages.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }
    var showReadingModeSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    var isVerticalMode by remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pdfUiState.totalPages.coerceAtLeast(1) })

    val window = (context as? Activity)?.window
    var isBarsHiddenByScroll by remember { mutableStateOf(false) }
    val shouldHideSystemUI = pdfUiState.isImmersiveMode || isBarsHiddenByScroll

    if (window != null) {
        val controller = remember { WindowInsetsControllerCompat(window, window.decorView) }
        LaunchedEffect(shouldHideSystemUI) {
            if (shouldHideSystemUI) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        LaunchedEffect(pdfUiState.readingMode) {
            val isNight = pdfUiState.readingMode == ReadingMode.NIGHT
            controller.isAppearanceLightStatusBars = !isNight
            controller.isAppearanceLightNavigationBars = !isNight
        }
    }

    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }

    val handleBack = {
        if (hasUnsavedChanges) {
            showSaveDialog = true
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(pdfUri) {
        viewModel.loadPdf(pdfUri)
    }

    val allAnnotations by viewModel.annotations.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PdfViewerEvent.ScrollToPage -> {
                    coroutineScope.launch {
                        if (isVerticalMode) {
                            listState.animateScrollToItem(event.pageIndex)
                        } else {
                            pagerState.animateScrollToPage(event.pageIndex)
                        }
                    }
                }
                is PdfViewerEvent.ShowSnackbar -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PdfViewerEvent.ThumbnailGenerated -> {}
            }
        }
    }

    val firstVisibleIndex by remember { derivedStateOf { if (isVerticalMode) listState.firstVisibleItemIndex else pagerState.currentPage } }
    LaunchedEffect(firstVisibleIndex) {
        viewModel.updateCurrentPage(firstVisibleIndex)
    }

    // Sync Pager and ListState when switching modes
    LaunchedEffect(isVerticalMode) {
        if (isVerticalMode) {
            listState.scrollToItem(pagerState.currentPage)
        } else {
            pagerState.scrollToPage(listState.firstVisibleItemIndex)
        }
    }

    // Handle Back Button
    BackHandler(enabled = isSearchActive || hasUnsavedChanges) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            viewModel.search("")
            focusManager.clearFocus()
        } else if (hasUnsavedChanges) {
            showSaveDialog = true
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Changes?") },
            text = { Text("You have unsaved annotations. Do you want to save them before closing?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    viewModel.saveAnnotations()
                    onNavigateBack()
                }) {
                    Text("Save & Close")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    viewModel.discardAnnotations()
                    onNavigateBack()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                TextField(
                    value = jumpPageInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) jumpPageInput = it },
                    placeholder = { Text("1 - ${pdfUiState.totalPages}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val page = jumpPageInput.toIntOrNull()
                    if (page != null && page in 1..pdfUiState.totalPages) {
                        coroutineScope.launch { listState.scrollToItem(page - 1) }
                        showJumpDialog = false
                    }
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.exportPdf(uri)
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < -2f) {
                    isBarsHiddenByScroll = true
                } else if (available.y > 2f) {
                    isBarsHiddenByScroll = false
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            TocDrawerSheet(
                tocList = pdfUiState.tocList,
                onNavigate = { pageIndex ->
                    coroutineScope.launch {
                        drawerState.close()
                        if (isVerticalMode) {
                            listState.animateScrollToItem(pageIndex)
                        } else {
                            pagerState.animateScrollToPage(pageIndex)
                        }
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (!shouldHideSystemUI && !pdfUiState.isAnnotationMode) {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { viewModel.toggleAnnotationMode() },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Annotate")
                    }
                }
            },
            topBar = {
                AnimatedVisibility(
                    visible = !shouldHideSystemUI,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it }
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp)) {
                            AnimatedVisibility(
                                visible = pdfUiState.isAnnotationMode,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.toggleAnnotationMode() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Annotation Mode")
                                    }
                                    Text(
                                        text = "Annotation Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    TextButton(onClick = { exportLauncher.launch(pdfUiState.fileName.substringBeforeLast(".") + "_Annotated.pdf") }) {
                                        Text("Export PDF")
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !isSearchActive && !pdfUiState.isAnnotationMode,
                                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = handleBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                    Text(
                                        text = pdfUiState.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .combinedClickable(
                                                onClick = { /* Tap ignored */ },
                                                onLongClick = {
                                                    viewModel.showToast(pdfUiState.fileName)
                                                }
                                            )
                                    )
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                    IconButton(onClick = { showThumbnailsSheet = true }) {
                                        Icon(Icons.Default.GridView, contentDescription = "Thumbnails")
                                    }
                                    if (pdfUiState.tocList.isNotEmpty()) {
                                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.List, contentDescription = "Table of Contents")
                                        }
                                    }
                                    var showOverflowMenu by remember { mutableStateOf(false) }
                                    IconButton(onClick = { showOverflowMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                                        DropdownMenu(
                                            expanded = showOverflowMenu,
                                            onDismissRequest = { showOverflowMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if (isVerticalMode) "Horizontal Scroll" else "Vertical Scroll") },
                                                leadingIcon = { Icon(if (isVerticalMode) Icons.Default.SwapHoriz else Icons.Default.SwapVert, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    isVerticalMode = !isVerticalMode
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Reading Mode") },
                                                leadingIcon = { Icon(Icons.Default.SettingsBrightness, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    showReadingModeSheet = true
                                                }
                                            )
                                            androidx.compose.material3.HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(if (pdfUiState.bookmarks.any { it.pageNumber == firstVisibleIndex + 1 }) "Remove Bookmark" else "Add Bookmark") },
                                                leadingIcon = { Icon(if (pdfUiState.bookmarks.any { it.pageNumber == firstVisibleIndex + 1 }) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.toggleBookmarkForPage(firstVisibleIndex + 1)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Bookmarks") },
                                                leadingIcon = { Icon(Icons.Default.Bookmarks, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    showBookmarksSheet = true
                                                }
                                            )
                                            androidx.compose.material3.HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Share PDF") },
                                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.sharePdf(context)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Share Current Page") },
                                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.shareCurrentPage(context)
                                                }
                                            )
                                            androidx.compose.material3.HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Print") },
                                                leadingIcon = { Icon(Icons.Default.Print, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.printPdf(context)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Download") },
                                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    viewModel.downloadPdf()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = isSearchActive,
                                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                        viewModel.search("")
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                                    }
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            viewModel.search(it)
                                        },
                                        placeholder = { Text("Search text...") },
                                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            viewModel.search("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                }
                            }

                            AnnotationBottomBar(
                                isVisible = pdfUiState.isAnnotationMode,
                                currentTool = pdfUiState.annotationTool,
                                currentColor = pdfUiState.brushColor,
                                currentStrokeWidth = pdfUiState.brushSize,
                                showAnnotations = pdfUiState.showAnnotations,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                onUndo = { viewModel.undo() },
                                onRedo = { viewModel.redo() },
                                onToolSelected = { viewModel.setAnnotationTool(it) },
                                onColorSelected = { viewModel.setBrushColor(it) },
                                onStrokeWidthSelected = { viewModel.setBrushSize(it) },
                                onHide = { viewModel.toggleAnnotationVisibility() },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp) // Added padding to float more
                                    .navigationBarsPadding()
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                when {
                    pdfUiState.isLoading -> CircularProgressIndicator()
                    pdfUiState.isPasswordProtected -> {
                        // Show a nice skeleton background while waiting for password
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }

                        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        androidx.compose.material3.ModalBottomSheet(
                            onDismissRequest = onNavigateBack,
                            sheetState = sheetState,
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            PasswordUnlockDialog(
                                password = password,
                                isPasswordVisible = isPasswordVisible,
                                onPasswordChange = { password = it },
                                onToggleVisibility = { isPasswordVisible = !isPasswordVisible },
                                onUnlock = {
                                    viewModel.loadPdf(pdfUri, password)
                                    focusManager.clearFocus()
                                },
                                onCancel = onNavigateBack
                            )
                        }
                    }
                    pdfUiState.error != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(pdfUiState.error!!, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Button(onClick = { viewModel.loadPdf(pdfUri) }, modifier = Modifier.padding(top = 16.dp)) {
                                Text("Retry / Unlock")
                            }
                        }
                    }
                    else -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val screenWidth = maxWidth
                            val screenHeight = maxHeight
                            ZoomableBox(
                                modifier = Modifier.fillMaxSize(),
                                minScale = 1f,
                                maxScale = 20f,
                                onScaleChange = { currentScale = it },
                                onOffsetChange = { currentOffset = it },
                                onTap = {
                                    viewModel.clearAllSelections()
                                }
                            ) {
                                if (isVerticalMode) {
                                    LazyColumn(
                                        state = listState,
                                        userScrollEnabled = currentScale <= 1.0f,
                                        modifier = Modifier.requiredSize(
                                            width = if (currentScale < 1f) screenWidth / currentScale else screenWidth,
                                            height = if (currentScale < 1f) screenHeight / currentScale else screenHeight
                                        ),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(pdfUiState.totalPages) { index ->
                                            PdfPageItem(
                                                modifier = Modifier.width(screenWidth),
                                                index = index,
                                                currentScale = currentScale,
                                                currentOffset = currentOffset,
                                                readingMode = pdfUiState.readingMode,
                                                sepiaIntensity = pdfUiState.sepiaIntensity,
                                                textureIntensity = pdfUiState.textureIntensity,
                                                searchResults = pdfUiState.searchResults,
                                                annotations = allAnnotations,
                                                isAnnotationMode = pdfUiState.isAnnotationMode,
                                                showAnnotations = pdfUiState.showAnnotations,
                                                annotationTool = pdfUiState.annotationTool,
                                                brushColor = pdfUiState.brushColor,
                                                brushSize = pdfUiState.brushSize,
                                                haptic = haptic,
                                                visiblePage = visiblePages[index],
                                                viewModel = viewModel
                                            )
                                        }
                                        item { Spacer(modifier = Modifier.height(100.dp)) }
                                    }
                                } else {
                                    androidx.compose.foundation.pager.HorizontalPager(
                                        state = pagerState,
                                        userScrollEnabled = currentScale <= 1.0f,
                                        modifier = Modifier.requiredSize(
                                            width = if (currentScale < 1f) screenWidth / currentScale else screenWidth,
                                            height = if (currentScale < 1f) screenHeight / currentScale else screenHeight
                                        ),
                                        pageSpacing = 16.dp,
                                        contentPadding = PaddingValues(16.dp)
                                    ) { index ->
                                        PdfPageItem(
                                            modifier = Modifier.fillMaxSize(),
                                            index = index,
                                            currentScale = currentScale,
                                            currentOffset = currentOffset,
                                            readingMode = pdfUiState.readingMode,
                                            sepiaIntensity = pdfUiState.sepiaIntensity,
                                            textureIntensity = pdfUiState.textureIntensity,
                                            searchResults = pdfUiState.searchResults,
                                            annotations = allAnnotations,
                                            isAnnotationMode = pdfUiState.isAnnotationMode,
                                            showAnnotations = pdfUiState.showAnnotations,
                                            annotationTool = pdfUiState.annotationTool,
                                            brushColor = pdfUiState.brushColor,
                                            brushSize = pdfUiState.brushSize,
                                            haptic = haptic,
                                            visiblePage = visiblePages[index],
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                            var canvasBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

                            // High-Res overlay moved to PdfPageItem for perfect coordinate alignment

                            if (isVerticalMode) {
                                PdfFastScroller(
                                    listState = listState,
                                    totalPages = pdfUiState.totalPages,
                                    currentScale = currentScale,
                                    viewModel = viewModel,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }

                            // Search Results Navigation
                            if (pdfUiState.searchResults.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(top = 16.dp),
                                    shape = RoundedCornerShape(100.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                    shadowElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${pdfUiState.currentSearchIndex + 1} / ${pdfUiState.searchResults.size}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { viewModel.navigateSearch(false) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev")
                                        }
                                        IconButton(onClick = { viewModel.navigateSearch(true) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                        }
                                    }
                                }
                            }

                            AnnotationBottomBar(
                                isVisible = pdfUiState.isAnnotationMode,
                                currentTool = pdfUiState.annotationTool,
                                currentColor = pdfUiState.brushColor,
                                currentStrokeWidth = pdfUiState.brushSize,
                                showAnnotations = pdfUiState.showAnnotations,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                onUndo = { viewModel.undo() },
                                onRedo = { viewModel.redo() },
                                onToolSelected = { viewModel.setAnnotationTool(it) },
                                onColorSelected = { viewModel.setBrushColor(it) },
                                onStrokeWidthSelected = { viewModel.setBrushSize(it) },
                                onHide = { viewModel.toggleAnnotationVisibility() },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp) // Added padding to float more
                                    .navigationBarsPadding()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReadingModeSheet) {
        ReadingModeBottomSheet(
            readingMode = pdfUiState.readingMode,
            sepiaIntensity = pdfUiState.sepiaIntensity,
            textureIntensity = pdfUiState.textureIntensity,
            onReadingModeChange = viewModel::setReadingMode,
            onSepiaIntensityChange = viewModel::setSepiaIntensity,
            onTextureIntensityChange = viewModel::setTextureIntensity,
            onDismiss = { showReadingModeSheet = false }
        )
    }

    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            bookmarks = pdfUiState.bookmarks,
            onBookmarkClick = { pageNum ->
                viewModel.scrollToPage(pageNum - 1)
                showBookmarksSheet = false
            },
            onDismiss = { showBookmarksSheet = false }
        )
    }

    if (showThumbnailsSheet) {
        ThumbnailGridBottomSheet(
            totalPages = pdfUiState.totalPages,
            currentPage = pdfUiState.currentPage,
            bookmarks = pdfUiState.bookmarks,
            viewModel = viewModel,
            onThumbnailClick = { pageNum ->
                viewModel.scrollToPage(pageNum - 1)
                showThumbnailsSheet = false
            },
            onDismiss = { showThumbnailsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingModeBottomSheet(
    readingMode: ReadingMode,
    sepiaIntensity: Float,
    textureIntensity: Float,
    onReadingModeChange: (ReadingMode) -> Unit,
    onSepiaIntensityChange: (Float) -> Unit,
    onTextureIntensityChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth()) {
            Text("Reading Mode", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = readingMode == ReadingMode.DEFAULT,
                    onClick = { onReadingModeChange(ReadingMode.DEFAULT) },
                    label = { Text("Default") }
                )
                FilterChip(
                    selected = readingMode == ReadingMode.NIGHT,
                    onClick = { onReadingModeChange(ReadingMode.NIGHT) },
                    label = { Text("B&W") }
                )
                FilterChip(
                    selected = readingMode == ReadingMode.SEPIA,
                    onClick = { onReadingModeChange(ReadingMode.SEPIA) },
                    label = { Text("Comfort") }
                )
            }

            if (readingMode == ReadingMode.SEPIA || readingMode == ReadingMode.NIGHT) {
                Spacer(modifier = Modifier.height(24.dp))
                if (readingMode == ReadingMode.SEPIA) {
                    Text("Tint Intensity: ${(sepiaIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = sepiaIntensity,
                        onValueChange = onSepiaIntensityChange,
                        valueRange = 0f..1f
                    )
                }
                Text("Texture Intensity: ${(textureIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = textureIntensity,
                    onValueChange = onTextureIntensityChange,
                    valueRange = 0f..1f
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksBottomSheet(
    bookmarks: List<com.morphdrop.app.data.local.entity.BookmarkEntity>,
    onBookmarkClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth()) {
            Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(bookmarks.size) { index ->
                        val bookmark = bookmarks[index]
                        ListItem(
                            headlineContent = { Text("Page ${bookmark.pageNumber}") },
                            leadingContent = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF008080)) },
                            modifier = Modifier.clickable { onBookmarkClick(bookmark.pageNumber) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailGridBottomSheet(
    totalPages: Int,
    currentPage: Int,
    bookmarks: List<com.morphdrop.app.data.local.entity.BookmarkEntity>,
    viewModel: PdfViewerViewModel,
    onThumbnailClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth().fillMaxHeight(0.8f)) {
            Text("Page Overview", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(totalPages) { index ->
                    val isBookmarked = bookmarks.any { it.pageNumber == index + 1 }
                    ThumbnailItem(
                        index = index,
                        isSelected = index + 1 == currentPage,
                        isBookmarked = isBookmarked,
                        viewModel = viewModel,
                        onClick = { onThumbnailClick(index + 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    index: Int,
    isSelected: Boolean,
    isBookmarked: Boolean,
    viewModel: PdfViewerViewModel,
    onClick: () -> Unit
) {
    val thumbnailsReady by viewModel.thumbnailsReady.collectAsState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, thumbnailsReady.contains(index)) {
        bitmap = viewModel.getThumbnail(index)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (bitmap != null) Modifier.aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat())
                    else Modifier.aspectRatio(0.707f)
                )
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    else Modifier
                ),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            color = Color.White,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isBookmarked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Bookmarked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }
                } else if (bitmap == null) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
    }
}

// Removed getReadingModeColorMatrix

@Composable
fun PasswordUnlockDialog(
    password: String,
    isPasswordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp)
            .fillMaxWidth()
            .imePadding(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "This file is protected",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Password",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        TextField(
            value = password,
            onValueChange = onPasswordChange,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onUnlock() })
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onUnlock) {
                Text("Open")
            }
        }
    }
}

@Composable
fun PdfPageItem(
    modifier: Modifier = Modifier,
    index: Int,
    currentScale: Float,
    currentOffset: Offset,
    readingMode: ReadingMode,
    sepiaIntensity: Float,
    textureIntensity: Float,
    searchResults: List<SearchMatch>,
    annotations: List<com.morphdrop.app.domain.model.PdfAnnotation>,
    isAnnotationMode: Boolean,
    showAnnotations: Boolean,
    annotationTool: AnnotationTool,
    brushColor: androidx.compose.ui.graphics.Color,
    brushSize: Float,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    visiblePage: com.morphdrop.app.ui.screens.pdf.VisiblePageState?,
    viewModel: PdfViewerViewModel
) {
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageLinks by remember { mutableStateOf<List<com.morphdrop.app.data.pdf.PdfLink>>(emptyList()) }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val selectionManager = remember { PdfSelectionManager() }

    val matches = remember(searchResults, index) {
        searchResults.filter { it.pageIndex == index }
    }

    val pageAnnotations = remember(annotations, index) {
        annotations.filter { it.pageIndex == index }
    }

    // Load preview bitmap immediately
    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            previewBitmap = viewModel.renderPreview(index)
            viewModel.loadPageData(index)
            pageLinks = viewModel.getPageLinks(index)
            selectionManager.setPageWords(viewModel.getPageWords(index))
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val windowMetrics = remember {
        val displayMetrics = context.resources.displayMetrics
        androidx.compose.ui.geometry.Size(displayMetrics.widthPixels.toFloat(), displayMetrics.heightPixels.toFloat())
    }

    // Generate Paper Texture once per configuration
    val paperTextureBrush = remember(textureIntensity) {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256 * 256)
        val random = java.util.Random(42) // Fixed seed for consistent texture
        val intensity = textureIntensity
        for (i in pixels.indices) {
            // Subtle noise variation
            val noise = (random.nextFloat() * 15 * intensity).toInt()
            pixels[i] = android.graphics.Color.argb(noise, 0, 0, 0)
        }
        bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 256)
        androidx.compose.ui.graphics.ShaderBrush(
            android.graphics.BitmapShader(
                bitmap,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT
            )
        )
    }

    val colorFilter = remember(readingMode, sepiaIntensity) {
        when (readingMode) {
            ReadingMode.DEFAULT -> null
            ReadingMode.NIGHT -> {
                // True Greyscale (E-ink) mode
                val matrix = android.graphics.ColorMatrix().apply {
                    setSaturation(0f)
                }
                ColorFilter.colorMatrix(ColorMatrix(matrix.array))
            }
            ReadingMode.SEPIA -> {
                val r = 1f - (1f - 244f/255f) * sepiaIntensity
                val g = 1f - (1f - 236f/255f) * sepiaIntensity
                val b = 1f - (1f - 216f/255f) * sepiaIntensity
                ColorFilter.tint(Color(r, g, b), androidx.compose.ui.graphics.BlendMode.Multiply)
            }
        }
    }

    var activeDrawingPath by remember { mutableStateOf<List<com.morphdrop.app.domain.model.PdfAnnotation.Drawing.Point>?>(null) }
    var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }

    BoxWithConstraints(
        modifier = modifier
            .zIndex(if (selectionManager.isSelecting || selectionManager.selectedWords.isNotEmpty()) 1f else 0f)
            .fillMaxWidth()
            .then(
                if (previewBitmap != null) Modifier.aspectRatio(previewBitmap!!.width.toFloat() / previewBitmap!!.height.toFloat())
                else Modifier.aspectRatio(0.707f)
            )
            .background(Color.White)
            .then(
                if (magnifierCenter != Offset.Unspecified && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Modifier.magnifier(
                        sourceCenter = { magnifierCenter },
                        magnifierCenter = { magnifierCenter - Offset(0f, 150f / currentScale) },
                        zoom = 2f
                    )
                } else Modifier
            )
            .then(
                if (isAnnotationMode && showAnnotations) {
                    Modifier.pointerInput(annotationTool, brushColor, brushSize, showAnnotations) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            
                            // If more than 1 finger is down initially, don't start drawing
                            if (currentEvent.changes.size > 1) {
                                return@awaitEachGesture
                            }

                            val scaleX = size.width.toFloat()
                            val scaleY = size.height.toFloat()

                            if (annotationTool == AnnotationTool.DRAW || annotationTool == AnnotationTool.HIGHLIGHT) {
                                activeDrawingPath = listOf(com.morphdrop.app.domain.model.PdfAnnotation.Drawing.Point(down.position.x / scaleX, down.position.y / scaleY))
                            } else if (annotationTool == AnnotationTool.ERASE) {
                                val tapX = down.position.x / scaleX
                                val tapY = down.position.y / scaleY
                                val threshold = 30f / scaleX
                                val toRemove = annotations.firstOrNull { ann ->
                                    if (ann is com.morphdrop.app.domain.model.PdfAnnotation.Drawing) {
                                        ann.pathPoints.any { pt -> ((pt.x - tapX) * (pt.x - tapX) + (pt.y - tapY) * (pt.y - tapY)) < (threshold * threshold) }
                                    } else false
                                }
                                if (toRemove != null) viewModel.removeAnnotation(toRemove.id)
                            }

                            // Consume the down event to prevent parent from panning if it's 1-finger
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                // If at any point we have more than 1 finger, stop drawing/erasing
                                if (event.changes.size > 1) {
                                    activeDrawingPath = null
                                    break
                                }

                                val change = event.changes.first()
                                if (change.pressed) {
                                    change.consume()
                                    val currentPos = change.position
                                    if (annotationTool == AnnotationTool.DRAW || annotationTool == AnnotationTool.HIGHLIGHT) {
                                        activeDrawingPath = activeDrawingPath.orEmpty() + com.morphdrop.app.domain.model.PdfAnnotation.Drawing.Point(currentPos.x / scaleX, currentPos.y / scaleY)
                                    } else if (annotationTool == AnnotationTool.ERASE) {
                                        val tapX = currentPos.x / scaleX
                                        val tapY = currentPos.y / scaleY
                                        val threshold = 30f / scaleX
                                        val toRemove = annotations.firstOrNull { ann ->
                                            if (ann is com.morphdrop.app.domain.model.PdfAnnotation.Drawing) {
                                                ann.pathPoints.any { pt -> ((pt.x - tapX) * (pt.x - tapX) + (pt.y - tapY) * (pt.y - tapY)) < (threshold * threshold) }
                                            } else false
                                        }
                                        if (toRemove != null) viewModel.removeAnnotation(toRemove.id)
                                    }
                                } else {
                                    // Finger up - finalize drawing
                                    if ((annotationTool == AnnotationTool.DRAW || annotationTool == AnnotationTool.HIGHLIGHT) && activeDrawingPath != null) {
                                        val finalPath = activeDrawingPath!!
                                        if (finalPath.size > 1) {
                                            viewModel.addDrawingAnnotation(
                                                pageIndex = index,
                                                color = brushColor.copy(alpha = if (annotationTool == AnnotationTool.HIGHLIGHT) 0.5f else 1f),
                                                strokeWidth = brushSize,
                                                pathPoints = finalPath
                                            )
                                        }
                                    }
                                    activeDrawingPath = null
                                    break
                                }
                            }
                        }
                    }
                } else Modifier
            )
            .pointerInput(isAnnotationMode) {
                if (isAnnotationMode) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        selectionManager.startSelection(offset, size.toSize())
                        magnifierCenter = offset
                    },
                    onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, _ ->
                        change.consume()
                        val previousSelectedCount = selectionManager.selectedWords.size
                        selectionManager.updateSelection(change.position, size.toSize())
                        if (selectionManager.selectedWords.size != previousSelectedCount) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                        magnifierCenter = change.position
                    },
                    onDragEnd = {
                        selectionManager.stopSelection()
                        magnifierCenter = Offset.Unspecified
                    },
                    onDragCancel = {
                        selectionManager.clearSelection()
                        magnifierCenter = Offset.Unspecified
                    }
                )
            }
            .onGloballyPositioned { coordinates ->
                if (currentScale > 1.0f) {
                    val bounds = coordinates.boundsInWindow()
                    // Screen bounds (ignoring status bar for now)
                    val screenRect = androidx.compose.ui.geometry.Rect(0f, 0f, windowMetrics.width, windowMetrics.height)
                    val intersection = bounds.intersect(screenRect)

                    if (!intersection.isEmpty) {
                        val leftNormalized = (intersection.left - bounds.left) / bounds.width
                        val topNormalized = (intersection.top - bounds.top) / bounds.height
                        val rightNormalized = (intersection.right - bounds.left) / bounds.width
                        val bottomNormalized = (intersection.bottom - bounds.top) / bounds.height

                        val viewport = android.graphics.RectF(
                            leftNormalized, topNormalized, rightNormalized, bottomNormalized
                        )
                        viewModel.updateVisiblePage(index, bounds, viewport, currentScale)
                    } else {
                        viewModel.removeVisiblePage(index)
                    }
                } else {
                    viewModel.removeVisiblePage(index)
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (previewBitmap != null) {
                Box {
                    Crossfade<Bitmap?>(targetState = previewBitmap, label = "bitmap_fade") { targetBitmap ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw preview bitmap across full bounds
                            if (targetBitmap != null) {
                                drawImage(
                                    image = targetBitmap.asImageBitmap(),
                                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    colorFilter = colorFilter
                                )
                            }
                        }
                    }

                    val highResBitmap = if (currentScale > 1.0f && visiblePage != null && visiblePage.renderedScale == currentScale) visiblePage.highResBitmap else null
                    
                    // 1. High-Res Overlay Layer
                    if (highResBitmap != null && visiblePage != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val viewport = visiblePage.renderedViewport ?: visiblePage.normalizedViewport
                            if (viewport != null) {
                                val dstLeft = (viewport.left * size.width).toInt()
                                val dstTop = (viewport.top * size.height).toInt()
                                val dstWidth = (viewport.width() * size.width).toInt()
                                val dstHeight = (viewport.height() * size.height).toInt()

                                drawImage(
                                    image = highResBitmap.asImageBitmap(),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(dstLeft, dstTop),
                                    dstSize = androidx.compose.ui.unit.IntSize(dstWidth, dstHeight),
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    colorFilter = colorFilter
                                )
                            }
                        }
                    }

                    // 2. Saved Annotations Layer
                    if (showAnnotations) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val scaleX = size.width
                            val scaleY = size.height

                            pageAnnotations.forEach { annotation ->
                                if (annotation is com.morphdrop.app.domain.model.PdfAnnotation.Highlight) {
                                    annotation.boundingBoxes.forEach { rect ->
                                        drawRect(
                                            color = annotation.color.copy(alpha = 0.4f),
                                            topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                                            size = androidx.compose.ui.geometry.Size(rect.width * scaleX, rect.height * scaleY)
                                        )
                                    }
                                } else if (annotation is com.morphdrop.app.domain.model.PdfAnnotation.Drawing) {
                                    val path = androidx.compose.ui.graphics.Path()
                                    if (annotation.pathPoints.isNotEmpty()) {
                                        path.moveTo(annotation.pathPoints[0].x * scaleX, annotation.pathPoints[0].y * scaleY)
                                        for (i in 1 until annotation.pathPoints.size) {
                                            path.lineTo(annotation.pathPoints[i].x * scaleX, annotation.pathPoints[i].y * scaleY)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = annotation.color,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = annotation.strokeWidth.dp.toPx(), 
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round, 
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 3. Active Drawing Layer (Highest Reactivity)
                    if (activeDrawingPath != null && activeDrawingPath!!.isNotEmpty() && showAnnotations) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val scaleX = size.width
                            val scaleY = size.height
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo(activeDrawingPath!![0].x * scaleX, activeDrawingPath!![0].y * scaleY)
                            for (i in 1 until activeDrawingPath!!.size) {
                                path.lineTo(activeDrawingPath!![i].x * scaleX, activeDrawingPath!![i].y * scaleY)
                            }
                            drawPath(
                                path = path,
                                color = brushColor.copy(alpha = if (annotationTool == AnnotationTool.HIGHLIGHT) 0.5f else 1f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = brushSize.dp.toPx(), 
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round, 
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }


                    // Paper Texture Overlay
                    if (readingMode != ReadingMode.DEFAULT && textureIntensity > 0f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                brush = paperTextureBrush,
                                blendMode = androidx.compose.ui.graphics.BlendMode.Multiply
                            )
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        matches.forEach { match ->
                            match.bounds.forEach { rectF ->
                                val pWidth = maxOf(match.pageWidth, 1f)
                                val pHeight = maxOf(match.pageHeight, 1f)
                                val left = (rectF.left / pWidth) * size.width
                                val top = (rectF.top / pHeight) * size.height
                                val right = (rectF.right / pWidth) * size.width
                                val bottom = (rectF.bottom / pHeight) * size.height

                                drawRect(
                                    color = Color.Yellow.copy(alpha = 0.4f),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = Fill
                                )
                            }
                        }
                    }

                    // Hyperlinks Overlay
                    if (pageLinks.isNotEmpty()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val w = maxWidth.value
                            val h = maxHeight.value
                            pageLinks.forEach { link ->
                                val left = (link.bounds.left * w).dp
                                val top = (link.bounds.top * h).dp
                                val width = (link.bounds.width() * w).dp
                                val height = (link.bounds.height() * h).dp

                                Box(
                                    modifier = Modifier
                                        .padding(start = left, top = top)
                                        .size(width, height)
                                        .clickable {
                                            try {
                                                uriHandler.openUri(link.uri)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                )
                            }
                        }
                    }

                    // Text Selection Overlay
                    if (selectionManager.selectedWords.isNotEmpty()) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var isTap = true
                                        while (true) {
                                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                                            if (event.changes.any { it.isConsumed }) {
                                                isTap = false
                                            }
                                            if (!event.changes.any { it.pressed }) {
                                                break
                                            }
                                        }
                                        if (isTap) {
                                            viewModel.clearAllSelections()
                                        }
                                    }
                                }
                        ) {
                            LaunchedEffect(Unit) {
                                viewModel.clearSelectionEvent.collect {
                                    if (selectionManager.selectedWords.isNotEmpty()) {
                                        selectionManager.clearSelection()
                                    }
                                }
                            }
                            val wPx = constraints.maxWidth.toFloat()
                            val hPx = constraints.maxHeight.toFloat()

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                selectionManager.selectedWords.forEach { word ->
                                    val left = (word.bounds.left * size.width)
                                    val top = (word.bounds.top * size.height)
                                    val width = (word.bounds.width() * size.width)
                                    val height = (word.bounds.height() * size.height)

                                    drawRect(
                                        color = Color(0xFF0A58F6).copy(alpha = 0.3f),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height)
                                    )
                                }
                            }

                            val startWord = selectionManager.selectedWords.first()
                            val endWord = selectionManager.selectedWords.last()

                            val startLeft = (startWord.bounds.left * maxWidth.value).dp
                            val startTop = (startWord.bounds.top * maxHeight.value).dp
                            val startBottom = (startWord.bounds.bottom * maxHeight.value).dp

                            val endRight = (endWord.bounds.right * maxWidth.value).dp
                            val endBottom = (endWord.bounds.bottom * maxHeight.value).dp
                            val endTop = (endWord.bounds.top * maxHeight.value).dp

                            val visualHandleSize = 12.dp / currentScale
                            val hitBoxSize = 48.dp / currentScale
                            val hitBoxOffset = 24.dp / currentScale

                            // Start Handle
                            Box(
                                modifier = Modifier
                                    .offset(x = startLeft - hitBoxOffset, y = startBottom) // Positioned at bottom-left of start word
                                    .size(hitBoxSize) // Large hit box for easy grabbing
                                    .pointerInput(Unit) {
                                        var dragPos = Offset.Zero
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            selectionManager.startHandleDrag(PdfSelectionManager.HandleType.START)
                                            dragPos = Offset(startWord.bounds.left * wPx, startWord.bounds.top * hPx)

                                            var isDragging = true
                                            while (isDragging) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change != null) {
                                                    if (change.pressed) {
                                                        val dragAmount = change.position - change.previousPosition
                                                        change.consume()
                                                        if (dragAmount != Offset.Zero) {
                                                            dragPos += dragAmount
                                                            magnifierCenter = dragPos
                                                            val previousSelectedCount = selectionManager.selectedWords.size
                                                            selectionManager.updateSelection(dragPos, androidx.compose.ui.geometry.Size(wPx, hPx))
                                                            if (selectionManager.selectedWords.size != previousSelectedCount) {
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    } else {
                                                        isDragging = false
                                                        selectionManager.stopHandleDrag()
                                                        magnifierCenter = Offset.Unspecified
                                                    }
                                                } else {
                                                    isDragging = false
                                                    selectionManager.stopHandleDrag()
                                                    magnifierCenter = Offset.Unspecified
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(modifier = Modifier.size(visualHandleSize).background(Color(0xFF0A58F6), CircleShape))
                            }

                            // End Handle
                            Box(
                                modifier = Modifier
                                    .offset(x = endRight - hitBoxOffset, y = endBottom) // Positioned at bottom-right of end word
                                    .size(hitBoxSize) // Large hit box for easy grabbing
                                    .pointerInput(Unit) {
                                        var dragPos = Offset.Zero
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            selectionManager.startHandleDrag(PdfSelectionManager.HandleType.END)
                                            dragPos = Offset(endWord.bounds.right * wPx, endWord.bounds.bottom * hPx)

                                            var isDragging = true
                                            while (isDragging) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change != null) {
                                                    if (change.pressed) {
                                                        val dragAmount = change.position - change.previousPosition
                                                        change.consume()
                                                        if (dragAmount != Offset.Zero) {
                                                            dragPos += dragAmount
                                                            magnifierCenter = dragPos
                                                            val previousSelectedCount = selectionManager.selectedWords.size
                                                            selectionManager.updateSelection(dragPos, androidx.compose.ui.geometry.Size(wPx, hPx))
                                                            if (selectionManager.selectedWords.size != previousSelectedCount) {
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    } else {
                                                        isDragging = false
                                                        selectionManager.stopHandleDrag()
                                                        magnifierCenter = Offset.Unspecified
                                                    }
                                                } else {
                                                    isDragging = false
                                                    selectionManager.stopHandleDrag()
                                                    magnifierCenter = Offset.Unspecified
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(modifier = Modifier.size(visualHandleSize).background(Color(0xFF0A58F6), CircleShape))
                            }

                            // Context Menu Popup
                            if (!selectionManager.isSelecting) {
                                val estimatedMenuHeight = 120.dp / currentScale
                                val estimatedMenuWidth = 200.dp / currentScale
                                
                                val showAbove = endBottom + (16.dp / currentScale) + estimatedMenuHeight > maxHeight
                                
                                val menuTop = if (showAbove) {
                                    startTop - estimatedMenuHeight - (8.dp / currentScale)
                                } else {
                                    endBottom + (16.dp / currentScale)
                                }
                                
                                // Ensure menu doesn't go off horizontally
                                val menuLeft = startLeft.coerceIn(0.dp, maxWidth - estimatedMenuWidth)
                                
                                Box(
                                    modifier = Modifier
                                        .offset(x = menuLeft, y = menuTop.coerceIn(0.dp, maxHeight - estimatedMenuHeight))
                                        .zIndex(1f)
                                        .graphicsLayer(
                                            scaleX = 1f / currentScale,
                                            scaleY = 1f / currentScale,
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                        )
                                        .padding(bottom = (8.dp / currentScale))
                                ) {
                                    Column(
                                        modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                            shadowElevation = 8.dp,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = {
                                                    selectionManager.selectAll()
                                                }) {
                                                    Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                                                }
                                                IconButton(onClick = {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Copied Text", selectionManager.getSelectedText())
                                                    clipboard.setPrimaryClip(clip)
                                                    viewModel.showToast("Copied to clipboard")
                                                    selectionManager.clearSelection()
                                                }) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                                }
                                                IconButton(onClick = {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, selectionManager.getSelectedText())
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Text"))
                                                    selectionManager.clearSelection()
                                                }) {
                                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                                }
                                                IconButton(onClick = {
                                                    val query = selectionManager.getSelectedText()
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                                                        putExtra(android.app.SearchManager.QUERY, query)
                                                    }
                                                    context.startActivity(intent)
                                                    selectionManager.clearSelection()
                                                }) {
                                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                            shadowElevation = 8.dp,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val colors = listOf(Color.Yellow, Color.Green, Color.Cyan, Color.Magenta)
                                                colors.forEach { color ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .background(color, CircleShape)
                                                            .clickable {
                                                                viewModel.addHighlightAnnotation(
                                                                    pageIndex = index,
                                                                    color = color,
                                                                    rects = selectionManager.selectedWords.map {
                                                                        androidx.compose.ui.geometry.Rect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)
                                                                    }
                                                                )
                                                                selectionManager.clearSelection()
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Skeleton loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray.copy(alpha = 0.3f))
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
fun TocDrawerSheet(
    tocList: List<com.morphdrop.app.data.pdf.PdfTocItem>,
    onNavigate: (Int) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Table of Contents",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            androidx.compose.material3.HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (tocList.isEmpty()) {
                    item {
                        Text(
                            text = "No Table of Contents available.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item {
                        Column {
                            tocList.forEach { item ->
                                TocItemRow(item = item, depth = 0, onNavigate = onNavigate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TocItemRow(
    item: com.morphdrop.app.data.pdf.PdfTocItem,
    depth: Int,
    onNavigate: (Int) -> Unit
) {
    Column {
        var isExpanded by remember { mutableStateOf(false) }
        val hasChildren = item.children.isNotEmpty()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.pageIndex >= 0) {
                        onNavigate(item.pageIndex)
                    } else if (hasChildren) {
                        isExpanded = !isExpanded
                    }
                }
                .padding(start = (16 + depth * 16).dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = "Expand"
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(24.dp).padding(end = 8.dp))
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (item.pageIndex >= 0) {
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isExpanded && hasChildren) {
            item.children.forEach { child ->
                TocItemRow(item = child, depth = depth + 1, onNavigate = onNavigate)
            }
        }
    }
}
