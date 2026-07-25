package com.morphdrop.app.ui.screens.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.morphdrop.app.util.FileHelper
import io.github.fletchmckee.liquid.rememberLiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    onNavigateBack: () -> Unit,
    onSplitStarted: (workId: String) -> Unit,
    viewModel: SplitPdfViewModel = hiltViewModel()
) {
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    GradientBackground(liquidState = liquidState) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Split PDF", color = Color.White) },
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
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Extract specific page ranges into separate PDF documents.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (state.selectedFile == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GlassCard(liquidState = liquidState, onClick = { launcher.launch("application/pdf") }) {
                            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyStateAnimation(icon = Icons.Default.Add, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Select PDF File", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    SelectedFileCard(
                        uri = state.selectedFile!!,
                        onClear = { viewModel.onFileSelected(null) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Page Ranges", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Example: 1-5, 8-10 (Leave empty for all pages)", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextField(
                        value = state.pageRanges,
                        onValueChange = viewModel::onPageRangesChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("1-2, 5-7", color = Color.White.copy(alpha = 0.3f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                PrimaryButton(
                    text = "Split PDF",
                    onClick = { 
                        viewModel.startSplit(context)?.let { workId ->
                            onSplitStarted(workId.toString())
                        }
                    },
                    enabled = state.selectedFile != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedFileCard(uri: Uri, onClear: () -> Unit) {
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    GlassCard(liquidState = liquidState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(FileHelper.getFileName(context, uri), color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text(FileHelper.formatFileSize(FileHelper.getFileSize(context, uri)), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplitPdfScreenPreview() {
    MorphDropTheme {
        CompositionLocalProvider(LocalLiquidState provides rememberLiquidState()) {
            SplitPdfScreen(
                onNavigateBack = {},
                onSplitStarted = {}
            )
        }
    }
}
