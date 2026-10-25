package com.morphdrop.app.ui.screens.home

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.LastOpenedPdf
import com.morphdrop.app.ui.components.ConversionCard
import com.morphdrop.app.ui.components.dashedBorder
import com.morphdrop.app.ui.components.EmptyState
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.components.MorphScaffold
import com.morphdrop.app.ui.components.SectionHeader
import com.morphdrop.app.ui.components.TransformIndicator
import com.morphdrop.app.ui.navigation.Screen
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.util.TimeUtils
import com.morphdrop.app.util.FileHelper
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
    val lastOpenedPdf by mainViewModel.lastOpenedPdf.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()

    HomeScreenContent(
        searchQuery = searchQuery,
        filteredTypes = filteredTypes,
        favoriteTypes = favoriteTypes,
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
    // Saved across navigation: when the user opens a tool and comes back,
    // Home is recomposed from the back-stack entry's saved state, and a
    // plain remember grid state would restart at the top every time.
    val gridState = rememberSaveable(saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Quick-return header: visible at the top, hidden while scrolling
    // down, and shown again on any upward scroll — not only when the
    // grid reaches the very top.
    var headerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(gridState) {
        var previousIndex = 0
        var previousOffset = 0
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            headerVisible = when {
                index == 0 && offset == 0 -> true
                index > previousIndex ||
                    (index == previousIndex && offset > previousOffset) -> false
                else -> true
            }
            previousIndex = index
            previousOffset = offset
        }
    }

    // Search button beside the bottom nav, only while the search bar has
    // scrolled out of view: tapping it jumps to the top and focuses
    // search. MainActivity clears it when leaving the searchable screens.
    val isScrolledDown by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(isScrolledDown) {
        setSearchFabVisibility(isScrolledDown)
    }
    // The navbar search shortcut cannot focus the field straight after
    // scrolling: while the header is hidden, AnimatedVisibility has
    // removed the search bar from composition, so an immediate
    // requestFocus() lands on nothing and the keyboard never opens.
    // Instead the tap only scrolls and raises this flag; the effect
    // below focuses once the header is back and attached.
    var pendingSearchFocus by remember { mutableStateOf(false) }
    LaunchedEffect(headerVisible, pendingSearchFocus) {
        if (pendingSearchFocus && headerVisible) {
            // Let the header's enter animation attach the field first.
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
            pendingSearchFocus = false
        }
    }
    LaunchedEffect(Unit) {
        setOnSearchFabClick {
            coroutineScope.launch {
                gridState.scrollToItem(0)
                pendingSearchFocus = true
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            setSearchFabVisibility(false)
            setOnSearchFabClick(null)
        }
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
        modifier = Modifier.imePadding(),
        reserveFloatingNavSpace = !hasNavigationRail
    ) { innerPadding ->
        // Header lives OUTSIDE the grid, above it. That is the search
        // focus fix: in the previous layout the brand item was removed
        // from the grid the moment a query started, shifting the search
        // item to a new index, which destroyed the focused field after
        // the first character on the All view. Here the search bar's
        // composition never changes while typing, and the brand header
        // is the same on every tab.
        val filterSaver = Saver<HomeFilter, String>(
            save = { it.name },
            restore = { HomeFilter.valueOf(it) }
        )
        var selectedFilter by rememberSaveable(stateSaver = filterSaver) {
            mutableStateOf(HomeFilter.ALL)
        }
        val favoriteIds = favoriteTypes.map { it.id }.toSet()
        fun matchesFilter(type: ConversionType): Boolean = when (selectedFilter) {
            HomeFilter.ALL -> true
            HomeFilter.FAVORITES -> type.id in favoriteIds
            HomeFilter.CONVERSIONS -> type.category == ConversionType.CATEGORY_CONVERSIONS
            HomeFilter.PDF_TOOLS -> type.category == ConversionType.CATEGORY_PDF_TOOLS
            HomeFilter.IMAGE_TOOLS -> type.category == ConversionType.CATEGORY_IMAGE_TOOLS
        }
        val searching = searchQuery.isNotBlank()
        // Search always covers every tool: a query typed while a category
        // chip is selected must still find tools from other categories.
        val visibleTypes = if (searching) {
            filteredTypes
        } else {
            filteredTypes.filter { matchesFilter(it) }
        }

        // A filter or query change starts from the top — but only when
        // it actually changed. On first composition (e.g. coming back
        // from a tool) the saved scroll position must be left alone,
        // or returning would always jump to the top.
        var previousFilter by remember { mutableStateOf<HomeFilter?>(null) }
        var previousQuery by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(selectedFilter, searchQuery) {
            val changed = (previousFilter != null && previousFilter != selectedFilter) ||
                (previousQuery != null && previousQuery != searchQuery)
            previousFilter = selectedFilter
            previousQuery = searchQuery
            if (changed &&
                (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
            ) {
                gridState.scrollToItem(0)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = headerVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            start = 16.dp,
                            end = 16.dp
                        )
                ) {
                    Text(
                        text = "MorphDrop",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Drop. Transform. Done.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MorphDropSearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        active = false,
                        onActiveChange = { },
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 32.dp, top = 4.dp, bottom = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedFilter == HomeFilter.ALL,
                                    onClick = { selectedFilter = HomeFilter.ALL },
                                    label = { Text("All ${ConversionType.defaultList.size}") },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Show all ${ConversionType.defaultList.size} tools"
                                    }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == HomeFilter.FAVORITES,
                                    onClick = { selectedFilter = HomeFilter.FAVORITES },
                                    label = { Text("Favorites") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Show favorite tools, ${favoriteTypes.size} pinned"
                                    }
                                )
                            }
                            val categoryFilters = listOf(
                                HomeFilter.CONVERSIONS to ConversionType.CATEGORY_CONVERSIONS,
                                HomeFilter.PDF_TOOLS to ConversionType.CATEGORY_PDF_TOOLS,
                                HomeFilter.IMAGE_TOOLS to ConversionType.CATEGORY_IMAGE_TOOLS
                            )
                            categoryFilters.forEach { (filter, categoryName) ->
                                val count = ConversionType.defaultList.count { it.category == categoryName }
                                item {
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text("$categoryName $count") },
                                        modifier = Modifier.semantics {
                                            contentDescription = "Filter by $categoryName, $count tools"
                                        }
                                    )
                                }
                            }
                        }
                        // Fade at the right edge: a hint the chip row
                        // scrolls. The 32dp end padding keeps the last
                        // chip out from under it, so it is never clipped.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(28.dp)
                                .height(40.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 48.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (searching) {
                    item(key = "search-count", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${visibleTypes.size} result${if (visibleTypes.size == 1) "" else "s"} for \"$searchQuery\"" +
                                if (selectedFilter != HomeFilter.ALL) " · searched in all tools" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    if (visibleTypes.isEmpty()) {
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
                        items(visibleTypes, key = { it.id }) { item ->
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
                } else if (selectedFilter == HomeFilter.FAVORITES) {
                    if (favoriteTypes.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                icon = Icons.Default.Favorite,
                                title = "No favorites yet",
                                body = "Long-press a tool or tap its heart to pin it here.",
                                actionLabel = "Browse all tools",
                                onAction = { selectedFilter = HomeFilter.ALL }
                            )
                        }
                    } else {
                        items(favoriteTypes, key = { it.id }) { item ->
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
                } else if (selectedFilter != HomeFilter.ALL) {
                    val categoryName = when (selectedFilter) {
                        HomeFilter.CONVERSIONS -> ConversionType.CATEGORY_CONVERSIONS
                        HomeFilter.PDF_TOOLS -> ConversionType.CATEGORY_PDF_TOOLS
                        else -> ConversionType.CATEGORY_IMAGE_TOOLS
                    }
                    item(key = "filter-header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "$categoryName · ${visibleTypes.size} tools",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(visibleTypes, key = { it.id }) { item ->
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
                } else {
                    // All: continue reading, drop zone, favorites rail, then
                    // the full tool grid grouped by category. The intent
                    // strip and the Recent rail were removed for a cleaner
                    // home — recents live in History, one tap away in the
                    // bottom nav.
                    if (lastOpenedPdf != null) {
                        item(key = "continue-reading", span = { GridItemSpan(maxLineSpan) }) {
                            ContinueReadingCard(
                                lastPdf = lastOpenedPdf,
                                onOpen = onOpenLastPdf,
                                onDismiss = onDismissLastPdf
                            )
                        }
                    }

                    item(key = "drop-zone", span = { GridItemSpan(maxLineSpan) }) {
                        DropZoneCard(onPickFile = { dropZonePicker.launch(arrayOf("*/*")) })
                    }

                    if (favoriteTypes.isNotEmpty()) {
                        item(key = "favorites-header", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = "Favorites",
                                icon = Icons.Default.Favorite,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        item(key = "favorites-rail", span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(favoriteTypes.take(6), key = { it.id }) { item ->
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
                                if (favoriteTypes.size > 6) {
                                    item {
                                        FavoritesSeeAllCard(
                                            count = favoriteTypes.size,
                                            onClick = { selectedFilter = HomeFilter.FAVORITES }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val categories = listOf(
                        ConversionType.CATEGORY_CONVERSIONS,
                        ConversionType.CATEGORY_PDF_TOOLS,
                        ConversionType.CATEGORY_IMAGE_TOOLS
                    )
                    categories.forEach { categoryName ->
                        val itemsInCategory = visibleTypes.filter { it.category == categoryName }
                        if (itemsInCategory.isNotEmpty()) {
                            item(key = "category-$categoryName", span = { GridItemSpan(maxLineSpan) }) {
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
}

/** Home grid filter: everything, favorites, or one tool category. */
private enum class HomeFilter {
    ALL, FAVORITES, CONVERSIONS, PDF_TOOLS, IMAGE_TOOLS
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

/** Trailing rail card shown when more favorites exist than the rail holds. */
@Composable
private fun FavoritesSeeAllCard(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.width(110.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "See all $count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary
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
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .padding(20.dp)
            ) {
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
                // The list scrolls inside a capped dialog: with 13 tools
                // for a PDF, a plain Column overflowed the screen and the
                // last options were unreachable. A scrollbar thumb plus a
                // "scroll for more" pill make the scrolling discoverable.
                val listState = rememberLazyListState()
                val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            val info = listState.layoutInfo
                            val total = info.totalItemsCount
                            val visible = info.visibleItemsInfo.size
                            if (total > visible && visible > 0) {
                                val thumbHeight = size.height * visible / total
                                val firstIndex = info.visibleItemsInfo.first().index
                                val thumbY = size.height * firstIndex / total
                                drawRoundRect(
                                    color = thumbColor,
                                    topLeft = Offset(size.width - 4.dp.toPx(), thumbY),
                                    size = Size(4.dp.toPx(), thumbHeight),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        // Bottom clearance so the "Scroll for more" pill
                        // never covers the last row's content.
                        contentPadding = PaddingValues(bottom = 48.dp)
                    ) {
                    items(candidates, key = { it.id }) { tool ->
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
                    if (listState.canScrollForward) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scroll for more",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
            listOf("images_to_pdf", "image_converter", "image_resize", "compress_images", "metadata_editor")
        ext in listOf("heic", "heif") ->
            listOf("heic_to_image", "metadata_editor")
        ext == "pdf" -> listOf(
            "pdf_to_text", "pdf_to_docx", "pdf_to_csv", "pdf_to_excel", "ocr_searchable_pdf", "pdf_to_images", "merge_pdf", "split_pdf", "compress_pdf",
            "protect_pdf", "unlock_pdf", "page_editor", "watermark_pdf",
            "page_numbers_pdf", "rotate_pdf", "metadata_editor"
        )
        ext == "xlsx" -> listOf("excel_to_pdf", "xlsx_to_csv", "metadata_editor")
        ext == "csv" -> listOf("csv_to_pdf", "csv_to_xlsx", "excel_to_pdf", "metadata_editor")
        ext == "xls" -> listOf("excel_to_pdf", "metadata_editor")
        ext == "txt" -> listOf("txt_to_pdf", "metadata_editor")
        ext in listOf("md", "markdown") -> listOf("md_to_pdf", "metadata_editor")
        // Fallback for unknown extensions: sniff the MIME type.
        mime.startsWith("image/") ->
            listOf("images_to_pdf", "image_converter", "compress_images", "metadata_editor")
        mime == "application/pdf" -> listOf("pdf_to_text", "pdf_to_docx", "pdf_to_images", "merge_pdf", "compress_pdf", "metadata_editor")
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
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {},
            onNavigate = {}
        )
    }
}
