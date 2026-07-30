package com.morphdrop.app.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.components.ConversionCard
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.navigation.Screen
import com.morphdrop.app.ui.components.MorphDropBottomNavigation
import com.morphdrop.app.MainViewModel
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToConfig: (conversionTypeId: String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    mainViewModel: MainViewModel // Added mainViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredTypes by viewModel.filteredConversionTypes.collectAsStateWithLifecycle()
    val favoriteTypes by viewModel.favoriteConversionTypes.collectAsStateWithLifecycle()

    HomeScreenContent(
        searchQuery = searchQuery,
        filteredTypes = filteredTypes,
        favoriteTypes = favoriteTypes,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleFavorite = viewModel::onToggleFavorite,
        onNavigateToConfig = onNavigateToConfig,
        onNavigate = onNavigate,
        setSearchFabVisibility = mainViewModel::setSearchFabVisibility,
        setOnSearchFabClick = mainViewModel::setOnSearchFabClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    searchQuery: String,
    filteredTypes: List<ConversionType>,
    favoriteTypes: List<ConversionType>,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    onNavigate: (String) -> Unit,
    setSearchFabVisibility: (Boolean) -> Unit,
    setOnSearchFabClick: ((() -> Unit)?) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Track search FAB visibility and sync with MainViewModel
    val showSearchFab by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(showSearchFab) {
        setSearchFabVisibility(showSearchFab)
    }

    LaunchedEffect(Unit) {
        setOnSearchFabClick {
            coroutineScope.launch {
                gridState.animateScrollToItem(0)
                kotlinx.coroutines.delay(100)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            setSearchFabVisibility(false)
            setOnSearchFabClick(null)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "MorphDrop",
                scrollBehavior = scrollBehavior,
                showTagline = true
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (filteredTypes.isEmpty()) {
                EmptySearchState(query = searchQuery, onClear = { onSearchQueryChange("") })
            } else {
                ToolsGrid(
                    gridState = gridState,
                    focusRequester = focusRequester,
                    filteredTypes = filteredTypes,
                    favoriteTypes = favoriteTypes,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onNavigateToConfig = onNavigateToConfig,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun ToolsGrid(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    focusRequester: FocusRequester,
    filteredTypes: List<ConversionType>,
    favoriteTypes: List<ConversionType>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 80.dp), // Increased padding for floating navbar
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar integrated as an item in the list
        item(span = { GridItemSpan(2) }) {
            MorphDropSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                active = false,
                onActiveChange = { },
                focusRequester = focusRequester,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp)
            )
        }

        if (searchQuery.isBlank() && favoriteTypes.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(
                    title = "Favorites", 
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(span = { GridItemSpan(2) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(favoriteTypes, key = { it.id }) { item ->
                        ConversionCard(
                            conversionType = item,
                            onClick = { onNavigateToConfig(item.id) },
                            onFavoriteToggle = { onToggleFavorite(item.id) },
                            modifier = Modifier.width(160.dp),
                            isCompact = true
                        )
                    }
                }
            }
        }

        val categories = listOf(
            ConversionType.CATEGORY_CONVERSIONS,
            ConversionType.CATEGORY_PDF_TOOLS,
            ConversionType.CATEGORY_IMAGE_TOOLS,
            ConversionType.CATEGORY_UNOPTIMIZED
        )

        categories.forEach { categoryName ->
            val itemsInCategory = filteredTypes.filter { it.category == categoryName }
            if (itemsInCategory.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 16.dp)
                    )
                }
                items(itemsInCategory, key = { it.id }) { item ->
                    ConversionCard(
                        conversionType = item,
                        onClick = { onNavigateToConfig(item.id) },
                        onFavoriteToggle = { onToggleFavorite(item.id) },
                        modifier = Modifier.padding(
                            start = if (itemsInCategory.indexOf(item) % 2 == 0) 16.dp else 0.dp,
                            end = if (itemsInCategory.indexOf(item) % 2 == 1) 16.dp else 0.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptySearchState(query: String, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tools match \"$query\"",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(text = "Clear Search", onClick = onClear)
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
            onNavigate = {},
            setSearchFabVisibility = {},
            setOnSearchFabClick = {}
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
            onNavigate = {},
            setSearchFabVisibility = {},
            setOnSearchFabClick = {}
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
            onNavigate = {},
            setSearchFabVisibility = {},
            setOnSearchFabClick = {}
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
            onNavigate = {},
            setSearchFabVisibility = {},
            setOnSearchFabClick = {}
        )
    }
}
