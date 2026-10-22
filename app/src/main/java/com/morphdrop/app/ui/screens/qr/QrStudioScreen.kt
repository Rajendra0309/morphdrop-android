package com.morphdrop.app.ui.screens.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.components.SectionHeader
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrStudioScreen(
    onNavigateBack: () -> Unit,
    viewModel: QrStudioViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "QR Code Studio",
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
        ) {
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Create") },
                    icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text("Scan") },
                    icon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) }
                )
            }

            AnimatedVisibility(visible = state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.selectedTab == 0) {
                QrCreateTab(state = state, viewModel = viewModel)
            } else {
                QrScanTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrCreateTab(state: QrStudioUiState, viewModel: QrStudioViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Column {
            SectionHeader(title = "Code Type", icon = Icons.Outlined.QrCodeScanner)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QrContentType.entries.forEach { type ->
                    FilterChip(
                        selected = state.contentType == type,
                        onClick = { viewModel.setContentType(type) },
                        label = { Text(type.label) }
                    )
                }
            }
        }

        when (state.contentType) {
            QrContentType.TEXT -> {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = { value -> viewModel.update { copy(text = value) } },
                    label = { Text("Text") },
                    placeholder = { Text("Any text you want inside the code") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            QrContentType.LINK -> {
                OutlinedTextField(
                    value = state.link,
                    onValueChange = { value -> viewModel.update { copy(link = value) } },
                    label = { Text("Website link") },
                    placeholder = { Text("example.com or https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            QrContentType.WIFI -> {
                OutlinedTextField(
                    value = state.wifiSsid,
                    onValueChange = { value -> viewModel.update { copy(wifiSsid = value) } },
                    label = { Text("Network name (SSID)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.wifiPassword,
                    onValueChange = { value -> viewModel.update { copy(wifiPassword = value) } },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("WPA", "WEP", "nopass").forEach { security ->
                        FilterChip(
                            selected = state.wifiSecurity == security,
                            onClick = { viewModel.update { copy(wifiSecurity = security) } },
                            label = { Text(if (security == "nopass") "Open (no password)" else security) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Hidden network",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.wifiHidden,
                        onCheckedChange = { checked -> viewModel.update { copy(wifiHidden = checked) } }
                    )
                }
            }
            QrContentType.CONTACT -> {
                OutlinedTextField(
                    value = state.contactName,
                    onValueChange = { value -> viewModel.update { copy(contactName = value) } },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.contactPhone,
                    onValueChange = { value -> viewModel.update { copy(contactPhone = value) } },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.contactEmail,
                    onValueChange = { value -> viewModel.update { copy(contactEmail = value) } },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            QrContentType.PHONE -> {
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = { value -> viewModel.update { copy(phone = value) } },
                    label = { Text("Phone number") },
                    placeholder = { Text("+91...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            QrContentType.EMAIL -> {
                OutlinedTextField(
                    value = state.emailAddress,
                    onValueChange = { value -> viewModel.update { copy(emailAddress = value) } },
                    label = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.emailSubject,
                    onValueChange = { value -> viewModel.update { copy(emailSubject = value) } },
                    label = { Text("Subject (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            QrContentType.SMS -> {
                OutlinedTextField(
                    value = state.smsNumber,
                    onValueChange = { value -> viewModel.update { copy(smsNumber = value) } },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.smsMessage,
                    onValueChange = { value -> viewModel.update { copy(smsMessage = value) } },
                    label = { Text("Message (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }

        PrimaryButton(
            text = "Generate QR Code",
            onClick = viewModel::generate,
            enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth()
        )

        val generated = state.generatedBitmap
        if (generated != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = generated.asImageBitmap(),
                            contentDescription = "Generated QR code",
                            modifier = Modifier.size(224.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.generatedPayload,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::saveGeneratedQr) {
                            Text("Save Image")
                        }
                        OutlinedButton(
                            onClick = {
                                val bytes = java.io.ByteArrayOutputStream()
                                    .also { generated.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                    .toByteArray()
                                val cacheUri = FileHelper.saveToCache(context, "shared_qr_code.png", bytes)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, cacheUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Share")
                        }
                    }
                    if (state.savedFileName != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "Saved to Downloads/MorphDrop",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrScanTab(state: QrStudioUiState, viewModel: QrStudioViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionGranted = granted }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.scanImage(uri)
        }
    }

    LaunchedEffect(state.scanMode) {
        if (state.scanMode == QrScanMode.CAMERA && !cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.scanMode == QrScanMode.CAMERA,
                onClick = { viewModel.setScanMode(QrScanMode.CAMERA) },
                label = { Text("Camera") },
                leadingIcon = {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            FilterChip(
                selected = state.scanMode == QrScanMode.IMAGE,
                onClick = { viewModel.setScanMode(QrScanMode.IMAGE) },
                label = { Text("From Image") },
                leadingIcon = {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        if (state.scanMode == QrScanMode.CAMERA) {
            if (cameraPermissionGranted) {
                CameraScanner(
                    onCodeScanned = viewModel::onCameraCodeScanned,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Text(
                    text = "Point the camera at a QR code or barcode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera permission is needed to scan codes live. Nothing is recorded or uploaded — scanning happens on your device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Allow Camera",
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isScanningImage) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Looking for a code in the image...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Pick a screenshot or photo that contains a QR code or barcode",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Choose Image",
                            onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }
                        )
                    }
                }
            }
        }

        if (state.scanError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.scanError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val scanned = state.scannedCode
        if (scanned != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = scanned.formatName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = scanned.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(scanned.text)) }
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Copy")
                        }
                        if (scanned.text.startsWith("http://") || scanned.text.startsWith("https://")) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scanned.text))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Open Link")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, scanned.text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Scanned Text"))
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Share")
                        }
                        OutlinedButton(onClick = viewModel::createFromScanned) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Make QR from this")
                        }
                        OutlinedButton(onClick = viewModel::clearScanResult) {
                            Text("Scan Again")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Live camera scanner: CameraX preview with an ML Kit barcode analyzer.
 * Frames are processed on a single background thread and dropped when the
 * analyzer is busy, so the preview stays smooth on mid-range phones.
 */
@Composable
private fun CameraScanner(
    onCodeScanned: (com.morphdrop.app.data.qr.ScannedCode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var torchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    val scanner = remember {
        com.google.mlkit.vision.barcode.BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
    }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        cameraControl?.enableTorch(torchOn)
    }

    Box(modifier = modifier) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = androidx.camera.core.ImageAnalysis.Builder()
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFrame(imageProxy, scanner, onCodeScanned)
                    }
                    try {
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        android.util.Log.w("QrStudio", "Camera bind failed", e)
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = { torchOn = !torchOn },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = if (torchOn) "Turn flash off" else "Turn flash on",
                tint = Color.White
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processFrame(
    imageProxy: androidx.camera.core.ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCodeScanned: (com.morphdrop.app.data.qr.ScannedCode) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees
    )
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.let { barcode ->
                onCodeScanned(
                    com.morphdrop.app.data.qr.ScannedCode(
                        text = barcode.rawValue.orEmpty(),
                        formatName = com.morphdrop.app.data.qr.QrCodeImage.formatDisplayName(barcode.format)
                    )
                )
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
