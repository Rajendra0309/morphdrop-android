package com.morphdrop.app.ui.screens.pdf

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPasswordScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: PdfPasswordViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onFileSelected(uri)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    PdfPasswordScreenContent(
        state = uiState,
        scrollBehavior = scrollBehavior,
        onNavigateBack = onNavigateBack,
        onPickFile = { filePicker.launch(arrayOf("application/pdf")) },
        onPasswordChanged = { viewModel.onPasswordChanged(it) },
        onConfirmPasswordChanged = { viewModel.onConfirmPasswordChanged(it) },
        onOutputFileNameChanged = { viewModel.onOutputFileNameChanged(it) },
        onActionChanged = { viewModel.onActionChanged(it) },
        onApply = {
            val workId = viewModel.startProtect(context)
            if (workId != null) onNavigateToProcessing("protect_pdf", workId.toString())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPasswordScreenContent(
    state: PdfPasswordState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onPickFile: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onActionChanged: (String) -> Unit,
    onApply: () -> Unit
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Protect PDF",
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
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Protect your PDF",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Add or remove a password from your PDF document",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // File Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = state.fileName.ifBlank { "No file selected" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (state.selectedFile != null) "Change File" else "Select PDF File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = state.action == "ADD_PASSWORD",
                    onClick = { onActionChanged("ADD_PASSWORD") },
                    label = { 
                        Text(
                            "Add Password",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = state.action == "REMOVE_PASSWORD",
                    onClick = { onActionChanged("REMOVE_PASSWORD") },
                    label = { 
                        Text(
                            "Remove Password",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text(if (state.action == "ADD_PASSWORD") "New Password" else "Current Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )

            if (state.action == "ADD_PASSWORD") {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    isError = state.password != state.confirmPassword && state.confirmPassword.isNotEmpty()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.outputFileName,
                onValueChange = onOutputFileNameChanged,
                label = { Text("Output File Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = if (state.action == "ADD_PASSWORD") "Protect PDF" else "Unlock PDF",
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedFile != null && state.password.isNotEmpty() && 
                        (state.action == "REMOVE_PASSWORD" || state.password == state.confirmPassword)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfPasswordScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfPasswordScreenContent(
            state = PdfPasswordState(
                selectedFile = Uri.parse("doc.pdf"),
                fileName = "Confidential.pdf",
                password = "secure_password",
                confirmPassword = "",
                outputFileName = "Confidential_Protected.pdf"
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFile = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onOutputFileNameChanged = {},
            onActionChanged = {},
            onApply = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfPasswordScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfPasswordScreenContent(
            state = PdfPasswordState(
                selectedFile = null,
                fileName = "",
                password = "test",
                confirmPassword = "test",
                outputFileName = ""
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFile = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onOutputFileNameChanged = {},
            onActionChanged = {},
            onApply = {}
        )
    }
}
