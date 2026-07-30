package com.morphdrop.app.ui.screens.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.ThemeAnimationManager
import com.morphdrop.app.ui.theme.MorphDropTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.calculateCacheSize(context)
    }

    SettingsScreenContent(
        state = uiState,
        onToggleDarkMode = viewModel::toggleDarkMode,
        onClearCache = {
            viewModel.clearCache(context)
            Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
        },
        onOutputFolderChange = viewModel::updateOutputFolderName,
        onRateApp = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "MorphDrop v${uiState.appVersion}", Toast.LENGTH_SHORT).show()
            }
        },
        onReportBug = {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@morphdrop.app"))
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Email support@morphdrop.app", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onToggleDarkMode: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOutputFolderChange: (String) -> Unit,
    onRateApp: () -> Unit,
    onReportBug: () -> Unit
) {
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showEditFolderDialog by remember { mutableStateOf(false) }
    var tempFolderName by remember { mutableStateOf(state.defaultOutputDirectory) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear App Cache") },
            text = { Text("Are you sure you want to clear temporary cached files (${state.cacheSizeFormatted})?") },
            confirmButton = {
                TextButton(onClick = {
                    onClearCache()
                    showClearCacheDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditFolderDialog) {
        AlertDialog(
            onDismissRequest = { showEditFolderDialog = false },
            title = { Text("Edit Output Folder Name") },
            text = {
                TextField(
                    value = tempFolderName,
                    onValueChange = { tempFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onOutputFolderChange(tempFolderName)
                    showEditFolderDialog = false
                }) {
                    Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Settings",
                scrollBehavior = scrollBehavior,
                showBackArrow = false
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
            }

            SettingsSection(title = "General") {
                SettingsItem(
                    title = "Output Folder",
                    description = state.defaultOutputDirectory,
                    icon = Icons.Default.Folder,
                    onClick = {
                        tempFolderName = state.defaultOutputDirectory
                        showEditFolderDialog = true
                    }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                SettingsItem(
                    title = "Clear Cache",
                    description = "Temporary size: ${state.cacheSizeFormatted}",
                    icon = Icons.Default.CleaningServices,
                    onClick = { showClearCacheDialog = true }
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                ThemeAnimationManager.revealCenter = bounds.center
            }
        )
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
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
            onClearCache = {},
            onOutputFolderChange = {},
            onRateApp = {},
            onReportBug = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
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
            onClearCache = {},
            onOutputFolderChange = {},
            onRateApp = {},
            onReportBug = {}
        )
    }
}
