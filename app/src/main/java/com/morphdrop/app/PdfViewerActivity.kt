package com.morphdrop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.morphdrop.app.ui.screens.pdf.PdfViewerScreen
import com.morphdrop.app.ui.theme.MorphDropTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PdfViewerActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private var currentPdfUri: Uri? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentPdfUri?.let { outState.putString("saved_pdf_uri", it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay
            val maxMode = display.supportedModes.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }
        
        // Remove window background to prevent splash screen overlap
        window.setBackgroundDrawable(null)
        
        enableEdgeToEdge()

        val incomingUri: Uri? = intent.data
            ?: intent.getParcelableExtra("pdf_uri")
            ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

        // Continue-reading target page (0-based), 0 when not specified.
        val initialPage = intent.getIntExtra("page", 0).coerceAtLeast(0)

        // Durable read access: take a persistable grant only when the sender actually
        // granted one; otherwise copy the content into app-private cache on IO so the URI
        // stays readable even if the granting process dies.
        val savedUri: Uri? = savedInstanceState?.getString("saved_pdf_uri")?.let { Uri.parse(it) }
        val initialUri: Uri? = if (savedUri != null) {
            savedUri
        } else if (incomingUri != null &&
            (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        ) {
            runCatching {
                contentResolver.takePersistableUriPermission(incomingUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            incomingUri
        } else if (incomingUri != null && (incomingUri.authority == "${packageName}.fileprovider" || incomingUri.scheme == "file")) {
            incomingUri
        } else {
            null
        }

        currentPdfUri = initialUri
        var pdfUri by mutableStateOf<Uri?>(initialUri)
        var isResolving by mutableStateOf(incomingUri != null && initialUri == null)

        if (incomingUri != null && initialUri == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val cached = copyUriToCache(incomingUri)
                val resolved = cached ?: incomingUri
                withContext(Dispatchers.Main) {
                    currentPdfUri = cached
                    pdfUri = resolved
                    isResolving = false
                }
            }
        }

        setContent {
            LaunchedEffect(incomingUri, isResolving, pdfUri) {
                if (incomingUri == null || (!isResolving && pdfUri == null)) finish()
            }

            val themeMode by mainViewModel.themeMode.collectAsState()
            val dynamicColorEnabled by mainViewModel.dynamicColorEnabled.collectAsState()
            val isDarkMode = when (themeMode) {
                com.morphdrop.app.domain.model.ThemeMode.DARK -> true
                com.morphdrop.app.domain.model.ThemeMode.LIGHT -> false
                com.morphdrop.app.domain.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MorphDropTheme(darkTheme = isDarkMode, dynamicColor = dynamicColorEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (pdfUri != null) {
                        PdfViewerScreen(
                            pdfUri = pdfUri!!,
                            initialPage = initialPage,
                            onNavigateBack = { finish() }
                        )
                    } else if (isResolving) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    private fun copyUriToCache(uri: Uri): Uri? {
        if (uri.authority == "${packageName}.fileprovider" || uri.scheme == "file") {
            return uri
        }
        return runCatching {
            val destDir = java.io.File(cacheDir, "shared_incoming").apply { mkdirs() }
            // Preserve the sender's filename so the viewer's filename chain
            // (provider DISPLAY_NAME → path segment → fallback) shows the real name.
            val sourceName = queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')?.let {
                    java.net.URLDecoder.decode(it, Charsets.UTF_8.name())
                }
            val safeName = sanitizeFileName(sourceName) ?: "document"
            val uriHash = uri.toString().hashCode().toUInt().toString(16)
            val dest = java.io.File(destDir, "${safeName}_$uriHash.pdf")
            val temp = java.io.File(destDir, "${safeName}_${uriHash}.tmp")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
                if (!temp.renameTo(dest)) {
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                dest
            )
        }.getOrNull()
    }

    /** Sender's display name for [uri], or null when it can't be determined. */
    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            }
    }.getOrNull()

    /** Keeps alphanumerics, dots, dashes and underscores; drops everything else. */
    private fun sanitizeFileName(name: String?): String? {
        val cleaned = name
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.trim('_', '.', ' ')
            ?.take(80)
        return cleaned?.takeIf { it.isNotBlank() }
    }

    /** Returns a non-colliding file for [fileName] inside [dir]. */
    private fun uniqueFile(dir: java.io.File, fileName: String): java.io.File {
        var candidate = java.io.File(dir, fileName)
        var n = 1
        val stem = fileName.substringBeforeLast('.')
        while (candidate.exists()) {
            n++
            candidate = java.io.File(dir, "$stem ($n).pdf")
        }
        return candidate
    }
}
