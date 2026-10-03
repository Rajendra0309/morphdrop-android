package com.morphdrop.app.ui.screens.home

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import com.morphdrop.app.ui.screens.history.resolveConversionType
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.MainViewModel
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.LastOpenedPdf
import com.morphdrop.app.ui.components.ConversionCard
import com.morphdrop.app.ui.components.dashedBorder
import com.morphdrop.app.ui.components.EmptyState
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.MorphScaffold
import com.morphdrop.app.ui.components.SectionHeader
import com.morphdrop.app.ui.components.TransformIndicator
import com.morphdrop.app.ui.navigation.Screen
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.util.TimeUtils
import com.morphdrop.app.util.FileHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToConfig: (conversionTypeId: String) -> Unit = {},
    /** Long-press on a tool card: quick conversion with the tool's saved preset. */
    onQuickConvert: (conversionTypeId: String) -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    mainViewModel: MainViewModel,
    gridColumns: Int = 2,
    hasNavigationRail: Boolean = false
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredTypes by viewModel.filteredConversionTypes.collectAsStateWithLifecycle()
    val favoriteTypes by viewModel.favoriteConversionTypes.collectAsStateWithLifecycle()
    val recentConversions by viewModel.recentConversions.collectAsStateWithLifecycle()
    val lastOpenedPdf by mainViewModel.lastOpenedPdf.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()

    HomeScreenContent(
        searchQuery = searchQuery,
        filteredTypes = filteredTypes,
        favoriteTypes = favoriteTypes,
        recentConversions = recentConversions,
        lastOpenedPdf = lastOpenedPdf,
        onOpenLastPdf = {
            homeScope.launch {
                val opened = mainViewModel.openLastOpenedPdf(context)
                if (!opened) {
                    Toast.makeText(context, "File no longer available", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDismissLastPdf = mainViewModel::clearLastOpenedPdf,
        setSearchFabVisibility = mainViewModel::setSearchFabVisibility,
        setOnSearchFabClick = mainViewModel::setOnSearchFabClick,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleFavorite = viewModel::onToggleFavorite,
        onNavigateToConfig = onNavigateToConfig,
        onQuickConvert = onQuickConvert,
        onOpenRoute = onOpenRoute,
        onNavigate = onNavigate,
        gridColumns = gridColumns,
        hasNavigationRail = hasNavigationRail
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    searchQuery: String,
    filteredTypes: List<ConversionType>,
    favoriteTypes: List<ConversionType>,
    recentConversions: List<ConversionHistoryEntity>,
    lastOpenedPdf: LastOpenedPdf? = null,
    onOpenLastPdf: () -> Unit = {},
    onDismissLastPdf: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    /** Long-press on a tool card: quick conversion with the tool's saved preset. */
    onQuickConvert: (String) -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    onNavigate: (String) -> Unit,
    setSearchFabVisibility: (Boolean) -> Unit = {},
    setOnSearchFabClick: ((() -> Unit)?) -> Unit = {},
    gridColumns: Int = 2,
    hasNavigationRail: Boolean = false
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Scroll-driven search button beside the navbar: appears once the search
    // bar has scrolled out of view; tapping scrolls back up and focuses search.
    val isScrolledDown by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 }
    }
    LaunchedEffect(isScrolledDown) {
        setSearchFabVisibility(isScrolledDown)
    }
    LaunchedEffect(Unit) {
        setOnSearchFabClick {
            coroutineScope.launch {
                gridState.animateScrollToItem(0)
                delay(100)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
    // Re-sync when the screen is revealed again (back stack / tab switch).
    DisposableEffect(Unit) {
        setSearchFabVisibility(isScrolledDown)
        onDispose { }
    }

    // Drop-zone file picker: pick any file, then choose the tool for it.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileName by remember { mutableStateOf("") }
    var toolCandidates by remember { mutableStateOf<List<ConversionType>>(emptyList()) }
    var showToolPicker by remember { mutableStateOf(false) }

    val dropZonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            coroutineScope.launch {
                val candidates = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    candidateToolsForUri(context, uri)
                }
                val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    FileHelper.getFileName(context, uri)
                }
                if (candidates.size == 1) {
                    onOpenRoute(routeForToolWithUri(candidates.first().id, uri))
                } else {
                    pendingUri = uri
                    pendingFileName = name
                    toolCandidates = candidates
                    showToolPicker = true
                }
            }
        }
    }

    if (showToolPicker && pendingUri != null) {
        ToolPickerDialog(
            fileName = pendingFileName.ifBlank { "Selected file" },
            candidates = toolCandidates,
            onDismiss = { showToolPicker = false },
            onPick = { tool ->
                showToolPicker = false
                onOpenRoute(routeForToolWithUri(tool.id, pendingUri!!))
            }
        )
    }

    MorphScaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        topBar = {
            MorphDropTopAppBar(
                title = "MorphDrop",
                scrollBehavior = scrollBehavior,
                showTagline = true,
                isBrand = true
            )
        },
        reserveFloatingNavSpace = !hasNavigationRail
    ) { innerPadding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 48.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // In-home search bar, always at the top.
            item(span = { GridItemSpan(maxLineSpan) }) {
                MorphDropSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    active = false,
                    onActiveChange = { },
                    focusRequester = focusRequester,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Continue reading: jump back into the last-viewed PDF at the
            // exact page. Shown only when not searching.
            if (searchQuery.isBlank() && lastOpenedPdf != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueReadingCard(
                        lastPdf = lastOpenedPdf,
                        onOpen = onOpenLastPdf,
                        onDismiss = onDismissLastPdf
                    )
                }
            }

            // Intent strip: "what do you want to do?" one-tap shortcuts to the
            // most-used tools. Visible only when not searching.
            if (searchQuery.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    IntentStrip(onIntentClick = onNavigateToConfig)
                }
            }

            if (searchQuery.isNotBlank()) {
                if (filteredTypes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No tools match \"$searchQuery\"",
                            body = "Try a different keyword, like \"PDF\" or \"image\".",
                            actionLabel = "Clear search",
                            onAction = { onSearchQueryChange("") }
                        )
                    }
                } else {
                    items(filteredTypes, key = { it.id }) { item ->
                        ConversionCard(
                            conversionType = item,
                            onClick = { onNavigateToConfig(item.id) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQuickConvert(item.id)
                            },
                            onFavoriteToggle = { onToggleFavorite(item.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // (a) Drop zone launchpad
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DropZoneCard(onPickFile = { dropZonePicker.launch(arrayOf("*/*")) })
                }

                // (b) Recent conversions
                if (recentConversions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "Recent",
                            icon = Icons.Default.History,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(recentConversions, key = { it.id }) { entity ->
                                RecentConversionChip(
                                    entity = entity,
                                    onClick = {
                                        val tool = resolveConversionType(entity.conversionTypeId, entity.conversionType)
                                        if (tool != null) onNavigateToConfig(tool.id)
                                        else onNavigate(Screen.History.route)
                                    }
                                )
                            }
                        }
                    }
                }

                // (c) Favorites rail
                if (favoriteTypes.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "Favorites",
                            icon = Icons.Default.Favorite,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(favoriteTypes, key = { it.id }) { item ->
                                ConversionCard(
                                    conversionType = item,
                                    onClick = { onNavigateToConfig(item.id) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onQuickConvert(item.id)
                                    },
                                    onFavoriteToggle = { onToggleFavorite(item.id) },
                                    modifier = Modifier.width(170.dp),
                                    isCompact = true
                                )
                            }
                        }
                    }
                }

                // (d) Categorized tools
                val categories = listOf(
                    ConversionType.CATEGORY_CONVERSIONS,
                    ConversionType.CATEGORY_PDF_TOOLS,
                    ConversionType.CATEGORY_IMAGE_TOOLS
                )
                categories.forEach { categoryName ->
                    val itemsInCategory = filteredTypes.filter { it.category == categoryName }
                    if (itemsInCategory.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(itemsInCategory, key = { it.id }) { item ->
                            ConversionCard(
                                conversionType = item,
                                onClick = { onNavigateToConfig(item.id) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onQuickConvert(item.id)
                                },
                                onFavoriteToggle = { onToggleFavorite(item.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dashed drop-zone card that opens the system file picker. */
@Composable
private fun DropZoneCard(onPickFile: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onPickFile,
        shape = RoundedCornerShape(20.dp),
        color = scheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .dashedBorder(2.dp, scheme.primary.copy(alpha = 0.6f), 20.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Drop a file",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pick any file — we'll suggest the right tools",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Recent conversion chip: morph pill (when resolvable) + filename + age. */
@Composable
private fun RecentConversionChip(
    entity: ConversionHistoryEntity,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tool = remember(entity.conversionTypeId, entity.conversionType) {
        resolveConversionType(entity.conversionTypeId, entity.conversionType)
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.width(210.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tool != null) {
                TransformIndicator(conversionType = tool, compact = true)
            } else {
                Text(
                    text = entity.conversionType,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = entity.inputFileName.ifBlank { "Converted file" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = TimeUtils.formatRelativeTime(entity.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/** Dialog listing candidate tools for a picked file. */
@Composable
private fun ToolPickerDialog(
    fileName: String,
    candidates: List<ConversionType>,
    onDismiss: () -> Unit,
    onPick: (ConversionType) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Convert this file",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                candidates.forEach { tool ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = { onPick(tool) })
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TransformIndicator(conversionType = tool, compact = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tools that can handle a picked file, by extension. PDF pages run through
 * the dedicated PDF screens, which host their own pickers and route via
 * ConversionConfig URIs.
 */
private fun candidateToolsForUri(context: android.content.Context, uri: Uri): List<ConversionType> {
    val name = FileHelper.getFileName(context, uri)
    val ext = name.substringAfterLast('.', "").lowercase()
    val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    val ids = when {
        ext in listOf("png", "jpg", "jpeg", "webp", "bmp") ->
            listOf("images_to_pdf", "image_converter", "compress_images", "metadata_editor")
        ext == "pdf" -> listOf(
            "pdf_to_images", "merge_pdf", "split_pdf", "compress_pdf",
            "protect_pdf", "unlock_pdf", "page_editor", "watermark_pdf",
            "page_numbers_pdf", "rotate_pdf", "metadata_editor"
        )
        ext in listOf("xlsx", "xls", "csv") -> listOf("excel_to_pdf", "metadata_editor")
        ext == "txt" -> listOf("txt_to_pdf", "metadata_editor")
        ext in listOf("md", "markdown") -> listOf("md_to_pdf", "metadata_editor")
        // Fallback for unknown extensions: sniff the MIME type.
        mime.startsWith("image/") ->
            listOf("images_to_pdf", "image_converter", "compress_images", "metadata_editor")
        mime == "application/pdf" -> listOf("pdf_to_images", "merge_pdf", "compress_pdf", "metadata_editor")
        mime.startsWith("text/") -> listOf("txt_to_pdf", "metadata_editor")
        else -> listOf("metadata_editor")
    }
    val byId = ConversionType.defaultList.associateBy { it.id }
    return ids.mapNotNull { byId[it] }
}

/** Route that carries the picked URI into the right screen for [toolId]. */
private fun routeForToolWithUri(toolId: String, uri: Uri): String {
    // createRoute() encodes the URI itself — pass it raw.
    val raw = uri.toString()
    return when (toolId) {
        "watermark_pdf" -> Screen.PdfWatermark.createRoute(raw)
        "page_numbers_pdf" -> Screen.PdfPageNumbers.createRoute(raw)
        "compress_pdf" -> Screen.PdfCompress.createRoute(raw)
        "rotate_pdf" -> Screen.PdfRotate.createRoute(raw)
        else -> Screen.ConversionConfig.createRoute(toolId, raw)
    }
}


/**
 * Intent strip — answers "what do you want to do?" with one-tap shortcuts to
 * the most-used tools, instead of making the user hunt through the grid.
 */
@Composable
private fun IntentStrip(
    onIntentClick: (conversionTypeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val intents = remember {
        listOf(
            "compress_pdf" to "Compress a PDF",
            "merge_pdf" to "Merge PDFs",
            "ocr_text_extractor" to "Extract text",
            "images_to_pdf" to "Images to PDF",
            "image_converter" to "Convert images",
            "protect_pdf" to "Protect a PDF"
        ).mapNotNull { (id, label) ->
            ConversionType.defaultList.firstOrNull { it.id == id }?.let { it to label }
        }
    }

    Column(modifier = modifier.padding(top = 4.dp)) {
        Text(
            text = "What do you want to do?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(intents, key = { it.first.id }) { (type, label) ->
                SuggestionChip(
                    onClick = { onIntentClick(type.id) },
                    label = { Text(label) },
                    icon = {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }
}

/**
 * "Pick up where you left off" card: reopens the last-viewed PDF at the
 * exact page the user left off on.
 */
@Composable
private fun ContinueReadingCard(
    lastPdf: LastOpenedPdf,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = remember(lastPdf.uri) { runCatching { Uri.parse(lastPdf.uri) }.getOrNull() }
    // Prefer the name stored when the PDF was open; fall back to a live query on IO.
    val fileName by produceState(
        initialValue = lastPdf.displayName.takeIf { it.isNotBlank() } ?: "Document.pdf",
        lastPdf.uri,
        lastPdf.displayName
    ) {
        if (lastPdf.displayName.isBlank() && uri != null) {
            val queried = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { FileHelper.getFileName(context, uri) }.getOrNull()
            }
            if (!queried.isNullOrBlank()) {
                value = queried
            }
        }
    }
    val relativeTime = remember(lastPdf.timestamp) {
        if (lastPdf.timestamp > 0) TimeUtils.formatRelativeTime(lastPdf.timestamp) else null
    }
    val pageLabel = remember(lastPdf.page, lastPdf.totalPages) {
        if (lastPdf.totalPages > 0) "Page ${lastPdf.page + 1} of ${lastPdf.totalPages}"
        else "Page ${lastPdf.page + 1}"
    }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Pick up where you left off: $fileName, $pageLabel"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pick up where you left off",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(pageLabel)
                        if (relativeTime != null) append(" • $relativeTime")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(name = "Small Phone", showBackground = true, showSystemUi = true, device = "spec:width=360dp,height=640dp,dpi=480")
@Composable
fun HomeScreenSmallPreview() {
    MorphDropTheme(darkTheme = false) {
        HomeScreenContent(
            searchQuery = "",
            filteredTypes = ConversionType.defaultList,
            favoriteTypes = ConversionType.defaultList.take(2),
            recentConversions = emptyList(),
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {},
            onNavigate = {}
        )
    }
}

@Preview(name = "Large Phone", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun HomeScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        HomeScreenContent(
            searchQuery = "",
            filteredTypes = ConversionType.defaultList,
            favoriteTypes = ConversionType.defaultList.take(2),
            recentConversions = emptyList(),
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {},
            onNavigate = {}
        )
    }
}

@Preview(name = "Foldable", showBackground = true, showSystemUi = true, device = "spec:width=673dp,height=841dp,dpi=480")
@Composable
fun HomeScreenFoldablePreview() {
    MorphDropTheme(darkTheme = false) {
        HomeScreenContent(
            searchQuery = "",
            filteredTypes = ConversionType.defaultList,
            favoriteTypes = ConversionType.defaultList.take(2),
            recentConversions = emptyList(),
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {},
            onNavigate = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun HomeScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        HomeScreenContent(
            searchQuery = "",
            filteredTypes = ConversionType.defaultList,
            favoriteTypes = ConversionType.defaultList.take(2),
            recentConversions = emptyList(),
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {},
            onNavigate = {}
        )
    }
}
