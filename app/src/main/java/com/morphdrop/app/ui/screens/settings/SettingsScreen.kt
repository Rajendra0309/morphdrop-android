package com.morphdrop.app.ui.screens.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.theme.MorphDropTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreenContent(
        state = uiState,
        onToggleDarkMode = viewModel::toggleDarkMode,
        onRateApp = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback for emulator or no Play Store
            }
        },
        onReportBug = {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@morphdrop.app"))
            context.startActivity(intent)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onToggleDarkMode: (Boolean) -> Unit,
    onRateApp: () -> Unit,
    onReportBug: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "Appearance") {
                SettingsToggleItem(
                    title = "Dark Mode",
                    description = "Enable darker interface colors",
                    icon = Icons.Default.DarkMode,
                    checked = state.isDarkMode,
                    onCheckedChange = onToggleDarkMode
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                SettingsItem(
                    title = "Dynamic Colors",
                    description = "Adapts to your wallpaper (Android 12+)",
                    icon = Icons.Default.ColorLens,
                    onClick = { }
                )
            }

            SettingsSection(title = "General") {
                SettingsItem(
                    title = "Output Folder",
                    description = state.defaultOutputDirectory,
                    icon = Icons.Default.Info,
                    onClick = { }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                SettingsItem(
                    title = "Cache Size",
                    description = state.cacheSizeFormatted,
                    icon = Icons.Default.Info,
                    onClick = { }
                )
            }

            SettingsSection(title = "Support & About") {
                SettingsItem(
                    title = "Rate App",
                    description = "Love the app? Let us know!",
                    icon = Icons.Default.Star,
                    onClick = onRateApp
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                SettingsItem(
                    title = "Report a Bug",
                    description = "Something not working correctly?",
                    icon = Icons.Default.BugReport,
                    onClick = onReportBug
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                SettingsItem(
                    title = "Privacy Policy",
                    description = "Read our data handling practices",
                    icon = Icons.Default.Policy,
                    onClick = { }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                SettingsItem(
                    title = "About MorphDrop",
                    description = "Version ${state.appVersion}",
                    icon = Icons.Default.Info,
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun SettingsScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        SettingsScreenContent(
            state = SettingsUiState(
                isDarkMode = false,
                defaultOutputDirectory = "Downloads/MorphDrop",
                cacheSizeFormatted = "12 MB",
                appVersion = "1.0.0"
            ),
            onToggleDarkMode = {},
            onRateApp = {},
            onReportBug = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SettingsScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        SettingsScreenContent(
            state = SettingsUiState(
                isDarkMode = true,
                defaultOutputDirectory = "Downloads/MorphDrop",
                cacheSizeFormatted = "12 MB",
                appVersion = "1.0.0"
            ),
            onToggleDarkMode = {},
            onRateApp = {},
            onReportBug = {}
        )
    }
}
