package com.morphdrop.app.ui.screens.pdf

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@Composable
fun PdfPasswordScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (workId: String) -> Unit = {},
    viewModel: PdfPasswordViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PdfPasswordScreenContent(
        state = uiState,
        onNavigateBack = onNavigateBack,
        onPasswordChanged = { viewModel.onPasswordChanged(it) },
        onConfirmPasswordChanged = { viewModel.onConfirmPasswordChanged(it) },
        onApply = {
            val workId = viewModel.startProtect(context)
            if (workId != null) onNavigateToProcessing(workId.toString())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPasswordScreenContent(
    state: PdfPasswordState,
    onNavigateBack: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onApply: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                text = "Add a password to prevent unauthorized access",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )

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

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Apply Protection",
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.password.isNotEmpty() && state.password == state.confirmPassword
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun PdfPasswordScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfPasswordScreenContent(
            state = PdfPasswordState(
                password = "secure_password",
                confirmPassword = ""
            ),
            onNavigateBack = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onApply = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PdfPasswordScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfPasswordScreenContent(
            state = PdfPasswordState(
                password = "test",
                confirmPassword = "test"
            ),
            onNavigateBack = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onApply = {}
        )
    }
}
