package com.morphdrop.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.NeonEmerald
import com.morphdrop.app.ui.theme.TextPrimary
import com.morphdrop.app.ui.theme.TextSecondary
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.rememberLiquidState

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liquidState = LocalLiquidState.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.calculateCacheSize(context)
    }

    SettingsScreenContent(
        uiState = uiState,
        liquidState = liquidState,
        onNavigateBack = onNavigateBack,
        onToggleDarkMode = viewModel::toggleDarkMode,
        onUpdateOutputFolder = viewModel::updateOutputFolderName,
        onClearHistory = viewModel::clearHistory,
        onClearCache = { viewModel.clearCache(context) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    liquidState: LiquidState,
    onNavigateBack: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onUpdateOutputFolder: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var showFolderDialog by remember { mutableStateOf(false) }

    if (showFolderDialog) {
        var folderName by remember { mutableStateOf(uiState.defaultOutputDirectory) }
        Dialog(onDismissRequest = { showFolderDialog = false }) {
            GlassCard(liquidState = liquidState) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Output Folder Name",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    TextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeonEmerald
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PrimaryButton(
                            text = "Cancel",
                            onClick = { showFolderDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = "Save",
                            onClick = {
                                onUpdateOutputFolder(folderName)
                                showFolderDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 16.dp, end = 16.dp, bottom = 128.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "General",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeonEmerald
                )

                GlassCard(
                    liquidState = liquidState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Theme",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!uiState.isDarkMode) NeonEmerald else Color.Transparent)
                                    .clickable { onToggleDarkMode(false) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "LIGHT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (!uiState.isDarkMode) Color.White else TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (uiState.isDarkMode) NeonEmerald else Color.Transparent)
                                    .clickable { onToggleDarkMode(true) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "DARK",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (uiState.isDarkMode) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }

                GlassCard(
                    liquidState = liquidState,
                    onClick = { showFolderDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Default output folder",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.defaultOutputDirectory,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Storage",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeonEmerald
                )

                GlassCard(
                    liquidState = liquidState,
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clear history",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = Color(0xFFD32F2F)
                        )
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                GlassCard(
                    liquidState = liquidState,
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clear cache",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = uiState.cacheSizeFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "About",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeonEmerald
                )

                GlassCard(
                    liquidState = liquidState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = uiState.appVersion,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Open source licenses",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                                    context.startActivity(intent)
                                }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GitHub Repository",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MorphDropTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                isDarkMode = true,
                defaultOutputDirectory = "/storage/emulated/0/Download/MorphDrop",
                appVersion = "1.0.0",
                cacheSizeFormatted = "24.5 MB"
            ),
            liquidState = rememberLiquidState(),
            onNavigateBack = {},
            onToggleDarkMode = {},
            onUpdateOutputFolder = {},
            onClearHistory = {},
            onClearCache = {}
        )
    }
}
