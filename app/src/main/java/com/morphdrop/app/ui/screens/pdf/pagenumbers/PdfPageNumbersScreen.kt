package com.morphdrop.app.ui.screens.pdf.pagenumbers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.morphdrop.app.PdfViewerActivity
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.domain.model.PageNumberFormat
import com.morphdrop.app.domain.model.PageNumberPosition
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfPageNumbersScreen(
    initialUri: Uri? = null,
    onNavigateBack: () -> Unit,
    viewModel: PdfPageNumbersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            scrollState.animateScrollTo(0)
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && state.selectedUri == null) {
            viewModel.onPdfSelected(initialUri)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.onPdfSelected(uri)
        }
    }

    var hasAutoLaunchedPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasAutoLaunchedPicker && initialUri == null && state.selectedUri == null) {
            hasAutoLaunchedPicker = true
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Add Page Numbers",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack,
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Error banner if any
            AnimatedVisibility(visible = state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Success Result Card
            AnimatedVisibility(visible = state.isSuccess && state.resultUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Page Numbers Added Successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${state.resultFileName} (${FileHelper.formatFileSize(state.resultFileSize)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, PdfViewerActivity::class.java).apply {
                                        data = state.resultUri
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, state.resultUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Numbered PDF"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }

            val currentBitmap = if (state.activePreviewTab == 0) state.firstPageBitmap else state.lastPageBitmap
            val isCoverSkipped = state.activePreviewTab == 0 && state.config.skipFirstPage
            val isLastSkipped = state.activePreviewTab == 1 && state.config.skipLastPage

            // Unified Hero Document Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        if (state.selectedUri != null && currentBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val pageAspectRatio = (currentBitmap.width.toFloat() / currentBitmap.height.toFloat()).coerceIn(0.4f, 2.5f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(0.92f)
                                        .aspectRatio(pageAspectRatio)
                                        .shadow(6.dp, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = currentBitmap.asImageBitmap(),
                                        contentDescription = "PDF Preview Page",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isCoverSkipped) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Cover Page (Unnumbered)",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    } else if (isLastSkipped) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Last Page (Unnumbered)",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    } else {
                                        val pageNumToDisplay = if (state.activePreviewTab == 0) {
                                            state.config.startNumber
                                        } else {
                                            state.config.startNumber + state.pageCount - 1
                                        }

                                        PageNumberOverlayLayer(
                                            config = state.config,
                                            displayedPage = pageNumToDisplay,
                                            totalPages = state.pageCount
                                        )
                                    }
                                }
                            }
                        } else if (state.selectedUri != null && state.isPreviewLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "PDF Document",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (state.selectedUri != null && state.pageCount > 1) {
                        PrimaryTabRow(
                            selectedTabIndex = state.activePreviewTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = state.activePreviewTab == 0,
                                onClick = { viewModel.setActivePreviewTab(0) },
                                text = { Text("First Page") }
                            )
                            Tab(
                                selected = state.activePreviewTab == 1,
                                onClick = { viewModel.setActivePreviewTab(1) },
                                text = { Text("Last Page") }
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.selectedUri != null) state.fileName else "No File Selected",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (state.selectedUri != null) {
                                        "${FileHelper.formatFileSize(state.fileSize)} • ${state.pageCount} pages"
                                    } else {
                                        "Tap to select a PDF to add page numbers"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FormatBadge(fileType = FileType.PDF)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.selectedUri != null) "Change PDF Document" else "Select PDF Document")
                        }
                    }
                }
            }

            // Configuration Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Position & Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Position selector (6 visual chips)
                    Text("Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageNumberPosition.entries.forEach { pos ->
                            val isSelected = state.config.position == pos
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateConfig { it.copy(position = pos) } },
                                label = {
                                    Text(
                                        when (pos) {
                                            PageNumberPosition.BOTTOM_CENTER -> "Bottom Center"
                                            PageNumberPosition.BOTTOM_RIGHT -> "Bottom Right"
                                            PageNumberPosition.BOTTOM_LEFT -> "Bottom Left"
                                            PageNumberPosition.TOP_CENTER -> "Top Center"
                                            PageNumberPosition.TOP_RIGHT -> "Top Right"
                                            PageNumberPosition.TOP_LEFT -> "Top Left"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Format selector
                    Text("Format Style", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageNumberFormat.entries.forEach { fmt ->
                            val isSelected = state.config.format == fmt
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateConfig { it.copy(format = fmt) } },
                                label = {
                                    Text(
                                        when (fmt) {
                                            PageNumberFormat.PAGE_X -> "Page 1"
                                            PageNumberFormat.NUMBER_ONLY -> "1"
                                            PageNumberFormat.PAGE_X_OF_Y -> "Page 1 of N"
                                            PageNumberFormat.X_OF_Y -> "1/N"
                                            PageNumberFormat.CUSTOM -> "Custom"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // Custom format input field
                    if (state.config.format == PageNumberFormat.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.config.customTemplate,
                            onValueChange = { tmpl -> viewModel.updateConfig { it.copy(customTemplate = tmpl) } },
                            label = { Text("Template (e.g. - {page} -)") },
                            supportingText = { Text("Use {page} and {total} as variables") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${state.config.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = state.config.fontSizeSp,
                        onValueChange = { sz -> viewModel.updateConfig { it.copy(fontSizeSp = sz) } },
                        valueRange = 8f..28f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Margin Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Margin from Edge", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${state.config.marginDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = state.config.marginDp,
                        onValueChange = { m -> viewModel.updateConfig { it.copy(marginDp = m) } },
                        valueRange = 8f..48f,
                        steps = 7
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Starting Page Number Input
                    OutlinedTextField(
                        value = state.config.startNumber.toString(),
                        onValueChange = { str ->
                            val num = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                            viewModel.updateConfig { it.copy(startNumber = num.coerceIn(1, 9999)) }
                        },
                        label = { Text("Start Numbering At") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Color picker row
                    Text("Font Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val colorPalette = listOf(
                        0xFF000000 to "Black",
                        0xFF424242 to "Dark Gray",
                        0xFF757575 to "Gray",
                        0xFF0D47A1 to "Blue",
                        0xFFD32F2F to "Red",
                        0xFF1B5E20 to "Green"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorPalette.forEach { (c, _) ->
                            val isSelected = state.config.fontColor == c
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.updateConfig { it.copy(fontColor = c) } }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Skip first page toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Skip First Page (Cover)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Numbering begins on page 2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.config.skipFirstPage,
                            onCheckedChange = { sk -> viewModel.updateConfig { it.copy(skipFirstPage = sk) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Skip last page toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Skip Last Page", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Leave appendix or back cover clean", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.config.skipLastPage,
                            onCheckedChange = { sk -> viewModel.updateConfig { it.copy(skipLastPage = sk) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom page range
                    OutlinedTextField(
                        value = state.customRangeText,
                        onValueChange = viewModel::onCustomRangeTextChanged,
                        label = { Text("Custom Target Pages (optional, e.g. 2-8)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Apply Button
            PrimaryButton(
                text = if (state.isProcessing) "Adding Page Numbers..." else "Add Page Numbers",
                onClick = viewModel::applyPageNumbers,
                enabled = !state.isProcessing && state.selectedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun PageNumberOverlayLayer(
    config: PageNumberConfig,
    displayedPage: Int,
    totalPages: Int
) {
    val alignment = when (config.position) {
        PageNumberPosition.TOP_LEFT -> Alignment.TopStart
        PageNumberPosition.TOP_CENTER -> Alignment.TopCenter
        PageNumberPosition.TOP_RIGHT -> Alignment.TopEnd
        PageNumberPosition.BOTTOM_LEFT -> Alignment.BottomStart
        PageNumberPosition.BOTTOM_CENTER -> Alignment.BottomCenter
        PageNumberPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    val pageText = when (config.format) {
        PageNumberFormat.PAGE_X -> "Page $displayedPage"
        PageNumberFormat.NUMBER_ONLY -> "$displayedPage"
        PageNumberFormat.PAGE_X_OF_Y -> "Page $displayedPage of $totalPages"
        PageNumberFormat.X_OF_Y -> "$displayedPage/$totalPages"
        PageNumberFormat.CUSTOM -> {
            config.customTemplate
                .replace("{page}", "$displayedPage")
                .replace("{total}", "$totalPages")
        }
    }

    val marginScaled = (config.marginDp * 0.45f).coerceIn(4f, 24f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(marginScaled),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = pageText,
                fontSize = (config.fontSizeSp * 0.75f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(config.fontColor),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
