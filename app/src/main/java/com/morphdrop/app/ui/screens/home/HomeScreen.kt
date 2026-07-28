package com.morphdrop.app.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.components.ConversionCard
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@Composable
fun HomeScreen(
    onNavigateToConfig: (conversionTypeId: String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
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
        onNavigateToConfig = onNavigateToConfig
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
    onNavigateToConfig: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MorphDrop",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Drop. Transform. Done.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MorphDropSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                active = isSearchActive,
                onActiveChange = { isSearchActive = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filteredTypes.isEmpty()) {
                EmptySearchState(query = searchQuery, onClear = { onSearchQueryChange("") })
            } else {
                ToolsGrid(
                    filteredTypes = filteredTypes,
                    favoriteTypes = favoriteTypes,
                    searchQuery = searchQuery,
                    onNavigateToConfig = onNavigateToConfig,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun ToolsGrid(
    filteredTypes: List<ConversionType>,
    favoriteTypes: List<ConversionType>,
    searchQuery: String,
    onNavigateToConfig: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (searchQuery.isBlank() && favoriteTypes.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = "Favorites", icon = Icons.Default.Favorite)
            }
            item(span = { GridItemSpan(2) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
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
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(itemsInCategory, key = { it.id }) { item ->
                    ConversionCard(
                        conversionType = item,
                        onClick = { onNavigateToConfig(item.id) },
                        onFavoriteToggle = { onToggleFavorite(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MorphDropTheme {
        HomeScreenContent(
            searchQuery = "",
            filteredTypes = ConversionType.defaultList,
            favoriteTypes = ConversionType.defaultList.take(2),
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {}
        )
    }
}
