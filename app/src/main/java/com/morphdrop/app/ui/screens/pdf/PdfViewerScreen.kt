package com.morphdrop.app.ui.screens.pdf

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val uiState by viewModel.uiState.collectAsState()
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
                    placeholder = { Text("1 - ${uiState.totalPages}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val page = jumpPageInput.toIntOrNull()
                    if (page != null && page in 1..uiState.totalPages) {
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                                text = uiState.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .combinedClickable(
                                        onClick = { /* Tap ignored */ },
                                        onLongClick = { 
                                            viewModel.showToast(uiState.fileName)
                                        }
                                    )
                            )
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { viewModel.printPdf(context) }) {
                                Icon(Icons.Default.Print, contentDescription = "Print")
                            }
                            IconButton(onClick = { viewModel.downloadPdf() }) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.isPasswordProtected -> {
                    // Gray background overlay matching reference
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
                uiState.error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Button(onClick = { viewModel.loadPdf(pdfUri) }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Retry / Unlock")
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ZoomableBox(maxScale = 5f) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.totalPages) { index ->
                                    PdfPageItem(index, viewModel)
                                }
                                item { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                        
                        PdfFastScroller(
                            listState = listState,
                            totalPages = uiState.totalPages,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )

                        // Floating Page Indicator Pill
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .clickable { 
                                    jumpPageInput = uiState.currentPage.toString()
                                    showJumpDialog = true 
                                }
                        ) {
                            Text(
                                text = "Page ${uiState.currentPage} of ${uiState.totalPages}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }

                        // Search Results Navigation
                        if (uiState.searchResults.isNotEmpty()) {
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
                                        "${uiState.currentSearchIndex + 1} / ${uiState.searchResults.size}",
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
}

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
    val uiState by viewModel.uiState.collectAsState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageText by remember { mutableStateOf("") }
    
    val matches = remember(uiState.searchResults, index) {
        uiState.searchResults.filter { it.pageIndex == index }
    }

    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            bitmap = viewModel.renderPage(index)
            pageText = viewModel.getPageText(index)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (bitmap != null) Modifier.aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat())
                else Modifier.aspectRatio(0.707f)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                SelectionContainer {
                    Box {
                        // Invisible selectable text layer
                        Text(
                            text = pageText,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0f),
                            fontSize = 6.sp, // Small font to pack characters
                            lineHeight = 6.sp
                        )
                        
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        
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
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}
