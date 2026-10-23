package com.morphdrop.app.ui.screens.signpdf

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.domain.usecase.conversion.SignPdfUseCase
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import androidx.compose.foundation.gestures.detectDragGestures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: SignPdfViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDrawPad by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onPdfPicked(uri) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onSignatureImagePicked(uri) }

    if (showDrawPad) {
        SignaturePadDialog(
            onDismiss = { showDrawPad = false },
            onDone = { bitmap ->
                viewModel.onSignatureDrawn(bitmap)
                showDrawPad = false
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Sign PDF",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Document
            SignSectionTitle("Document")
            if (state.pdfUri == null) {
                OutlinedButton(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose PDF") }
            } else {
                Text(
                    text = state.pdfName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoadingPreview -> CircularProgressIndicator(
                            modifier = Modifier.padding(48.dp)
                        )
                        state.pagePreview != null -> Image(
                            bitmap = state.pagePreview!!.asImageBitmap(),
                            contentDescription = "Page ${state.currentPage + 1} preview",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.goToPage(state.currentPage - 1) },
                        enabled = state.currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    Text("Page ${state.currentPage + 1} of ${state.pageCount}")
                    IconButton(
                        onClick = { viewModel.goToPage(state.currentPage + 1) },
                        enabled = state.currentPage < state.pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
                TextButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                    Text("Choose a different PDF")
                }
            }

            // Signature
            SignSectionTitle("Signature")
            if (state.signatureBitmap == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showDrawPad = true }, modifier = Modifier.weight(1f)) {
                        Text("Draw Signature")
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pick from Photos") }
                }
                Text(
                    text = "A transparent PNG works best. Photos of a signature on white " +
                        "paper also stamp well.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = state.signatureBitmap!!.asImageBitmap(),
                        contentDescription = "Your signature",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(12.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showDrawPad = true }, modifier = Modifier.weight(1f)) {
                        Text("Draw Again")
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pick Another") }
                }
            }

            // Placement
            SignSectionTitle("Placement")
            val positions = listOf(
                SignPdfUseCase.POS_BOTTOM_RIGHT to "Bottom Right",
                SignPdfUseCase.POS_BOTTOM_LEFT to "Bottom Left",
                SignPdfUseCase.POS_TOP_RIGHT to "Top Right",
                SignPdfUseCase.POS_TOP_LEFT to "Top Left",
                SignPdfUseCase.POS_CENTER to "Centre"
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                positions.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { (id, label) ->
                            FilterChip(
                                selected = state.position == id,
                                onClick = { viewModel.onPositionChanged(id) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            SignSectionTitle("Pages")
            val scopes = listOf(
                SignPdfUseCase.SCOPE_THIS_PAGE to "This Page",
                SignPdfUseCase.SCOPE_FIRST to "First Page",
                SignPdfUseCase.SCOPE_LAST to "Last Page",
                SignPdfUseCase.SCOPE_ALL to "All Pages"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scopes.forEach { (id, label) ->
                    FilterChip(
                        selected = state.scope == id,
                        onClick = { viewModel.onScopeChanged(id) },
                        label = { Text(label) }
                    )
                }
            }

            SignSectionTitle("Signature Size: ${state.widthPercent}% of page width")
            Slider(
                value = state.widthPercent.toFloat(),
                onValueChange = { viewModel.onWidthPercentChanged(it.toInt()) },
                valueRange = 10f..50f
            )

            Button(
                onClick = {
                    viewModel.startSign()?.let { workId ->
                        onNavigateToProcessing("sign_pdf", workId.toString())
                    }
                },
                enabled = state.canSign,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sign PDF", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SignSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Finger/stylus drawing pad. Strokes are collected as vector paths and
 * rasterized onto a transparent bitmap only when the user confirms, so
 * the stamped signature has no paper background.
 */
@Composable
private fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    val inkColor = Color(0xFF1A237E)
    var paths by remember { mutableStateOf<List<Path>>(emptyList()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var redrawTick by remember { mutableIntStateOf(0) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw your signature") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                    redrawTick++
                                },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    redrawTick++
                                },
                                onDragEnd = {
                                    currentPath?.let { paths = paths + it }
                                    currentPath = null
                                    redrawTick++
                                }
                            )
                        }
                ) {
                    @Suppress("UNUSED_EXPRESSION") redrawTick // read to redraw on stroke changes
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        paths.forEach { path ->
                            drawPath(path, color = inkColor, style = Stroke(width = 6f))
                        }
                        currentPath?.let { path ->
                            drawPath(path, color = inkColor, style = Stroke(width = 6f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign inside the box. Only the ink is kept — the background stays transparent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val all = paths + listOfNotNull(currentPath)
                    if (all.isNotEmpty() && padSize.width > 0 && padSize.height > 0) {
                        onDone(rasterizeSignature(all, padSize))
                    }
                },
                enabled = paths.isNotEmpty() || currentPath != null
            ) { Text("Use Signature") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        paths = emptyList()
                        currentPath = null
                        redrawTick++
                    }
                ) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Draws the collected strokes into a transparent bitmap at pad resolution. */
private fun rasterizeSignature(paths: List<Path>, size: IntSize): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(0, PorterDuff.Mode.CLEAR)
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(26, 35, 126)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    paths.forEach { path -> canvas.drawPath(path.asAndroidPath(), paint) }
    return bitmap
}
