package com.morphdrop.app.ui.screens.pdf

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PageThumbnail
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageEditorScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: PdfPageEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onFileSelected(uri)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    PdfPageEditorScreenContent(
        state = uiState,
        scrollBehavior = scrollBehavior,
        onNavigateBack = onNavigateBack,
        onPickFile = { filePicker.launch(arrayOf("application/pdf")) },
        onRotatePage = { viewModel.rotatePage(it) },
        onOutputFileNameChanged = { viewModel.onOutputFileNameChanged(it) },
        onSaveChanges = {
            val workId = viewModel.startEditing(context)
            if (workId != null) onNavigateToProcessing("page_editor", workId.toString())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageEditorScreenContent(
    state: PdfPageEditorState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onPickFile: () -> Unit,
    onRotatePage: (Int) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onSaveChanges: () -> Unit
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Organize PDF",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            if (state.pages.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = state.outputFileName,
                            onValueChange = onOutputFileNameChanged,
                            label = { Text("Output File Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryButton(
                            text = "Save Changes",
                            onClick = onSaveChanges,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (state.pages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "No PDF selected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select a PDF file to reorder or rotate its pages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select PDF File")
                }
            }
        } else if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(innerPadding)
            ) {
                items(state.pages, key = { it.originalIndex }) { page ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfPageEditorScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfPageEditorScreenContent(
            state = PdfPageEditorState(
                fileName = "Project_Docs.pdf",
                pages = listOf(
                    PageData(1, 0, 0),
                    PageData(2, 1, 90),
                    PageData(3, 2, 0)
                ),
                outputFileName = "Project_Docs_Edited.pdf"
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFile = {},
            onRotatePage = {},
            onOutputFileNameChanged = {},
            onSaveChanges = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfPageEditorScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfPageEditorScreenContent(
            state = PdfPageEditorState(
                isLoading = true
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFile = {},
            onRotatePage = {},
            onOutputFileNameChanged = {},
            onSaveChanges = {}
        )
    }
}
