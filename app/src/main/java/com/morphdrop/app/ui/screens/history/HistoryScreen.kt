package com.morphdrop.app.ui.screens.history

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.MainViewModel
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.ui.components.EmptyStateAnimation
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (historyId: Long) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
    mainViewModel: MainViewModel
) {
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    // Stabilize callbacks to prevent lambda mismatch crashes
    val stabilizedOnDetail = remember(onNavigateToDetail) {
        { item: ConversionHistoryEntity -> onNavigateToDetail(item.id) }
    }
    
    HistoryScreenContent(
        historyList = historyList,
        searchQuery = searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearAll = { viewModel.clearAll() },
        onDeleteItem = { viewModel.deleteItem(it) },
        onItemClick = stabilizedOnDetail,
        setSearchFabVisibility = { mainViewModel.setSearchFabVisibility(it) },
        setOnSearchFabClick = { mainViewModel.setOnSearchFabClick(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreenContent(
    historyList: List<ConversionHistoryEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onDeleteItem: (ConversionHistoryEntity) -> Unit,
    onItemClick: (ConversionHistoryEntity) -> Unit,
    setSearchFabVisibility: (Boolean) -> Unit,
    setOnSearchFabClick: ((() -> Unit)?) -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all conversion history logs?") },
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

    val hasActions = historyList.isNotEmpty() || searchQuery.isNotEmpty()

    // Track search FAB visibility and sync with MainViewModel
    val showSearchFab by remember {
        derivedStateOf { 
            listState.firstVisibleItemIndex > 0
        }
    }

    // Reactive sync: ensure visibility is updated whenever it changes OR on screen entry
    LaunchedEffect(showSearchFab) {
        setSearchFabVisibility(showSearchFab)
    }

    // Re-register click listener whenever the screen is active
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

    // Force re-sync when screen is revealed (e.g. from background or backstack)
    DisposableEffect(Unit) {
        setSearchFabVisibility(showSearchFab)
        onDispose {
            setSearchFabVisibility(false)
            setOnSearchFabClick(null)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "History",
                scrollBehavior = scrollBehavior,
                showBackArrow = false,
                hasActions = hasActions,
                actions = {
                    if (hasActions) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
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

                if (historyList.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No matching history",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                } else if (historyList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(bottom = innerPadding.calculateTopPadding()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                EmptyStateAnimation(
                                    icon = Icons.Default.History,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "No history yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Your converted files will appear here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(historyList, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onDelete = { onDeleteItem(item) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: ConversionHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
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
                            if (item.success) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = if (item.success) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.conversionType.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.inputFileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (item.success) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
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
            onItemClick = {},
            setSearchFabVisibility = {},
            setOnSearchFabClick = {}
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
            onItemClick = {},
            setSearchFabVisibility = {},
            setOnSearchFabClick = {}
        )
    }
}
