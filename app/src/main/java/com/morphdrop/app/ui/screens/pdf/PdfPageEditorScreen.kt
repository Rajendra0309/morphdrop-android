package com.morphdrop.app.ui.screens.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.EmptyStateAnimation
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.components.GradientBackground
import com.morphdrop.app.ui.components.PageThumbnail
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.MorphDropTheme
import io.github.fletchmckee.liquid.rememberLiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageEditorScreen(
    onNavigateBack: () -> Unit,
    onSaveStarted: (workId: String) -> Unit,
    viewModel: PdfPageEditorViewModel = hiltViewModel()
) {
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    // Temporary local state for mockup reordering/rotation
    var localPages by remember(state.selectedFile) { 
        mutableStateOf<List<PageData>>(if (state.selectedFile != null) (1..5).map { PageData(it, 0) } else emptyList()) 
    }

    GradientBackground(liquidState = liquidState) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Organize PDF", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (state.selectedFile == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassCard(liquidState = liquidState, onClick = { launcher.launch("application/pdf") }) {
                            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyStateAnimation(icon = Icons.Default.Transform, modifier = Modifier.size(80.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Select PDF to Organize", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Tap rotation icon to rotate pages. Long press would reorder (Phase 4).",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(localPages) { page ->
                            PageThumbnail(
                                pageNumber = page.number,
                                rotation = page.rotation,
                                onRotate = {
                                    val newList = localPages.toMutableList()
                                    val index = newList.indexOf(page)
                                    newList[index] = page.copy(rotation = (page.rotation + 90) % 360)
                                    localPages = newList
                                }
                            )
                        }
                    }

                    PrimaryButton(
                        text = "Save Changes",
                        onClick = { 
                            viewModel.startEditing(context, localPages)?.let { workId ->
                                onSaveStarted(workId.toString())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PdfPageEditorScreenPreview() {
    MorphDropTheme {
        CompositionLocalProvider(LocalLiquidState provides rememberLiquidState()) {
            PdfPageEditorScreen(
                onNavigateBack = {},
                onSaveStarted = {}
            )
        }
    }
}

data class PageData(
    val number: Int,
    val rotation: Int
)
