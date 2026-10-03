package com.morphdrop.app.ui.screens.history

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.Job
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.MainViewModel
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.components.EmptyState
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.util.TimeUtils
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (historyId: Long) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
    mainViewModel: MainViewModel,
    isExpanded: Boolean = false,
    /** Navigates to a Conversion Config screen pre-loaded with the sample file. */
    onTrySampleConvert: (String) -> Unit = {}
) {
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val screenScope = rememberCoroutineScope()

    // Stabilize callbacks to prevent lambda mismatch crashes
    val stabilizedOnDetail = remember(onNavigateToDetail) {
        { item: ConversionHistoryEntity -> onNavigateToDetail(item.id) }
    }

    HistoryScreenContent(
        historyList = historyList,
        searchQuery = searchQuery,
        filter = filter,
        selectedIds = selectedIds,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onTogglePin = viewModel::togglePin,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = { viewModel.selectAll(historyList.map { it.id }) },
        onClearSelection = viewModel::clearSelection,
        onDeleteSelected = { visible -> viewModel.deleteSelected(visible) },
        onRestoreItems = viewModel::restoreItems,
        onClearAll = { viewModel.clearAll() },
        onDeleteItem = { viewModel.deleteItem(it) },
        onRestoreItem = { viewModel.restoreItem(it) },
        onTrySample = {
            screenScope.launch {
                openSamplePdf(context)?.let { uri ->
                    onTrySampleConvert(uri.toString())
                }
            }
        },
        onItemClick = stabilizedOnDetail,
        setSearchFabVisibility = mainViewModel::setSearchFabVisibility,
        setOnSearchFabClick = mainViewModel::setOnSearchFabClick,
        isExpanded = isExpanded
    )
}

/**
 * Generates a small one-page sample PDF in cache and returns its content URI,
 * or null if generation failed. Powers the History empty-state "Try a sample
 * file" action — fully offline.
 */
