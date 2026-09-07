package com.morphdrop.app.ui.screens.markdown

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.ui.theme.MorphDropTheme
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================= Structured Markdown Block Model =================

sealed interface ParsedBlock {
    val id: String
    data class Header(val level: Int, val title: String, override val id: String) : ParsedBlock
    data class Code(val language: String, val code: String, override val id: String) : ParsedBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, override val id: String) : ParsedBlock
    data class RichText(val markdown: String, override val id: String) : ParsedBlock
}

fun parseTableCells(line: String): List<String> {
    var trimmed = line.trim()
    if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
    if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length - 1)
    return trimmed.split("|").map { it.trim() }
}

fun formatInlineMarkdown(text: String, textColor: Color, codeBg: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            // Link: [text](url) or Image: ![alt](url)
            val isImage = (text[i] == '!' && i + 1 < len && text[i + 1] == '[')
            val isLink = (text[i] == '[')
            if (isImage || isLink) {
                val bracketStart = if (isImage) i + 1 else i
                val closeBracket = text.indexOf(']', bracketStart + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val displayText = text.substring(bracketStart + 1, closeBracket)
                        if (!isImage) {
                            withStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)) {
                                append(displayText)
                            }
                        } else {
                            append(displayText)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // Bold: **text**
            if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val boldContent = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(boldContent)
                    }
                    i = end + 2
                    continue
                }
            }
            // Code: `code`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    val codeContent = text.substring(i + 1, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = codeBg,
                            color = textColor
                        )
                    ) {
                        append(" $codeContent ")
                    }
                    i = end + 1
                    continue
                }
            }
            // Italic: *text*
            if (text[i] == '*' && (i + 1 == len || text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    val italicContent = text.substring(i + 1, end)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                        append(italicContent)
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}

fun parseMarkdownToBlocks(markdown: String): List<ParsedBlock> {
    if (markdown.isBlank()) return emptyList()
    val blocks = mutableListOf<ParsedBlock>()
    val lines = markdown.lines()
    var i = 0
    var blockCounter = 0
    val textBuffer = StringBuilder()

    fun flushText() {
        val trimmedText = textBuffer.toString().trim()
        if (trimmedText.isNotEmpty()) {
            blocks.add(ParsedBlock.RichText(trimmedText, "text_${blockCounter++}"))
            textBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Check for fenced code blocks: ``` or ~~~
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            flushText()
            val fence = if (trimmed.startsWith("```")) "```" else "~~~"
            val lang = trimmed.removePrefix(fence).trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size) {
                val codeLine = lines[i]
                if (codeLine.trim().startsWith(fence)) {
                    break
                }
                codeLines.add(codeLine)
                i++
            }
            blocks.add(ParsedBlock.Code(lang, codeLines.joinToString("\n"), "code_${blockCounter++}"))
            i++
            continue
        }

        // Check for Markdown Table: line containing | followed by separator line containing ---
        if (trimmed.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().contains("---") && lines[i + 1].trim().startsWith("|")) {
            flushText()
            val headerLine = trimmed
            val headers = parseTableCells(headerLine)

            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val rowLine = lines[i].trim()
                if (!rowLine.contains("---")) {
                    val rowCells = parseTableCells(rowLine)
                    if (rowCells.isNotEmpty()) {
                        rows.add(rowCells)
                    }
                }
                i++
            }
            if (headers.isNotEmpty()) {
                blocks.add(ParsedBlock.Table(headers, rows, "table_${blockCounter++}"))
            }
            continue
        }

        // Check for Headings: # Heading
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                flushText()
                val title = trimmed.drop(level).trim()
                blocks.add(ParsedBlock.Header(level, title, "header_${blockCounter++}"))
                i++
                continue
            }
        }

        textBuffer.append(line).append("\n")
        i++
    }
    flushText()
    return blocks
}

// ================= Main Composable Screen =================

@Composable
fun MarkdownViewerScreen(
    uriString: String,
    onNavigateBack: () -> Unit,
    viewModel: MarkdownViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(uriString) {
        viewModel.loadFile(uriString)
    }

    MarkdownViewerContent(
        state = state,
        uriString = uriString,
        onNavigateBack = onNavigateBack,
        onToggleSearch = { viewModel.toggleSearch() },
        onSetSearchQuery = { viewModel.setSearchQuery(it) },
        onSetSearchMatches = { viewModel.setSearchMatches(it) },
        onNextMatch = { viewModel.nextMatch() },
        onPrevMatch = { viewModel.prevMatch() },
        onSetTextSize = { viewModel.setTextSize(it) },
        onIncreaseTextSize = { viewModel.increaseTextSize() },
        onDecreaseTextSize = { viewModel.decreaseTextSize() },
        onSetReadingMode = { viewModel.setReadingMode(it) },
        onToggleImmersiveMode = { viewModel.toggleImmersiveMode() },
        onDismissLargeFileBanner = { viewModel.dismissLargeFileBanner() },
        onRetry = { viewModel.loadFile(uriString) },
        onGetShareableFileUri = { viewModel.getShareableFileUri(it) },
        onGenerateHtmlPreviewUri = { viewModel.generateHtmlPreviewUri() },
        onGeneratePdfFile = { viewModel.generatePdfFile() },
        onSavePdfToUri = { viewModel.savePdfToUri(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MarkdownViewerContent(
    state: MarkdownViewerState,
    uriString: String,
    onNavigateBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSearchMatches: (List<SearchMatch>) -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    onSetTextSize: (Float) -> Unit,
    onIncreaseTextSize: () -> Unit,
    onDecreaseTextSize: () -> Unit,
    onSetReadingMode: (ReadingMode) -> Unit,
    onToggleImmersiveMode: () -> Unit,
    onDismissLargeFileBanner: () -> Unit,
    onRetry: () -> Unit,
    onGetShareableFileUri: suspend (String) -> Uri?,
    onGenerateHtmlPreviewUri: suspend () -> Uri?,
    onGeneratePdfFile: suspend () -> Uri?,
    onSavePdfToUri: suspend (Uri) -> Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var scrollRatio by remember { mutableFloatStateOf(0f) }
    var lastRecordedTextSize by remember { mutableFloatStateOf(state.textSizeSp) }

    // Anchor relative scroll position when scrolling normally
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0 && !scrollState.isScrollInProgress) {
            scrollRatio = scrollState.value.toFloat() / scrollState.maxValue
        }
    }

    // When textSizeSp changes (zooming in or out), restore the proportional scroll position
    // so the viewport does not jump upside or lose the user's reading position
    LaunchedEffect(state.textSizeSp) {
        if (state.textSizeSp != lastRecordedTextSize) {
            lastRecordedTextSize = state.textSizeSp
            val targetRatio = if (scrollState.maxValue > 0) {
                scrollState.value.toFloat() / scrollState.maxValue
            } else {
                scrollRatio
            }
            if (targetRatio > 0.01f) {
                delay(60)
                val newScroll = (targetRatio * scrollState.maxValue).toInt()
                scrollState.scrollTo(newScroll)
            }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    var showMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }

    // Intercept hardware/system back button
    BackHandler(enabled = state.isSearchActive || state.immersiveMode) {
        if (state.isSearchActive) {
            onToggleSearch()
        } else if (state.immersiveMode) {
            onToggleImmersiveMode()
        }
    }

    // Auto-focus search input and show keyboard when search is opened
    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            delay(120)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Reading Mode Colors
    val defaultBgColor = MaterialTheme.colorScheme.background
    val defaultTextColor = MaterialTheme.colorScheme.onBackground

    val bgColor = when (state.readingMode) {
        ReadingMode.DEFAULT -> defaultBgColor
        ReadingMode.NIGHT -> Color(0xFF121212)
        ReadingMode.SEPIA -> Color(0xFFF5E6D3)
    }

    val textColor = when (state.readingMode) {
        ReadingMode.DEFAULT -> defaultTextColor
        ReadingMode.NIGHT -> Color(0xFFE0E0E0)
        ReadingMode.SEPIA -> Color(0xFF3E2723)
    }

    val codeCardBg = when (state.readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ReadingMode.NIGHT -> Color(0xFF1A202C)
        ReadingMode.SEPIA -> Color(0xFFEBE0D2)
    }

    val codeHeaderBg = when (state.readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
        ReadingMode.NIGHT -> Color(0xFF161B22)
        ReadingMode.SEPIA -> Color(0xFFDFD4C4)
    }

    val borderColor = when (state.readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.outlineVariant
        ReadingMode.NIGHT -> Color(0xFF30363D)
        ReadingMode.SEPIA -> Color(0xFFD7CCC8)
    }

    // High-contrast floating controls and sheet colors for Night, Sepia, and Light modes
    val controlBgColor = when (state.readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
        ReadingMode.NIGHT -> Color(0xFF21262D)
        ReadingMode.SEPIA -> Color(0xFFEBE0D2)
    }

    val controlBorderColor = when (state.readingMode) {
        ReadingMode.DEFAULT -> borderColor
        ReadingMode.NIGHT -> Color(0xFF38444D)
        ReadingMode.SEPIA -> Color(0xFFD7CCC8)
    }

    val controlIconColor = when (state.readingMode) {
        ReadingMode.DEFAULT -> textColor
        ReadingMode.NIGHT -> Color(0xFFF0F6FC)
        ReadingMode.SEPIA -> Color(0xFF3E2723)
    }

    val tocFabContainer = when (state.readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.primaryContainer
        ReadingMode.NIGHT -> Color(0xFF21262D)
        ReadingMode.SEPIA -> Color(0xFFE0D3C1)
    }

    val tocFabContent = when (state.readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.onPrimaryContainer
        ReadingMode.NIGHT -> Color(0xFF58A6FF)
        ReadingMode.SEPIA -> Color(0xFF3E2723)
    }

    // Window Insets & Status Bar Controller:
    // If background luminance is high (>0.5), status bar icons MUST be dark.
    // In Night mode, background is black, so icons MUST be white.
    val isDark = state.readingMode == ReadingMode.NIGHT
    val isLightBg = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) > 0.5f
    val activity = context as? ComponentActivity
    DisposableEffect(isLightBg, state.immersiveMode) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = isLightBg
            insetsController.isAppearanceLightNavigationBars = isLightBg

            val barStyle = if (isLightBg) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
            act.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

            if (state.immersiveMode) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }

    // Save as PDF File Picker Launcher (Storage Access Framework)
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = onSavePdfToUri(uri)
                if (success) {
                    Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Markwon instance configured for inline rich text formatting
    val markwon = remember(state.readingMode, state.textSizeSp) {
        val density = context.resources.displayMetrics.density

        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    val codeBg = when {
                        isDark -> AndroidColor.parseColor("#161B22")
                        state.readingMode == ReadingMode.SEPIA -> AndroidColor.parseColor("#EFEBE9")
                        else -> AndroidColor.parseColor("#F6F8FA")
                    }
                    builder
                        .codeBlockBackgroundColor(codeBg)
                        .codeBlockTextColor(textColor.toArgb())
                        .codeBackgroundColor(codeBg)
                        .codeTextColor(textColor.toArgb())
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockMargin((8 * density).toInt())
                }
            })
            .build()
    }

    // Parse blocks
    val parsedBlocks = remember(state.content) {
        parseMarkdownToBlocks(state.content)
    }

    // Bring into view requesters for Table of Contents and search match navigation
    val blockRequesters = remember(parsedBlocks) {
        parsedBlocks.indices.associateWith { BringIntoViewRequester() }
    }

    // Extract searchable plain text per block
    val blockSearchableTexts = remember(parsedBlocks, markwon) {
        parsedBlocks.map { block ->
            when (block) {
                is ParsedBlock.Header -> block.title
                is ParsedBlock.Code -> block.code
                is ParsedBlock.Table -> (block.headers + block.rows.flatten()).joinToString(" ")
                is ParsedBlock.RichText -> {
                    val node = markwon.parse(block.markdown)
                    markwon.render(node).toString()
                }
            }
        }
    }

    // Compute search matches with exact 1:1 character alignment per block
    val computedMatches = remember(state.searchQuery, blockSearchableTexts) {
        val q = state.searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            val list = mutableListOf<SearchMatch>()
            var globalId = 0
            blockSearchableTexts.forEachIndexed { blockIdx, text ->
                var idx = text.indexOf(q, 0, ignoreCase = true)
                var countInBlock = 0
                while (idx >= 0) {
                    list.add(
                        SearchMatch(
                            matchId = globalId++,
                            blockIndex = blockIdx,
                            matchIndexInBlock = countInBlock++,
                            start = idx,
                            end = idx + q.length
                        )
                    )
                    idx = text.indexOf(q, idx + q.length, ignoreCase = true)
                }
            }
            list
        }
    }

    // Sync computed matches with ViewModel
    LaunchedEffect(computedMatches) {
        onSetSearchMatches(computedMatches)
    }

    // Auto-scroll when navigating through search matches with arrows or search query change
    LaunchedEffect(state.currentSearchIndex, computedMatches) {
        if (state.isSearchActive && computedMatches.isNotEmpty()) {
            val activeMatch = computedMatches.getOrNull(state.currentSearchIndex)
            if (activeMatch != null) {
                blockRequesters[activeMatch.blockIndex]?.bringIntoView()
            }
        }
    }

    // Reading Mode Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Reading Mode",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.readingMode == ReadingMode.DEFAULT,
                        onClick = { onSetReadingMode(ReadingMode.DEFAULT) },
                        label = { Text("Default") }
                    )
                    FilterChip(
                        selected = state.readingMode == ReadingMode.NIGHT,
                        onClick = { onSetReadingMode(ReadingMode.NIGHT) },
                        label = { Text("Night") }
                    )
                    FilterChip(
                        selected = state.readingMode == ReadingMode.SEPIA,
                        onClick = { onSetReadingMode(ReadingMode.SEPIA) },
                        label = { Text("Sepia") }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Table of Contents Bottom Sheet (Polished M3 layout with hierarchy and badges)
    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            containerColor = if (isDark) Color(0xFF161B22) else MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Header with icon, title, counter chip, and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF1F2937) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF60A5FA) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Table of Contents",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        if (state.tableOfContents.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF238636).copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${state.tableOfContents.size} sections",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isDark) Color(0xFF7EE787) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showTocSheet = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Table of Contents", tint = textColor)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (isDark) Color(0xFF30363D) else borderColor.copy(alpha = 0.6f)
                )

                if (state.tableOfContents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No headings found in this document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.tableOfContents.size) { idx ->
                            val item = state.tableOfContents[idx]
                            val indent = ((item.level - 1) * 16).dp

                            Surface(
                                onClick = {
                                    showTocSheet = false
                                    val blockIdx = parsedBlocks.indexOfFirst {
                                        it is ParsedBlock.Header && it.title.equals(item.title, ignoreCase = true)
                                    }
                                    if (blockIdx != -1) {
                                        coroutineScope.launch {
                                            blockRequesters[blockIdx]?.bringIntoView()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF21262D).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF30363D) else borderColor.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (item.level) {
                                            1 -> if (isDark) Color(0xFF388BFD).copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer
                                            2 -> if (isDark) Color(0xFF238636).copy(alpha = 0.25f) else MaterialTheme.colorScheme.secondaryContainer
                                            else -> if (isDark) Color(0xFF30363D) else MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        Text(
                                            text = "H${item.level}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (item.level) {
                                                1 -> if (isDark) Color(0xFF79C0FF) else MaterialTheme.colorScheme.onPrimaryContainer
                                                2 -> if (isDark) Color(0xFF7EE787) else MaterialTheme.colorScheme.onSecondaryContainer
                                                else -> textColor.copy(alpha = 0.8f)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = item.title,
                                        style = when (item.level) {
                                            1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            2 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                            else -> MaterialTheme.typography.bodyMedium
                                        },
                                        color = textColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = if (state.immersiveMode) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!state.immersiveMode) {
                TopAppBar(
                    title = {
                        if (state.isSearchActive) {
                            // Balanced, elegant pill-shaped search input field
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (state.readingMode == ReadingMode.NIGHT) Color(0xFF21262D) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.5.dp, if (state.readingMode == ReadingMode.NIGHT) Color(0xFF38444D) else borderColor.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = textColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = state.searchQuery,
                                        onValueChange = { query ->
                                            onSetSearchQuery(query)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                                        cursorBrush = SolidColor(textColor),
                                        decorationBox = { innerTextField ->
                                            if (state.searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search in document...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = textColor.copy(alpha = 0.5f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { onSetSearchQuery("") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = textColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // File name title: truncated with ellipsis on long names, toast on long-press
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                        onLongClick = {
                                            Toast.makeText(context, state.fileName, Toast.LENGTH_LONG).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = state.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textColor
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (state.isSearchActive) {
                                onToggleSearch()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (state.isSearchActive) {
                            // Dedicated controls beside search bar: match count, prev/next arrows, and close
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (state.searchMatches.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (state.readingMode == ReadingMode.NIGHT) Color(0xFF388BFD).copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(end = 2.dp)
                                    ) {
                                        Text(
                                            text = "${state.currentSearchIndex + 1}/${state.searchMatches.size}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (state.readingMode == ReadingMode.NIGHT) Color(0xFF79C0FF) else MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onPrevMatch,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Previous Match",
                                            tint = textColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onNextMatch,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Next Match",
                                            tint = textColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onToggleSearch,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Search",
                                        tint = textColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            // Fullscreen / Immersive mode toggle icon
                            IconButton(onClick = onToggleImmersiveMode) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen Mode")
                            }

                            // Search button
                            IconButton(onClick = {
                                onToggleSearch()
                                onSetSearchQuery("")
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }

                            // Options menu
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Reading Mode") },
                                    onClick = {
                                        showMenu = false
                                        showSettingsSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as PDF") },
                                    onClick = {
                                        showMenu = false
                                        coroutineScope.launch {
                                            val pdfUri = onGeneratePdfFile()
                                            if (pdfUri != null) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Markdown as PDF"))
                                            } else {
                                                Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save as PDF") },
                                    onClick = {
                                        showMenu = false
                                        val baseName = state.fileName.removeSuffix(".md").ifBlank { "Document" }
                                        savePdfLauncher.launch("$baseName.pdf")
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share File (.md)") },
                                    onClick = {
                                        showMenu = false
                                        coroutineScope.launch {
                                            val fileUri = onGetShareableFileUri(uriString)
                                            if (fileUri != null) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/markdown"
                                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Markdown File"))
                                            } else {
                                                Toast.makeText(context, "Could not prepare file for sharing", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Raw Text") },
                                    onClick = {
                                        showMenu = false
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, state.content)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Raw Text"))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy All Text") },
                                    onClick = {
                                        showMenu = false
                                        clipboardManager.setText(AnnotatedString(state.content))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Preview in Browser (HTML)") },
                                    onClick = {
                                        showMenu = false
                                        coroutineScope.launch {
                                            val htmlUri = onGenerateHtmlPreviewUri()
                                            if (htmlUri != null) {
                                                try {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(htmlUri, "text/html")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(Intent.createChooser(browserIntent, "Open in Browser"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Failed to generate HTML preview", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = bgColor,
                        titleContentColor = textColor,
                        navigationIconContentColor = textColor,
                        actionIconContentColor = textColor
                    )
                )
            }
        }
    ) { padding ->
        val effectivePadding = if (state.immersiveMode) PaddingValues(0.dp) else padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(effectivePadding)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Loading ${state.fileName}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
            } else if (state.isError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(36.dp))
                            Text(
                                text = state.errorMessage ?: "Failed to open markdown file",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.isLargeFile && state.showLargeFileBanner) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Large file (>5MB) • High-performance reading mode enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onDismissLargeFileBanner,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Document Scrollable Column
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        parsedBlocks.forEachIndexed { blockIndex, block ->
                            val requester = blockRequesters[blockIndex]
                            val blockModifier = if (requester != null) {
                                Modifier.bringIntoViewRequester(requester)
                            } else {
                                Modifier
                            }

                            val activeMatch = if (state.isSearchActive && state.searchMatches.isNotEmpty()) {
                                state.searchMatches.getOrNull(state.currentSearchIndex)
                            } else null

                            val activeMatchIndexInThisBlock = if (activeMatch?.blockIndex == blockIndex) {
                                activeMatch.matchIndexInBlock
                            } else -1

                            val activeQuery = if (state.isSearchActive) state.searchQuery.trim() else ""

                            when (block) {
                                is ParsedBlock.Header -> {
                                    MarkdownHeaderItem(
                                        level = block.level,
                                        title = block.title,
                                        textColor = textColor,
                                        baseTextSizeSp = state.textSizeSp,
                                        searchQuery = activeQuery,
                                        activeMatchIndexInThisBlock = activeMatchIndexInThisBlock,
                                        modifier = blockModifier
                                    )
                                }
                                is ParsedBlock.Code -> {
                                    MarkdownCodeCard(
                                        language = block.language,
                                        code = block.code,
                                        backgroundColor = codeCardBg,
                                        headerColor = codeHeaderBg,
                                        borderColor = borderColor,
                                        textColor = textColor,
                                        textSizeSp = state.textSizeSp,
                                        searchQuery = activeQuery,
                                        activeMatchIndexInThisBlock = activeMatchIndexInThisBlock,
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(block.code))
                                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = blockModifier
                                    )
                                }
                                is ParsedBlock.Table -> {
                                    MarkdownTableCard(
                                        headers = block.headers,
                                        rows = block.rows,
                                        borderColor = borderColor,
                                        textColor = textColor,
                                        headerBg = codeHeaderBg,
                                        readingMode = state.readingMode,
                                        textSizeSp = state.textSizeSp,
                                        modifier = blockModifier
                                    )
                                }
                                is ParsedBlock.RichText -> {
                                    MarkdownRichTextItem(
                                        markdown = block.markdown,
                                        markwon = markwon,
                                        textColor = textColor,
                                        textSizeSp = state.textSizeSp,
                                        searchQuery = activeQuery,
                                        activeMatchIndexInThisBlock = activeMatchIndexInThisBlock,
                                        modifier = blockModifier
                                    )
                                }
                            }
                        }

                        // Bottom spacer to ensure comfortable scrolling above floating pill
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }

                // ================= Floating Material 3 Controls =================

                // 1. Floating Circular Button: Table of Contents (Bottom Start)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    FloatingActionButton(
                        onClick = { showTocSheet = true },
                        shape = CircleShape,
                        containerColor = tocFabContainer,
                        contentColor = tocFabContent,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .size(52.dp)
                            .border(
                                width = if (state.readingMode == ReadingMode.NIGHT) 1.dp else 0.dp,
                                color = if (state.readingMode == ReadingMode.NIGHT) Color(0xFF38444D) else Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Table of Contents",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Floating Pill: Text Size (+ / -) Control (Bottom End)
                // Size number removed per user instruction: "in that dont that size number"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = controlBgColor,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, controlBorderColor),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            IconButton(
                                onClick = onDecreaseTextSize,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "Decrease Text Size",
                                    tint = controlIconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(controlBorderColor.copy(alpha = 0.6f))
                            )
                            IconButton(
                                onClick = onIncreaseTextSize,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Increase Text Size",
                                    tint = controlIconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Fullscreen Exit Floating HUD (Shown only when in Immersive Mode)
                if (state.immersiveMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            onClick = onToggleImmersiveMode,
                            shape = RoundedCornerShape(24.dp),
                            color = if (state.readingMode == ReadingMode.NIGHT) Color(0xFF21262D).copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = if (state.readingMode == ReadingMode.NIGHT) Color(0xFFF0F6FC) else MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, if (state.readingMode == ReadingMode.NIGHT) Color(0xFF38444D) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.FullscreenExit,
                                    contentDescription = "Exit Fullscreen",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exit Fullscreen",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= Component Composables =================

fun highlightSearchText(
    text: String,
    query: String,
    activeMatchIndexInThisBlock: Int,
    normalColor: Color
): AnnotatedString {
    if (query.isEmpty() || !text.contains(query, ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var idx = text.indexOf(query, 0, ignoreCase = true)
        var lastIdx = 0
        var matchCount = 0
        while (idx >= 0) {
            if (idx > lastIdx) {
                append(text.substring(lastIdx, idx))
            }
            val isCurrent = (matchCount == activeMatchIndexInThisBlock)
            val bg = if (isCurrent) Color.Cyan else Color.Yellow
            withStyle(SpanStyle(background = bg, color = Color.Black)) {
                append(text.substring(idx, idx + query.length))
            }
            matchCount++
            lastIdx = idx + query.length
            idx = text.indexOf(query, lastIdx, ignoreCase = true)
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}

@Composable
fun MarkdownHeaderItem(
    level: Int,
    title: String,
    textColor: Color,
    baseTextSizeSp: Float,
    searchQuery: String = "",
    activeMatchIndexInThisBlock: Int = -1,
    modifier: Modifier = Modifier
) {
    val (sizeMultiplier, weight) = when (level) {
        1 -> 1.5f to FontWeight.Bold
        2 -> 1.3f to FontWeight.Bold
        3 -> 1.15f to FontWeight.SemiBold
        4 -> 1.05f to FontWeight.SemiBold
        else -> 1.0f to FontWeight.Medium
    }

    val annotatedTitle = remember(title, searchQuery, activeMatchIndexInThisBlock, textColor) {
        highlightSearchText(title, searchQuery, activeMatchIndexInThisBlock, textColor)
    }

    Text(
        text = annotatedTitle,
        fontSize = (baseTextSizeSp * sizeMultiplier).sp,
        fontWeight = weight,
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (level <= 2) 12.dp else 6.dp, bottom = 4.dp)
    )
}

@Composable
fun MarkdownCodeCard(
    language: String,
    code: String,
    backgroundColor: Color,
    headerColor: Color,
    borderColor: Color,
    textColor: Color,
    textSizeSp: Float,
    onCopy: () -> Unit,
    searchQuery: String = "",
    activeMatchIndexInThisBlock: Int = -1,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header with language tag and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = textColor.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val annotatedCode = remember(code, searchQuery, activeMatchIndexInThisBlock, textColor) {
                highlightSearchText(code, searchQuery, activeMatchIndexInThisBlock, textColor)
            }

            // Horizontally scrollable code block container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = annotatedCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (textSizeSp - 2).coerceAtLeast(10f).sp,
                    color = textColor,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun MarkdownTableCard(
    headers: List<String>,
    rows: List<List<String>>,
    borderColor: Color,
    textColor: Color,
    headerBg: Color,
    readingMode: ReadingMode,
    textSizeSp: Float,
    codeBgColor: Color = if (readingMode == ReadingMode.NIGHT) Color(0xFF2D333B) else Color(0xFFEAEAEA),
    modifier: Modifier = Modifier
) {
    val evenRowBg = when (readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.surface
        ReadingMode.NIGHT -> Color(0xFF161B22)
        ReadingMode.SEPIA -> Color(0xFFF5E6D3)
    }

    val oddRowBg = when (readingMode) {
        ReadingMode.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ReadingMode.NIGHT -> Color(0xFF0D1117)
        ReadingMode.SEPIA -> Color(0xFFECE0D1)
    }

    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 1)
    val colWidths = remember(headers, rows, textSizeSp) {
        (0 until colCount).map { colIdx ->
            val headerLen = headers.getOrNull(colIdx)?.length ?: 0
            val maxCellLen = rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
            val longest = maxOf(headerLen, maxCellLen)
            when {
                longest <= 12 -> 110.dp
                longest <= 25 -> 150.dp
                longest <= 50 -> 210.dp
                longest <= 90 -> 270.dp
                else -> 320.dp
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        color = evenRowBg,
        modifier = modifier.fillMaxWidth()
    ) {
        // Horizontally scrollable table container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                // Table Header
                Row(
                    modifier = Modifier
                        .background(headerBg)
                        .border(width = 0.5.dp, color = borderColor)
                ) {
                    (0 until colCount).forEach { colIdx ->
                        val headerText = headers.getOrElse(colIdx) { "" }
                        Box(
                            modifier = Modifier
                                .width(colWidths[colIdx])
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = formatInlineMarkdown(headerText, textColor, codeBgColor),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                fontSize = (textSizeSp - 1).sp,
                                color = textColor
                            )
                        }
                    }
                }

                // Table Rows
                rows.forEachIndexed { rowIndex, rowCells ->
                    val rowBg = if (rowIndex % 2 == 0) evenRowBg else oddRowBg
                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .border(width = 0.5.dp, color = borderColor.copy(alpha = 0.4f))
                    ) {
                        (0 until colCount).forEach { colIdx ->
                            val cellText = rowCells.getOrElse(colIdx) { "" }
                            Box(
                                modifier = Modifier
                                    .width(colWidths[colIdx])
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = formatInlineMarkdown(cellText, textColor, codeBgColor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = (textSizeSp - 1).sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownRichTextItem(
    markdown: String,
    markwon: Markwon,
    textColor: Color,
    textSizeSp: Float,
    searchQuery: String = "",
    activeMatchIndexInThisBlock: Int = -1,
    modifier: Modifier = Modifier
) {
    val spanned = remember(markdown, markwon, textSizeSp) {
        val node = markwon.parse(markdown)
        markwon.render(node)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                textSize = textSizeSp
                setTextIsSelectable(true)
                setPadding(0, 4, 0, 8)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.setTextColor(textColor.toArgb())
            tv.textSize = textSizeSp

            if (searchQuery.isNotEmpty()) {
                val text = spanned.toString()
                val spannable = SpannableString(spanned)
                var idx = text.indexOf(searchQuery, 0, ignoreCase = true)
                var matchCount = 0
                while (idx >= 0) {
                    val isCurrent = (matchCount == activeMatchIndexInThisBlock)
                    val bg = if (isCurrent) AndroidColor.CYAN else AndroidColor.YELLOW
                    spannable.setSpan(BackgroundColorSpan(bg), idx, idx + searchQuery.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(ForegroundColorSpan(AndroidColor.BLACK), idx, idx + searchQuery.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    matchCount++
                    idx = text.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
                }
                tv.text = spannable
            } else {
                if (tv.text !== spanned) {
                    tv.text = spanned
                }
            }
        }
    )
}

// ================= Previews =================

@Preview(name = "Light Mode - Default", showBackground = true)
@Composable
private fun MarkdownViewerPreviewLight() {
    MorphDropTheme(darkTheme = false) {
        MarkdownViewerContent(
            state = MarkdownViewerState(
                fileName = "documentation_guide.md",
                content = """
                    # MorphDrop Architecture
                    MorphDrop is an offline-first **document conversion** toolkit for Android.
                    
                    ## Features
                    - High performance conversions
                    - Offline native Markdown viewer
                    - PDF and image processing
                    
                    | Tool | Format | Support |
                    |---|---|---|
                    | Markwon | Markdown | Native |
                    | PDFBox | PDF | Vector |
                    | Coil | Images | Dynamic |
                    
                    ```bash
                    ./gradlew assembleDebug --quiet && adb install app-debug.apk
                    ```
                    
                    ### Getting Started
                    Run the commands above to build the project.
                """.trimIndent(),
                readingMode = ReadingMode.DEFAULT,
                textSizeSp = 16f,
                tableOfContents = listOf(
                    TableOfContentItem(1, "MorphDrop Architecture", 0),
                    TableOfContentItem(2, "Features", 2),
                    TableOfContentItem(3, "Getting Started", 6)
                )
            ),
            uriString = "content://sample/preview.md",
            onNavigateBack = {},
            onToggleSearch = {},
            onSetSearchQuery = {},
            onSetSearchMatches = {},
            onNextMatch = {},
            onPrevMatch = {},
            onSetTextSize = {},
            onIncreaseTextSize = {},
            onDecreaseTextSize = {},
            onSetReadingMode = {},
            onToggleImmersiveMode = {},
            onDismissLargeFileBanner = {},
            onRetry = {},
            onGetShareableFileUri = { null },
            onGenerateHtmlPreviewUri = { null },
            onGeneratePdfFile = { null },
            onSavePdfToUri = { false }
        )
    }
}

@Preview(name = "Dark Mode - Night", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun MarkdownViewerPreviewDark() {
    MorphDropTheme(darkTheme = true) {
        MarkdownViewerContent(
            state = MarkdownViewerState(
                fileName = "release_notes_v1.3.1.md",
                content = """
                    # Release Notes v1.3.1
                    Night reading mode activated with high contrast typography and syntax styling.
                    
                    ```bash
                    git commit -m "feat(markdown): add horizontal scrolling and TOC"
                    ```
                    
                    | Metric | v1.2 | v1.3 |
                    |---|---|---|
                    | Startup | 220ms | 110ms |
                    | Memory | 64MB | 42MB |
                """.trimIndent(),
                readingMode = ReadingMode.NIGHT,
                textSizeSp = 18f,
                tableOfContents = listOf(
                    TableOfContentItem(1, "Release Notes v1.3.1", 0)
                )
            ),
            uriString = "content://sample/release.md",
            onNavigateBack = {},
            onToggleSearch = {},
            onSetSearchQuery = {},
            onSetSearchMatches = {},
            onNextMatch = {},
            onPrevMatch = {},
            onSetTextSize = {},
            onIncreaseTextSize = {},
            onDecreaseTextSize = {},
            onSetReadingMode = {},
            onToggleImmersiveMode = {},
            onDismissLargeFileBanner = {},
            onRetry = {},
            onGetShareableFileUri = { null },
            onGenerateHtmlPreviewUri = { null },
            onGeneratePdfFile = { null },
            onSavePdfToUri = { false }
        )
    }
}

@Preview(name = "Sepia Reading Mode", showBackground = true)
@Composable
private fun MarkdownViewerPreviewSepia() {
    MorphDropTheme(darkTheme = false) {
        MarkdownViewerContent(
            state = MarkdownViewerState(
                fileName = "book_chapter_1.md",
                content = """
                    # Chapter 1: The Beginning
                    Warm sepia mode designed for extended, glare-free reading.
                    
                    | Section | Status |
                    |---|---|
                    | Prologue | Completed |
                    | First Act | In Progress |
                """.trimIndent(),
                readingMode = ReadingMode.SEPIA,
                textSizeSp = 18f,
                tableOfContents = listOf(
                    TableOfContentItem(1, "Chapter 1: The Beginning", 0)
                )
            ),
            uriString = "content://sample/chapter1.md",
            onNavigateBack = {},
            onToggleSearch = {},
            onSetSearchQuery = {},
            onSetSearchMatches = {},
            onNextMatch = {},
            onPrevMatch = {},
            onSetTextSize = {},
            onIncreaseTextSize = {},
            onDecreaseTextSize = {},
            onSetReadingMode = {},
            onToggleImmersiveMode = {},
            onDismissLargeFileBanner = {},
            onRetry = {},
            onGetShareableFileUri = { null },
            onGenerateHtmlPreviewUri = { null },
            onGeneratePdfFile = { null },
            onSavePdfToUri = { false }
        )
    }
}

@Preview(name = "Search Active Mode", showBackground = true)
@Composable
private fun MarkdownViewerPreviewSearch() {
    MorphDropTheme(darkTheme = false) {
        MarkdownViewerContent(
            state = MarkdownViewerState(
                fileName = "project_spec.md",
                content = "# Project Spec\nSearching for keywords inside this document.",
                isSearchActive = true,
                searchQuery = "document",
                searchMatches = listOf(SearchMatch(0, 0, 0, 25, 33)),
                currentSearchIndex = 0,
                tableOfContents = listOf(
                    TableOfContentItem(1, "Project Spec", 0)
                )
            ),
            uriString = "content://sample/spec.md",
            onNavigateBack = {},
            onToggleSearch = {},
            onSetSearchQuery = {},
            onSetSearchMatches = {},
            onNextMatch = {},
            onPrevMatch = {},
            onSetTextSize = {},
            onIncreaseTextSize = {},
            onDecreaseTextSize = {},
            onSetReadingMode = {},
            onToggleImmersiveMode = {},
            onDismissLargeFileBanner = {},
            onRetry = {},
            onGetShareableFileUri = { null },
            onGenerateHtmlPreviewUri = { null },
            onGeneratePdfFile = { null },
            onSavePdfToUri = { false }
        )
    }
}
