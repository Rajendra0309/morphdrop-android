package com.morphdrop.app.ui.screens.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.components.ConversionCard
import com.morphdrop.app.ui.components.MorphDropSearchBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.components.RecentConversionCard
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.NeonEmerald
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

@Composable
fun HomeScreen(
    onNavigateToConfig: (conversionTypeId: String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val liquidState = LocalLiquidState.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredTypes by viewModel.filteredConversionTypes.collectAsStateWithLifecycle()
    val favoriteTypes by viewModel.favoriteConversionTypes.collectAsStateWithLifecycle()
    val recentConversions by viewModel.recentConversions.collectAsStateWithLifecycle()

    HomeScreenContent(
        liquidState = liquidState,
        searchQuery = searchQuery,
        filteredTypes = filteredTypes,
        favoriteTypes = favoriteTypes,
        recentConversions = recentConversions,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleFavorite = viewModel::onToggleFavorite,
        onNavigateToConfig = onNavigateToConfig
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    liquidState: LiquidState,
    searchQuery: String,
    filteredTypes: List<ConversionType>,
    favoriteTypes: List<ConversionType>,
    recentConversions: List<ConversionHistoryEntity>,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNavigateToConfig: (String) -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Box(modifier = Modifier.fillMaxSize()) {
        // Source Background (Liquefiable)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isLight) {
                            listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9), Color(0xFFEDF2F7))
                        } else {
                            listOf(Color(0xFF05070A), Color(0xFF0D1117), Color(0xFF05070A))
                        }
                    )
                )
                .liquefiable(liquidState)
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                            Text(
                                text = "MorphDrop",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-1).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Drop. Transform. Done.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
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
                    liquidState = liquidState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (filteredTypes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = "No tools found",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No tools match \"$searchQuery\"",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            PrimaryButton(
                                text = "Clear Search",
                                onClick = { onSearchQueryChange("") }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 128.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (searchQuery.isBlank() && recentConversions.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Recent Conversions",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        recentConversions.forEach { historyItem ->
                                            RecentConversionCard(
                                                history = historyItem,
                                                liquidState = liquidState,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (searchQuery.isBlank() && favoriteTypes.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Favorites",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp) // Align with parent grid
                                    ) {
                                        items(favoriteTypes, key = { "fav_${it.id}" }) { favItem ->
                                            ConversionCard(
                                                conversionType = favItem,
                                                onClick = { onNavigateToConfig(favItem.id) },
                                                onFavoriteToggle = { onToggleFavorite(favItem.id) },
                                                liquidState = liquidState,
                                                modifier = Modifier.width(160.dp), // Consistent width
                                                isCompact = true,
                                                descriptionPrefix = "Favorite"
                                            )
                                        }
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
                                    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                                        Text(
                                            text = categoryName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = if (categoryName == ConversionType.CATEGORY_UNOPTIMIZED) {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                            } else {
                                                MaterialTheme.colorScheme.onBackground
                                            }
                                        )
                                        if (categoryName == ConversionType.CATEGORY_UNOPTIMIZED) {
                                            Text(
                                                text = "Tools under active optimization and enhancement",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                items(itemsInCategory, key = { it.id }) { item ->
                                    ConversionCard(
                                        conversionType = item,
                                        onClick = { onNavigateToConfig(item.id) },
                                        onFavoriteToggle = { onToggleFavorite(item.id) },
                                        liquidState = liquidState,
                                        descriptionPrefix = categoryName
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

@Preview(showBackground = true, backgroundColor = 0xFFF1F5F9)
@Composable
fun HomeScreenPreview() {
    MorphDropTheme {
        HomeScreenContent(
            liquidState = rememberLiquidState(),
            searchQuery = "",
            filteredTypes = ConversionType.defaultList,
            favoriteTypes = ConversionType.defaultList.take(2),
            recentConversions = listOf(
                ConversionHistoryEntity(
                    id = 1,
                    conversionType = "pdf_to_images",
                    inputFileName = "Document.pdf",
                    outputFileNames = "page1.png, page2.png",
                    timestamp = System.currentTimeMillis(),
                    success = true
                )
            ),
            onSearchQueryChange = {},
            onToggleFavorite = {},
            onNavigateToConfig = {}
        )
    }
}
