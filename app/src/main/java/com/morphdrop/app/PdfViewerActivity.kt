package com.morphdrop.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import com.morphdrop.app.ui.screens.pdf.PdfViewerScreen
import com.morphdrop.app.ui.theme.MorphDropTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PdfViewerActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove window background to prevent splash screen overlap
        window.setBackgroundDrawable(null)
        
        enableEdgeToEdge()

        val pdfUri: Uri? = if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data
        } else {
            null
        }

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val isDarkMode = when (themeMode) {
                com.morphdrop.app.domain.model.ThemeMode.DARK -> true
                com.morphdrop.app.domain.model.ThemeMode.LIGHT -> false
                com.morphdrop.app.domain.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MorphDropTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (pdfUri != null) {
                        PdfViewerScreen(
                            pdfUri = pdfUri,
                            onNavigateBack = { finish() }
                        )
                    } else {
                        // Handle case where no URI was provided
                        finish()
                    }
                }
            }
        }
    }
}
