package com.morphdrop.app.ui.screens.pdf

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.ui.components.PdfFastScroller
import com.morphdrop.app.ui.components.ZoomableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val pdfUiState by viewModel.pdfUiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
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
    var currentScale by remember { mutableStateOf(1f) }

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
    }
    
    LaunchedEffect(pdfUri) {
        viewModel.loadPdf(pdfUri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PdfViewerEvent.ScrollToPage -> {
                    coroutineScope.launch {
                        listState.animateScrollToItem(event.pageIndex)
                    }
                }
                is PdfViewerEvent.ShowSnackbar -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex) {
        viewModel.updateCurrentPage(firstVisibleIndex)
    }

    // Handle Back Button
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
        viewModel.search("")
        focusManager.clearFocus()
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
    
    Scaffold(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            visible = !isSearchActive,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onNavigateBack) {
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
                                IconButton(onClick = { showReadingModeSheet = true }) {
                                    Icon(Icons.Default.SettingsBrightness, contentDescription = "Reading Mode")
                                }
                                IconButton(onClick = { showThumbnailsSheet = true }) {
                                    Icon(Icons.Default.GridView, contentDescription = "Thumbnails")
                                }
                                var showOverflowMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Bookmarks") },
                                            leadingIcon = { Icon(Icons.Default.Bookmarks, null) },
                                            onClick = { 
                                                showOverflowMenu = false
                                                showBookmarksSheet = true 
                                            }
                                        )
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
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            leadingIcon = { Icon(Icons.Default.Share, null) },
                                            onClick = { 
                                                showOverflowMenu = false
                                                viewModel.sharePdf(context) 
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF808080).copy(alpha = 0.8f))
                            .imePadding(),
                        contentAlignment = Alignment.Center
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        ZoomableBox(
                            maxScale = 5f, 
                            onTap = { viewModel.toggleImmersiveMode() },
                            onScaleChange = { currentScale = it }
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(pdfUiState.totalPages) { index ->
                                    PdfPageItem(index, viewModel)
                                }
                                item { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                        
                        PdfFastScroller(
                            listState = listState,
                            totalPages = pdfUiState.totalPages,
                            bookmarks = pdfUiState.bookmarks,
                            currentScale = currentScale,
                            onBookmarkToggle = { viewModel.toggleBookmarkForPage(it) },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )

                        // Search Results Navigation
                        if (pdfUiState.searchResults.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
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
                    ThumbnailItem(
                        index = index,
                        isSelected = index + 1 == currentPage,
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
    viewModel: PdfViewerViewModel,
    onClick: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            bitmap = viewModel.renderThumbnail(index)
        }
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
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
private fun PasswordUnlockDialog(
    password: String,
    isPasswordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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
                TextButton(onClick = onUnlock) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
fun PdfPageItem(index: Int, viewModel: PdfViewerViewModel) {
    val pdfUiState by viewModel.pdfUiState.collectAsState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageText by remember { mutableStateOf("") }
    
    val matches = remember(pdfUiState.searchResults, index) {
        pdfUiState.searchResults.filter { it.pageIndex == index }
    }

    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            bitmap = viewModel.renderPage(index)
            pageText = viewModel.getPageText(index)
        }
    }

    // Generate Paper Texture once per configuration
    val paperTextureBrush = remember(pdfUiState.sepiaIntensity) {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256 * 256)
        val random = java.util.Random(42) // Fixed seed for consistent texture
        val intensity = pdfUiState.sepiaIntensity
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

    val colorFilter = remember(pdfUiState.readingMode, pdfUiState.sepiaIntensity) {
        when (pdfUiState.readingMode) {
            ReadingMode.DEFAULT -> null
            ReadingMode.NIGHT -> {
                // True Greyscale (E-ink) mode
                val matrix = android.graphics.ColorMatrix().apply {
                    setSaturation(0f)
                }
                ColorFilter.colorMatrix(ColorMatrix(matrix.array))
            }
            ReadingMode.SEPIA -> {
                val r = 1f - (1f - 244f/255f) * pdfUiState.sepiaIntensity
                val g = 1f - (1f - 236f/255f) * pdfUiState.sepiaIntensity
                val b = 1f - (1f - 216f/255f) * pdfUiState.sepiaIntensity
                ColorFilter.tint(Color(r, g, b), androidx.compose.ui.graphics.BlendMode.Multiply)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (bitmap != null) Modifier.aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat())
                else Modifier.aspectRatio(0.707f)
            )
            .background(Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Box {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter
                    )

                        // Paper Texture Overlay
                        if (pdfUiState.readingMode != ReadingMode.DEFAULT && pdfUiState.sepiaIntensity > 0f) {
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
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