private suspend fun openSamplePdf(context: Context): android.net.Uri? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val doc = android.graphics.pdf.PdfDocument()
        try {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 28f
                isFakeBoldText = true
            }
            val bodyPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 16f
            }
            canvas.drawText("MorphDrop Sample", 72f, 120f, titlePaint)
            canvas.drawText("This is a sample PDF generated on-device.", 72f, 170f, bodyPaint)
            canvas.drawText("Open it, annotate it, or convert it with any", 72f, 200f, bodyPaint)
            canvas.drawText("of the PDF tools to see MorphDrop in action.", 72f, 224f, bodyPaint)
            doc.finishPage(page)
            val file = java.io.File(context.cacheDir, "morphdrop-sample.pdf")
            java.io.FileOutputStream(file).use { doc.writeTo(it) }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { doc.close() }
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreenContent(
    historyList: List<ConversionHistoryEntity>,
    searchQuery: String,
    filter: HistoryFilter = HistoryFilter.ALL,
    selectedIds: Set<Long> = emptySet(),
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit = {},
    onTogglePin: (ConversionHistoryEntity) -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: suspend (List<ConversionHistoryEntity>) -> List<ConversionHistoryEntity> = { emptyList() },
    onRestoreItems: (List<ConversionHistoryEntity>) -> Unit = {},
    onTrySample: () -> Unit = {},
    onClearAll: () -> Unit,
    onDeleteItem: (ConversionHistoryEntity) -> Unit,
    onRestoreItem: (ConversionHistoryEntity) -> Unit,
    onItemClick: (ConversionHistoryEntity) -> Unit,
    setSearchFabVisibility: (Boolean) -> Unit = {},
    setOnSearchFabClick: ((() -> Unit)?) -> Unit = {},
    isExpanded: Boolean = false
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    /** Non-empty selection = selection mode (multi-select bulk delete). */
    val selectionMode = selectedIds.isNotEmpty()

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all conversion history logs? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Scroll-driven search button beside the navbar: appears once the search
    // bar has scrolled out of view; tapping scrolls back up and focuses search.
    val showSearchFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    LaunchedEffect(showSearchFab) {
        setSearchFabVisibility(showSearchFab)
    }
    LaunchedEffect(Unit) {
        setOnSearchFabClick {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
                kotlinx.coroutines.delay(100)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
    // Force re-sync when screen is revealed (e.g. from background or backstack).
    DisposableEffect(Unit) {
        setSearchFabVisibility(showSearchFab)
        onDispose { }
    }

    val hasActions = historyList.isNotEmpty() || searchQuery.isNotEmpty()

    val groupedHistory = remember(historyList) { groupHistoryByDay(historyList) }

    BackHandler(enabled = selectionMode) {
        onClearSelection()
    }

    Scaffold(
        modifier = if (selectionMode) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Lift the snackbar above the floating bottom navbar (rendered by
        // MainActivity): otherwise the message hides behind the pill and the
        // Undo action is untappable. No lift needed when the rail is used.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (isExpanded) 24.dp else 88.dp)
            )
        },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        // Select all visible
                        IconButton(
                            onClick = onSelectAll,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select all",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        // Delete selected
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val deleted = onDeleteSelected(historyList)
                                    if (deleted.isNotEmpty()) {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "${deleted.size} deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestoreItems(deleted)
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                message = "Items restored",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else {
                MorphDropTopAppBar(
                    title = "History",
                    scrollBehavior = scrollBehavior,
                    showBackArrow = false,
                    hasActions = hasActions,
                    actions = {
                        if (hasActions) {
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Clear all history") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showClearDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // On large screens keep the list at a readable width, centered.
                    .then(if (isExpanded) Modifier.widthIn(max = 900.dp) else Modifier),
                contentPadding = PaddingValues(bottom = if (isExpanded) 24.dp else 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search bar integrated as an item in the list
                item {
                    MorphDropSearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        active = false,
                        onActiveChange = { },
                        placeholderText = "Search history...",
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 12.dp)
                    )
                }

                // Filter chips: All / PDF / Images / Excel
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        items(HistoryFilter.entries) { chipFilter ->
                            FilterChip(
                                selected = filter == chipFilter,
                                onClick = { onFilterChange(chipFilter) },
                                label = { Text(chipFilter.label) },
                                leadingIcon = if (filter == chipFilter) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                val isFiltering = searchQuery.isNotEmpty() || filter != HistoryFilter.ALL
                if (historyList.isEmpty() && isFiltering) {
                    item {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No matching history",
                            body = "Try a different search term or filter",
                            actionLabel = "Clear search & filters",
                            onAction = {
                                onSearchQueryChange("")
                                onFilterChange(HistoryFilter.ALL)
                            }
                        )
                    }
                } else if (historyList.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.History,
                            title = "No history yet",
                            body = "Your converted files will appear here",
                            actionLabel = "Try a sample file",
                            onAction = onTrySample
                        )
                    }
                } else {
                    groupedHistory.forEach { (sectionTitle, sectionItems) ->
                        stickyHeader {
                            HistorySectionHeader(title = sectionTitle)
                        }
                        items(sectionItems, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            SwipeableHistoryItem(
                                item = item,
                                selected = isSelected,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) onToggleSelection(item.id)
                                    else onItemClick(item)
                                },
                                onLongClick = { onToggleSelection(item.id) },
                                onTogglePin = { onTogglePin(item) },
                                onDelete = {
                                    onDeleteItem(item)
                                    snackbarJob?.cancel()
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarJob = coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "History deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestoreItem(item)
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                message = "Item restored",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.SwipeableHistoryItem(
    item: ConversionHistoryEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val density = LocalDensity.current
    var isDismissing by remember(item.id) { mutableStateOf(false) }
    val dismissState = remember(item.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            confirmValueChange = { value: SwipeToDismissBoxValue ->
                if (value == SwipeToDismissBoxValue.EndToStart && !isDismissing) {
                    isDismissing = true
                    currentOnDelete()
                    true
                } else {
                    false
                }
            },
            positionalThreshold = { distance: Float -> distance * 0.5f }
        )
    }

    LaunchedEffect(item.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
        isDismissing = false
    }

    // In selection mode swipe is disabled so gestures don't fight; the card
    // is rendered directly with its selection highlight.
    if (selectionMode) {
        HistoryItemCard(
            item = item,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            onTogglePin = onTogglePin,
            modifier = modifier.animateItem()
        )
    } else {
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier.animateItem(),
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                // Full-height delete affordance (always >= 48dp) revealed on swipe
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        ) {
            HistoryItemCard(
                item = item,
                selected = false,
                onClick = onClick,
                onLongClick = onLongClick,
                onTogglePin = onTogglePin,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: ConversionHistoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val relativeTime = remember(item.timestamp) {
        TimeUtils.formatRelativeTime(item.timestamp)
    }
    val outputName = remember(item.displayName, item.outputFileNames) {
        item.displayName.ifBlank {
            TimeUtils.formatOutputDisplayName(item.outputFileNames)
        }
    }

    val matchedType = remember(item.conversionTypeId, item.conversionType) {
        resolveConversionType(item.conversionTypeId, item.conversionType)
    }
    val itemIcon = matchedType?.icon ?: Icons.Default.Description

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "View conversion details",
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else if (item.success) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else itemIcon,
                        contentDescription = if (selected) "Selected" else null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                               else if (item.success) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.inputFileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Neutral type label — never raw neon format color as body text
                    Text(
                        text = (matchedType?.name ?: item.conversionType).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HistoryStatusChip(success = item.success)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Output: $outputName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Unpin conversion",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pin conversion",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(-45f)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryStatusChip(success: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (success) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (success) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (success) "Successful" else "Failed",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (success) "Done" else "Failed",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Groups history into Pinned / Today / Yesterday / Earlier sections,
 * preserving timestamp-descending order within each section. Pinned
 * favorites always come first.
 */
private fun groupHistoryByDay(
    list: List<ConversionHistoryEntity>
): List<Pair<String, List<ConversionHistoryEntity>>> {
    if (list.isEmpty()) return emptyList()

    val pinned = list.filter { it.isPinned }
    val unpinned = list.filterNot { it.isPinned }

    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = todayCal.timeInMillis
    val startOfYesterday = (todayCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis

    val today = mutableListOf<ConversionHistoryEntity>()
    val yesterday = mutableListOf<ConversionHistoryEntity>()
    val earlier = mutableListOf<ConversionHistoryEntity>()

    unpinned.forEach { item ->
        when {
            item.timestamp >= startOfToday -> today.add(item)
            item.timestamp >= startOfYesterday -> yesterday.add(item)
            else -> earlier.add(item)
        }
    }

    return buildList {
        if (pinned.isNotEmpty()) add("Pinned" to pinned)
        if (today.isNotEmpty()) add("Today" to today)
        if (yesterday.isNotEmpty()) add("Yesterday" to yesterday)
        if (earlier.isNotEmpty()) add("Earlier" to earlier)
    }
}

/**
 * Resolves a ConversionType for a history row. Prefers the stable
 * [conversionTypeId] stored on the entity; falls back to fuzzy-matching the
 * legacy [conversionType] string for rows saved before the id existed.
 */
internal fun resolveConversionType(typeId: String?, typeStr: String): ConversionType? {
    if (!typeId.isNullOrBlank()) {
        ConversionType.defaultList.find { it.id == typeId }?.let { return it }
    }
    return resolveConversionType(typeStr)
}

internal fun resolveConversionType(typeStr: String): ConversionType? {
    val normalized = typeStr.trim().lowercase()
    return ConversionType.defaultList.firstOrNull {
        it.id.equals(normalized, ignoreCase = true) ||
        it.name.equals(typeStr.trim(), ignoreCase = true)
    } ?: when {
        normalized.contains("batch ocr") || normalized == "batch_ocr" ->
            ConversionType.defaultList.find { it.id == "batch_ocr" }
        normalized.contains("ocr") || normalized.contains("extract text") ->
            ConversionType.defaultList.find { it.id == "ocr_text_extractor" }
        normalized.contains("pdf to image") || normalized == "pdf_to_images" ->
            ConversionType.defaultList.find { it.id == "pdf_to_images" }
        normalized.contains("images to pdf") || normalized == "images_to_pdf" || normalized == "image_to_pdf" ->
            ConversionType.defaultList.find { it.id == "images_to_pdf" }
        normalized.contains("excel") || normalized == "excel_to_pdf" ->
            ConversionType.defaultList.find { it.id == "excel_to_pdf" }
        normalized.contains("text to pdf") || normalized == "txt_to_pdf" || normalized == "text_to_pdf" ->
            ConversionType.defaultList.find { it.id == "txt_to_pdf" }
        normalized.contains("markdown to pdf") || normalized == "md_to_pdf" ->
            ConversionType.defaultList.find { it.id == "md_to_pdf" }
        normalized.contains("markdown") || normalized == "markdown_editor" ->
            ConversionType.defaultList.find { it.id == "markdown_editor" }
        normalized.contains("batch") || normalized == "batch_pdf" ->
            ConversionType.defaultList.find { it.id == "batch_pdf" }
        normalized.contains("merge") || normalized == "merge_pdf" || normalized == "merge_pdfs" ->
            ConversionType.defaultList.find { it.id == "merge_pdf" }
        normalized.contains("split") || normalized == "split_pdf" ->
            ConversionType.defaultList.find { it.id == "split_pdf" }
        normalized.contains("compress pdf") || normalized == "compress_pdf" ->
            ConversionType.defaultList.find { it.id == "compress_pdf" }
        normalized.contains("compress image") || normalized == "compress_images" ->
            ConversionType.defaultList.find { it.id == "compress_images" }
        normalized.contains("protect") || normalized == "protect_pdf" ->
            ConversionType.defaultList.find { it.id == "protect_pdf" }
        normalized.contains("unlock") || normalized == "unlock_pdf" ->
            ConversionType.defaultList.find { it.id == "unlock_pdf" }
        normalized.contains("organize") || normalized.contains("page_editor") ->
            ConversionType.defaultList.find { it.id == "page_editor" }
        normalized.contains("watermark") || normalized == "watermark_pdf" ->
            ConversionType.defaultList.find { it.id == "watermark_pdf" }
        normalized.contains("page number") || normalized == "page_numbers_pdf" ->
            ConversionType.defaultList.find { it.id == "page_numbers_pdf" }
        normalized.contains("rotate") || normalized == "rotate_pdf" ->
            ConversionType.defaultList.find { it.id == "rotate_pdf" }
        normalized.contains("image converter") || normalized == "image_converter" ->
            ConversionType.defaultList.find { it.id == "image_converter" }
        normalized.contains("meta") || normalized == "metadata_editor" ->
            ConversionType.defaultList.find { it.id == "metadata_editor" }
        else -> null
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun HistoryScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        HistoryScreenContent(
            historyList = listOf(
                ConversionHistoryEntity(
                    id = 1,
                    conversionType = "PDF to Images",
                    conversionTypeId = "pdf_to_images",
                    inputFileName = "Work_Presentation.pdf",
                    outputFileNames = "page_1.png, page_2.png",
                    timestamp = System.currentTimeMillis() - 3600000,
                    success = true
                ),
                ConversionHistoryEntity(
                    id = 2,
                    conversionType = "Word to PDF",
                    inputFileName = "Broken_File.docx",
                    outputFileNames = "-",
                    timestamp = System.currentTimeMillis() - 86400000,
                    success = false
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            onClearAll = {},
            onDeleteItem = {},
            onRestoreItem = {},
            onItemClick = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun HistoryScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        HistoryScreenContent(
            historyList = listOf(
                ConversionHistoryEntity(
                    id = 1,
                    conversionType = "Images to PDF",
                    conversionTypeId = "images_to_pdf",
                    inputFileName = "Summer_Vacation.zip",
                    outputFileNames = "Summer_Vacation.pdf",
                    timestamp = System.currentTimeMillis() - 120000,
                    success = true
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            onClearAll = {},
            onDeleteItem = {},
            onRestoreItem = {},
            onItemClick = {}
        )
    }
}
