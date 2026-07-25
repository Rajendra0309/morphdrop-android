package com.morphdrop.app.ui.screens.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.NeonEmerald
import io.github.fletchmckee.liquid.rememberLiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    onNavigateBack: () -> Unit,
    onMergeStarted: (workId: String) -> Unit,
    viewModel: MergePdfViewModel = hiltViewModel()
) {
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.onFilesSelected(uris)
    }

    GradientBackground(liquidState = liquidState) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Merge PDFs", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Combine multiple PDF files into one document in your preferred order.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (state.selectedFiles.isEmpty()) {
                        EmptyState(onAddFiles = { launcher.launch("application/pdf") })
                    } else {
                        FileList(
                            files = state.selectedFiles,
                            onRemove = viewModel::onRemoveFile,
                            onAddFiles = { launcher.launch("application/pdf") }
                        )
                    }
                }

                PrimaryButton(
                    text = "Merge Files",
                    onClick = { 
                        viewModel.startMerge(context)?.let { workId ->
                            onMergeStarted(workId.toString())
                        }
                    },
                    enabled = state.selectedFiles.size >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAddFiles: () -> Unit) {
    val liquidState = LocalLiquidState.current
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(
            liquidState = liquidState,
            onClick = onAddFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyStateAnimation(
                    icon = Icons.Default.Add,
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Add PDF Files", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select at least 2 files to merge", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<Uri>,
    onRemove: (Uri) -> Unit,
    onAddFiles: () -> Unit
) {
    val liquidState = LocalLiquidState.current
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(files) { uri ->
            GlassCard(liquidState = liquidState) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uri.lastPathSegment ?: "Unknown File",
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "PDF Document",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { onRemove(uri) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        
        item {
            OutlinedButton(
                onClick = onAddFiles,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add More Files")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MergePdfScreenPreview() {
    MorphDropTheme {
        CompositionLocalProvider(LocalLiquidState provides rememberLiquidState()) {
            MergePdfScreen(
                onNavigateBack = {},
                onMergeStarted = {}
            )
        }
    }
}
