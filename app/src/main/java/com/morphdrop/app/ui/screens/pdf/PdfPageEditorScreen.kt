package com.morphdrop.app.ui.screens.pdf

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.PageThumbnail
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@Composable
fun PdfPageEditorScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (workId: String) -> Unit = {},
    viewModel: PdfPageEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PdfPageEditorScreenContent(
        pages = uiState.pages,
        onNavigateBack = onNavigateBack,
        onRotatePage = { viewModel.rotatePage(it) },
        onSaveChanges = {
            val workId = viewModel.startEditing(context)
            if (workId != null) onNavigateToProcessing(workId.toString())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageEditorScreenContent(
    pages: List<PageData>,
    onNavigateBack: () -> Unit,
    onRotatePage: (Int) -> Unit,
    onSaveChanges: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organize PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    PrimaryButton(
                        text = "Save Changes",
                        onClick = onSaveChanges,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No pages to display", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(innerPadding)
            ) {
                items(pages, key = { it.originalIndex }) { page ->
                    PageThumbnail(
                        pageNumber = page.number,
                        rotation = page.rotation,
                        onRotate = { onRotatePage(page.originalIndex) }
                    )
                }
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun PdfPageEditorScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfPageEditorScreenContent(
            pages = listOf(
                PageData(1, 0, 0),
                PageData(2, 1, 90),
                PageData(3, 2, 0)
            ),
            onNavigateBack = {},
            onRotatePage = {},
            onSaveChanges = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PdfPageEditorScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfPageEditorScreenContent(
            pages = emptyList(),
            onNavigateBack = {},
            onRotatePage = {},
            onSaveChanges = {}
        )
    }
}
