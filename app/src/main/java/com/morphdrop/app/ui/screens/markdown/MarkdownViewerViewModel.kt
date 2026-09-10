package com.morphdrop.app.ui.screens.markdown

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import javax.inject.Inject

data class SearchMatch(
    val matchId: Int = 0,
    val blockIndex: Int = 0,
    val matchIndexInBlock: Int = 0,
    val start: Int = 0,
    val end: Int = 0
)

data class TableOfContentItem(
    val level: Int,
    val title: String,
    val lineOffset: Int
)

data class MarkdownViewerState(
    val isLoading: Boolean = false,
    val isLargeFile: Boolean = false,
    val showLargeFileBanner: Boolean = true,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val fileName: String = "Document.md",
    val fileSizeBytes: Long = 0L,
    val content: String = "",
    val textSizeSp: Float = 16f,
    val readingMode: ReadingMode = ReadingMode.DEFAULT,
    val immersiveMode: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val currentSearchIndex: Int = 0,
    val tableOfContents: List<TableOfContentItem> = emptyList()
)

@HiltViewModel
class MarkdownViewerViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MarkdownViewerState())
    val uiState: StateFlow<MarkdownViewerState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                settingsRepository.readingMode.collectLatest { mode ->
                    _uiState.value = _uiState.value.copy(readingMode = mode)
                }
            }
            launch {
                settingsRepository.markdownTextSize.collectLatest { size ->
                    _uiState.value = _uiState.value.copy(textSizeSp = size)
                }
            }
        }
    }

    fun loadFile(uriString: String) {
        if (uriString.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isError = true,
                errorMessage = "Invalid file URI"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isError = false)

            try {
                val uri = uriString.toUri()
                val context = getApplication<Application>()
                
                withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver

                    // Extract actual display name
                    var displayName = uri.lastPathSegment?.substringAfterLast("/") ?: "Document.md"
                    if (uri.scheme == "content") {
                        try {
                            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex != -1) {
                                        val name = cursor.getString(nameIndex)
                                        if (!name.isNullOrBlank()) displayName = name
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MarkdownViewer", "Could not query display name", e)
                        }
                    }
                    if (!displayName.endsWith(".md", ignoreCase = true) && !displayName.contains(".")) {
                        displayName = "$displayName.md"
                    }
                    
                    var fileSize = 0L
                    try {
                        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            fileSize = pfd.statSize
                        }
                    } catch (e: Exception) {
                        Log.e("MarkdownViewer", "Could not determine file size", e)
                    }

                    // Large file handling (> 5MB)
                    val isLarge = fileSize > (5 * 1024 * 1024)

                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isError = true,
                                errorMessage = "Unable to open file"
                            )
                        }
                        return@withContext
                    }

                    val content = try {
                        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                        reader.use { it.readText() }
                    } catch (_: Exception) {
                        // Fallback to system default charset
                        contentResolver.openInputStream(uri)?.use { fallbackStream ->
                            val fallbackReader = BufferedReader(InputStreamReader(fallbackStream, Charset.defaultCharset()))
                            fallbackReader.readText()
                        } ?: ""
                    }

                    val toc = extractTableOfContents(content)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            fileName = displayName,
                            fileSizeBytes = fileSize,
                            isLargeFile = isLarge,
                            showLargeFileBanner = isLarge,
                            content = content,
                            tableOfContents = toc
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MarkdownViewer", "Error reading file", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isError = true,
                        errorMessage = "Error reading file: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissLargeFileBanner() {
        _uiState.value = _uiState.value.copy(showLargeFileBanner = false)
    }

    fun setTextSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.setMarkdownTextSize(size)
        }
    }

    fun increaseTextSize() {
        val newSize = (_uiState.value.textSizeSp + 2f).coerceAtMost(28f)
        setTextSize(newSize)
    }

    fun decreaseTextSize() {
        val newSize = (_uiState.value.textSizeSp - 2f).coerceAtLeast(12f)
        setTextSize(newSize)
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            settingsRepository.setReadingMode(mode)
        }
    }

    fun toggleImmersiveMode() {
        _uiState.value = _uiState.value.copy(immersiveMode = !_uiState.value.immersiveMode)
    }

    fun toggleSearch() {
        val newState = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = newState,
            searchQuery = if (!newState) "" else _uiState.value.searchQuery,
            searchMatches = if (!newState) emptyList() else _uiState.value.searchMatches,
            currentSearchIndex = 0
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            currentSearchIndex = 0
        )
    }

    fun setSearchMatches(matches: List<SearchMatch>) {
        val safeIndex = if (matches.isNotEmpty()) {
            _uiState.value.currentSearchIndex.coerceIn(0, matches.size - 1)
        } else {
            0
        }
        _uiState.value = _uiState.value.copy(
            searchMatches = matches,
            currentSearchIndex = safeIndex
        )
    }

    /**
     * Search matches are computed directly on the rendered text
     * so that character indices match the Spanned text displayed in TextView 1:1.
     */
    fun updateSearchQuery(query: String, renderedText: String) {
        val trimmed = query.trim()
        val matches = if (trimmed.isNotEmpty() && renderedText.isNotEmpty()) {
            val list = mutableListOf<SearchMatch>()
            var idx = renderedText.indexOf(trimmed, 0, ignoreCase = true)
            var counter = 0
            while (idx >= 0) {
                list.add(SearchMatch(matchId = counter, blockIndex = 0, matchIndexInBlock = counter, start = idx, end = idx + trimmed.length))
                counter++
                idx = renderedText.indexOf(trimmed, idx + trimmed.length, ignoreCase = true)
            }
            list
        } else {
            emptyList()
        }

        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchMatches = matches,
            currentSearchIndex = 0
        )
    }

    fun nextMatch() {
        val matches = _uiState.value.searchMatches
        if (matches.isNotEmpty()) {
            val nextIdx = (_uiState.value.currentSearchIndex + 1) % matches.size
            _uiState.value = _uiState.value.copy(currentSearchIndex = nextIdx)
        }
    }

    fun prevMatch() {
        val matches = _uiState.value.searchMatches
        if (matches.isNotEmpty()) {
            val prevIdx = if (_uiState.value.currentSearchIndex - 1 < 0) matches.size - 1 else _uiState.value.currentSearchIndex - 1
            _uiState.value = _uiState.value.copy(currentSearchIndex = prevIdx)
        }
    }

    /**
     * Prepares the actual .md file in the app cache and returns a FileProvider URI
     * for sharing the file attachment (e.g. to WhatsApp, Gmail, Drive).
     */
    suspend fun getShareableFileUri(uriString: String): Uri? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val state = _uiState.value
        try {
            val safeName = if (state.fileName.endsWith(".md", ignoreCase = true)) state.fileName else "${state.fileName}.md"
            val shareDir = File(context.cacheDir, "shared_markdown").apply { mkdirs() }
            val shareFile = File(shareDir, safeName)

            if (state.content.isNotEmpty()) {
                shareFile.writeText(state.content, Charsets.UTF_8)
            } else {
                val uri = uriString.toUri()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    shareFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
        } catch (e: Exception) {
            Log.e("MarkdownViewer", "Error preparing share file", e)
            null
        }
    }

    /**
     * Converts markdown to styled HTML and writes to cache/html_preview/preview.html
     * so it can be previewed in external web browsers (Chrome, Firefox).
     */
    suspend fun generateHtmlPreviewUri(): Uri? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val state = _uiState.value
        try {
            val previewDir = File(context.cacheDir, "html_preview").apply { mkdirs() }
            val previewFile = File(previewDir, "preview.html")
            val html = buildHtmlDocument(state.fileName, state.content)
            previewFile.writeText(html, Charsets.UTF_8)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                previewFile
            )
        } catch (e: Exception) {
            Log.e("MarkdownViewer", "Error generating HTML preview", e)
            null
        }
    }

    /**
     * Converts markdown to styled PDF and writes to cache/shared_pdf/${fileName}.pdf
     * so it can be shared to messaging apps, email, etc.
     */
    suspend fun generatePdfFile(): Uri? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val state = _uiState.value
        try {
            val pdfDir = File(context.cacheDir, "shared_pdf").apply { mkdirs() }
            val baseName = state.fileName.removeSuffix(".md").ifBlank { "Document" }
            val pdfFile = File(pdfDir, "$baseName.pdf")

            createPdfDocument(state.fileName, state.content, pdfFile)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
        } catch (e: Exception) {
            Log.e("MarkdownViewer", "Error generating PDF file", e)
            null
        }
    }

    /**
     * Converts markdown to PDF and writes directly to targetUri selected by user (Save as PDF).
     */
    suspend fun savePdfToUri(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val state = _uiState.value
        try {
            val tempFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.pdf")
            createPdfDocument(state.fileName, state.content, tempFile)

            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                tempFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            tempFile.delete()
            true
        } catch (e: Exception) {
            Log.e("MarkdownViewer", "Error saving PDF to URI", e)
            false
        }
    }

    private fun extractTableOfContents(content: String): List<TableOfContentItem> {
        val items = mutableListOf<TableOfContentItem>()
        val lines = content.lines()
        var blockIndex = 0
        var inCodeBlock = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (inCodeBlock) continue

            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                    val title = trimmed.drop(level).trim()
                    if (title.isNotEmpty()) {
                        items.add(TableOfContentItem(level, title, blockIndex))
                    }
                }
            }
            blockIndex++
        }
        return items
    }

    private fun createPdfDocument(title: String, markdown: String, outputFile: File) {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36
        val contentWidth = pageWidth - (margin * 2)
        val maxContentHeight = pageHeight - (margin * 2) - 20 // leave space for footer

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(18, 24, 38)
        }
        val h1Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(24, 34, 52)
        }
        val h2Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(38, 52, 74)
        }
        val h3Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(50, 65, 88)
        }
        val h4Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(45, 60, 80)
        }
        val h5Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(55, 70, 90)
        }
        val h6Paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(65, 80, 100)
        }
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.rgb(35, 38, 45)
        }
        val quotePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            color = android.graphics.Color.rgb(85, 95, 110)
        }
        val tableHeaderPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(15, 20, 30)
        }
        val tableCellPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9f
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.rgb(35, 38, 45)
        }
        val codePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9f
            typeface = Typeface.MONOSPACE
            color = android.graphics.Color.rgb(30, 35, 45)
        }
        val footerPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 8.5f
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.rgb(140, 150, 160)
            textAlign = Paint.Align.CENTER
        }
        val codeBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(246, 248, 250)
        }
        val tableHeaderBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(240, 243, 246)
        }
        val tableAltRowBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(250, 252, 254)
        }
        val borderPaint = Paint().apply {
            color = android.graphics.Color.rgb(215, 222, 230)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val dividerPaint = Paint().apply {
            color = android.graphics.Color.rgb(225, 230, 236)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val quoteBarPaint = Paint().apply {
            color = android.graphics.Color.rgb(70, 130, 200)
            style = Paint.Style.FILL
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDoc.startPage(pageInfo)
        var canvas = currentPage.canvas
        var currentY = margin.toFloat()

        fun drawFooter() {
            canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 16f, footerPaint)
        }

        fun newPage() {
            drawFooter()
            pdfDoc.finishPage(currentPage)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            currentPage = pdfDoc.startPage(pageInfo)
            canvas = currentPage.canvas
            currentY = margin.toFloat()
        }

        fun formatMarkdownInline(text: String): CharSequence {
            val ssb = android.text.SpannableStringBuilder()
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
                            val startIdx = ssb.length
                            ssb.append(displayText)
                            if (!isImage) {
                                ssb.setSpan(
                                    android.text.style.ForegroundColorSpan(android.graphics.Color.rgb(10, 102, 194)),
                                    startIdx,
                                    ssb.length,
                                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
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
                        val boldText = text.substring(i + 2, end)
                        val startIdx = ssb.length
                        ssb.append(boldText)
                        ssb.setSpan(
                            android.text.style.StyleSpan(Typeface.BOLD),
                            startIdx,
                            ssb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 2
                        continue
                    }
                }

                // Italic: *text* (single asterisk)
                if (text[i] == '*' && (i + 1 == len || text[i + 1] != '*')) {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        val italicText = text.substring(i + 1, end)
                        val startIdx = ssb.length
                        ssb.append(italicText)
                        ssb.setSpan(
                            android.text.style.StyleSpan(Typeface.ITALIC),
                            startIdx,
                            ssb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 1
                        continue
                    }
                }

                // Code: `code`
                if (text[i] == '`') {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        val codeText = text.substring(i + 1, end)
                        val startIdx = ssb.length
                        ssb.append(" $codeText ")
                        ssb.setSpan(
                            android.text.style.TypefaceSpan("monospace"),
                            startIdx,
                            ssb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        ssb.setSpan(
                            android.text.style.BackgroundColorSpan(android.graphics.Color.rgb(240, 242, 245)),
                            startIdx,
                            ssb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 1
                        continue
                    }
                }

                ssb.append(text[i])
                i++
            }
            return ssb
        }

        fun createStaticLayout(source: CharSequence, paint: TextPaint, width: Int): StaticLayout {
            val safeWidth = width.coerceAtLeast(10)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(source, 0, source.length, paint, safeWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.18f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(source, paint, safeWidth, Layout.Alignment.ALIGN_NORMAL, 1.18f, 0f, false)
            }
        }

        fun parseTableCells(line: String): List<String> {
            var trimmed = line.trim()
            if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
            if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length - 1)
            return trimmed.split("|").map { it.trim() }
        }

        val lines = markdown.lines()
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx]
            val trimmed = line.trim()

            // 1. Code block handling: ```
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = if (trimmed.startsWith("```")) "```" else "~~~"
                val codeBuffer = mutableListOf<String>()
                idx++
                while (idx < lines.size) {
                    val codeLine = lines[idx]
                    if (codeLine.trim().startsWith(fence)) {
                        break
                    }
                    codeBuffer.add(codeLine)
                    idx++
                }
                idx++

                // Paginate long code blocks across pages so they never clip
                var lineIdx = 0
                while (lineIdx < codeBuffer.size) {
                    val availableSpace = (maxContentHeight + margin) - currentY - 24f
                    if (availableSpace < 45f) {
                        newPage()
                    }
                    val pageRemainingSpace = (maxContentHeight + margin) - currentY - 24f

                    val chunkLines = mutableListOf<String>()
                    while (lineIdx < codeBuffer.size) {
                        val testLines = chunkLines + codeBuffer[lineIdx]
                        val testLayout = createStaticLayout(testLines.joinToString("\n"), codePaint, contentWidth - 16)
                        if (chunkLines.isNotEmpty() && testLayout.height > pageRemainingSpace) {
                            break
                        }
                        chunkLines.add(codeBuffer[lineIdx])
                        lineIdx++
                    }

                    val chunkText = chunkLines.joinToString("\n")
                    val layout = createStaticLayout(chunkText, codePaint, contentWidth - 16)
                    val blockHeight = layout.height + 16

                    canvas.drawRoundRect(
                        margin.toFloat(),
                        currentY,
                        (margin + contentWidth).toFloat(),
                        currentY + blockHeight,
                        6f, 6f, codeBgPaint
                    )
                    canvas.drawRoundRect(
                        margin.toFloat(),
                        currentY,
                        (margin + contentWidth).toFloat(),
                        currentY + blockHeight,
                        6f, 6f, borderPaint
                    )
                    canvas.save()
                    canvas.translate((margin + 8).toFloat(), currentY + 8)
                    layout.draw(canvas)
                    canvas.restore()

                    currentY += blockHeight + 10f
                    if (lineIdx < codeBuffer.size) {
                        newPage()
                    }
                }
                continue
            }

            // 2. Table handling: | ... |
            if (trimmed.startsWith("|") && idx + 1 < lines.size && lines[idx + 1].trim().contains("---")) {
                val headerCells = parseTableCells(trimmed)
                val rows = mutableListOf<List<String>>()
                idx += 2
                while (idx < lines.size) {
                    val rLine = lines[idx].trim()
                    // Stop if line is empty or does not start with | (e.g. heading, horizontal rule, text)
                    if (!rLine.startsWith("|") || rLine.startsWith("#")) {
                        break
                    }
                    if (!rLine.contains("---")) {
                        val rCells = parseTableCells(rLine)
                        if (rCells.isNotEmpty()) {
                            rows.add(rCells)
                        }
                    }
                    idx++
                }

                val colCount = maxOf(headerCells.size, rows.maxOfOrNull { it.size } ?: 1)
                val paddedHeaders = headerCells + List(maxOf(0, colCount - headerCells.size)) { "" }

                // Proportional column widths based on maximum cell lengths
                val colLengths = (0 until colCount).map { colIdx ->
                    val hLen = paddedHeaders.getOrNull(colIdx)?.length ?: 0
                    val maxCell = rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
                    maxOf(hLen, maxCell, 4)
                }
                val totalLength = colLengths.sum().toFloat()
                val colWidths = colLengths.map { len ->
                    ((len.toFloat() / totalLength) * contentWidth).coerceIn(60f, contentWidth * 0.7f)
                }
                val widthSum = colWidths.sum()
                val normalizedWidths = colWidths.map { (it / widthSum) * contentWidth }
                val cellPadding = 5f

                // Draw Table Header
                val headerLayouts = (0 until colCount).map { c ->
                    val hText = formatMarkdownInline(paddedHeaders[c])
                    val cellW = (normalizedWidths[c] - cellPadding * 2).toInt()
                    createStaticLayout(hText, tableHeaderPaint, cellW)
                }
                val headerRowHeight = (headerLayouts.maxOfOrNull { it.height } ?: 16) + cellPadding * 2

                if (currentY + headerRowHeight + 30 > maxContentHeight + margin) {
                    newPage()
                }

                fun drawHeaderRow() {
                    canvas.drawRect(margin.toFloat(), currentY, (margin + contentWidth).toFloat(), currentY + headerRowHeight, tableHeaderBgPaint)
                    var xOffset = margin.toFloat()
                    for (c in 0 until colCount) {
                        val layout = headerLayouts[c]
                        canvas.save()
                        canvas.translate(xOffset + cellPadding, currentY + cellPadding)
                        layout.draw(canvas)
                        canvas.restore()
                        xOffset += normalizedWidths[c]
                    }
                    currentY += headerRowHeight
                }

                var pageTableStartY = currentY
                drawHeaderRow()

                fun finishTableOnCurrentPage() {
                    // Outer border for current page segment
                    canvas.drawRect(margin.toFloat(), pageTableStartY, (margin + contentWidth).toFloat(), currentY, borderPaint)
                    var colDividerX = margin.toFloat()
                    for (c in 0 until colCount - 1) {
                        colDividerX += normalizedWidths[c]
                        canvas.drawLine(colDividerX, pageTableStartY, colDividerX, currentY, borderPaint)
                    }
                }

                // Draw Data Rows
                for (r in rows.indices) {
                    val rowData = rows[r]
                    val cellLayouts = (0 until colCount).map { c ->
                        val rawText = rowData.getOrElse(c) { "" }
                        val formatted = formatMarkdownInline(rawText)
                        val cellW = (normalizedWidths[c] - cellPadding * 2).toInt()
                        createStaticLayout(formatted, tableCellPaint, cellW)
                    }
                    val rowHeight = (cellLayouts.maxOfOrNull { it.height } ?: 14) + cellPadding * 2

                    if (currentY + rowHeight > maxContentHeight + margin) {
                        // Finish table frame on current page before moving to next page
                        finishTableOnCurrentPage()
                        newPage()
                        pageTableStartY = currentY
                        drawHeaderRow()
                    }

                    val rowTop = currentY
                    if (r % 2 == 1) {
                        canvas.drawRect(margin.toFloat(), rowTop, (margin + contentWidth).toFloat(), rowTop + rowHeight, tableAltRowBgPaint)
                    }

                    var xOffset = margin.toFloat()
                    for (c in 0 until colCount) {
                        val layout = cellLayouts[c]
                        canvas.save()
                        canvas.translate(xOffset + cellPadding, rowTop + cellPadding)
                        layout.draw(canvas)
                        canvas.restore()
                        xOffset += normalizedWidths[c]
                    }

                    // Row bottom line
                    canvas.drawLine(margin.toFloat(), rowTop + rowHeight, (margin + contentWidth).toFloat(), rowTop + rowHeight, dividerPaint)
                    currentY += rowHeight
                }

                // Outer border & vertical column dividers for final page segment
                finishTableOnCurrentPage()

                currentY += 12f
                continue
            }

            // Horizontal rule: ---, ***, ___
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                if (currentY + 12f > maxContentHeight + margin) {
                    newPage()
                }
                currentY += 4f
                canvas.drawLine(margin.toFloat(), currentY, (margin + contentWidth).toFloat(), currentY, dividerPaint)
                currentY += 8f
                idx++
                continue
            }

            // 3. Lists: - item, * item, 1. item, 10. item, with indentation support
            val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
            val numDigits = trimmed.takeWhile { it.isDigit() }
            val isNumbered = numDigits.isNotEmpty() && trimmed.startsWith("$numDigits. ")
            if (isBullet || isNumbered) {
                val indentLevel = (line.takeWhile { it == ' ' || it == '\t' }.length / 2).coerceIn(0, 4)
                val indentPx = indentLevel * 12f
                val bulletPrefix = if (isBullet) "•  " else "$numDigits.  "
                val itemContent = if (isBullet) trimmed.drop(2).trim() else trimmed.substring(numDigits.length + 2).trim()
                val formattedItem = formatMarkdownInline(itemContent)

                val bulletWidth = (bodyPaint.measureText(bulletPrefix) + 4f).toInt().coerceAtLeast(14)
                val safeWidth = (contentWidth - bulletWidth - indentPx.toInt()).coerceAtLeast(50)
                val layout = createStaticLayout(formattedItem, bodyPaint, safeWidth)

                if (currentY + layout.height > maxContentHeight + margin) {
                    newPage()
                }

                canvas.drawText(bulletPrefix, margin.toFloat() + indentPx, currentY + 10f, bodyPaint)
                canvas.save()
                canvas.translate(margin.toFloat() + indentPx + bulletWidth, currentY)
                layout.draw(canvas)
                canvas.restore()
                currentY += layout.height + 4f
                idx++
                continue
            }

            // 4. Blockquotes: > quote
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                val formatted = formatMarkdownInline(quoteText)
                val quoteIndent = 12
                val layout = createStaticLayout(formatted, quotePaint, contentWidth - quoteIndent)

                if (currentY + layout.height > maxContentHeight + margin) {
                    newPage()
                }

                canvas.drawRect(margin.toFloat(), currentY, (margin + 3).toFloat(), currentY + layout.height, quoteBarPaint)
                canvas.save()
                canvas.translate((margin + quoteIndent).toFloat(), currentY)
                layout.draw(canvas)
                canvas.restore()
                currentY += layout.height + 6f
                idx++
                continue
            }

            // 5. Blank line
            if (trimmed.isEmpty()) {
                currentY += 6
                idx++
                continue
            }

            // 6. Headings and Paragraphs
            val isHeading = trimmed.startsWith("#")
            val headingLevel = if (isHeading) trimmed.takeWhile { it == '#' }.length else 0
            val isActualHeading = headingLevel in 1..6 && trimmed.length > headingLevel && trimmed[headingLevel] == ' '

            val (paint, cleanText, extraSpacing) = when {
                isActualHeading -> {
                    val rawText = trimmed.drop(headingLevel).trim()
                    when (headingLevel) {
                        1 -> Triple(h1Paint, rawText, 12)
                        2 -> Triple(h2Paint, rawText, 10)
                        3 -> Triple(h3Paint, rawText, 8)
                        4 -> Triple(h4Paint, rawText, 7)
                        5 -> Triple(h5Paint, rawText, 6)
                        else -> Triple(h6Paint, rawText, 6)
                    }
                }
                else -> Triple(bodyPaint, trimmed, 6)
            }

            val formattedText = formatMarkdownInline(cleanText)
            val layout = createStaticLayout(formattedText, paint, contentWidth)

            if (currentY + layout.height + (if (isActualHeading && headingLevel <= 2) 8 else 0) > maxContentHeight + margin) {
                newPage()
            }

            canvas.save()
            canvas.translate(margin.toFloat(), currentY)
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height

            if (isActualHeading && headingLevel <= 2) {
                currentY += 4f
                canvas.drawLine(margin.toFloat(), currentY, (margin + contentWidth).toFloat(), currentY, dividerPaint)
                currentY += 6f
            }

            currentY += extraSpacing
            idx++
        }

        drawFooter()
        pdfDoc.finishPage(currentPage)
        outputFile.outputStream().use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }

    private fun buildHtmlDocument(title: String, markdown: String): String {
        val bodyHtml = convertMarkdownToHtml(markdown)
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${escapeHtml(title)}</title>
              <style>
                :root {
                  --bg: #ffffff;
                  --text: #1f2328;
                  --code-bg: #f6f8fa;
                  --border: #d0d7de;
                  --link: #0969da;
                  --table-alt: #f6f8fa;
                }
                @media (prefers-color-scheme: dark) {
                  :root {
                    --bg: #0d1117;
                    --text: #e6edf3;
                    --code-bg: #161b22;
                    --border: #30363d;
                    --link: #2f81f7;
                    --table-alt: #161b22;
                  }
                }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif;
                  line-height: 1.6;
                  color: var(--text);
                  background-color: var(--bg);
                  max-width: 860px;
                  margin: 0 auto;
                  padding: 24px 16px;
                  word-wrap: break-word;
                }
                h1, h2, h3, h4, h5, h6 {
                  margin-top: 24px;
                  margin-bottom: 16px;
                  font-weight: 600;
                  line-height: 1.25;
                }
                h1 { font-size: 2em; border-bottom: 1px solid var(--border); padding-bottom: .3em; }
                h2 { font-size: 1.5em; border-bottom: 1px solid var(--border); padding-bottom: .3em; }
                h3 { font-size: 1.25em; }
                p, ul, ol { margin-top: 0; margin-bottom: 16px; }
                code {
                  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, monospace;
                  font-size: 85%;
                  background: var(--code-bg);
                  padding: .2em .4em;
                  border-radius: 4px;
                  border: 1px solid var(--border);
                }
                pre {
                  background: var(--code-bg);
                  padding: 16px;
                  border-radius: 8px;
                  overflow-x: auto;
                  border: 1px solid var(--border);
                }
                pre code {
                  background: transparent;
                  padding: 0;
                  border: none;
                }
                blockquote {
                  margin: 0 0 16px;
                  padding: 0 1em;
                  color: #656d76;
                  border-left: .25em solid var(--border);
                }
                table {
                  border-collapse: collapse;
                  width: 100%;
                  margin-bottom: 16px;
                  display: block;
                  overflow-x: auto;
                }
                table th, table td {
                  border: 1px solid var(--border);
                  padding: 8px 14px;
                  text-align: left;
                }
                table th {
                  background: var(--code-bg);
                  font-weight: 600;
                }
                table tr:nth-child(2n) {
                  background: var(--table-alt);
                }
                hr {
                  height: .25em;
                  padding: 0;
                  margin: 24px 0;
                  background-color: var(--border);
                  border: 0;
                }
                a { color: var(--link); text-decoration: none; }
                a:hover { text-decoration: underline; }
                img { max-width: 100%; height: auto; }
              </style>
            </head>
            <body>
              $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun convertMarkdownToHtml(markdown: String): String {
        val lines = markdown.lines()
        val sb = StringBuilder()
        var inCodeBlock = false
        var codeBlockLang = ""
        val codeBlockLines = mutableListOf<String>()
        var inTable = false
        val tableLines = mutableListOf<String>()

        fun flushTable() {
            if (!inTable || tableLines.isEmpty()) return
            sb.append("<table>\n")
            var isHeader = true
            for (tLine in tableLines) {
                if (tLine.contains("---")) {
                    isHeader = false
                    continue
                }
                val cells = tLine.split("|")
                    .map { it.trim() }
                    .filterIndexed { index, cell ->
                        // Exclude first/last empty splits from | a | b | format
                        !(cell.isEmpty() && (index == 0 || index == tLine.split("|").lastIndex))
                    }
                if (cells.isNotEmpty()) {
                    sb.append("  <tr>\n")
                    val tag = if (isHeader) "th" else "td"
                    for (c in cells) {
                        sb.append("    <$tag>${formatInlineHtml(c)}</$tag>\n")
                    }
                    sb.append("  </tr>\n")
                }
                isHeader = false
            }
            sb.append("</table>\n")
            tableLines.clear()
            inTable = false
        }

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append("<pre><code>")
                    sb.append(escapeHtml(codeBlockLines.joinToString("\n")))
                    sb.append("</code></pre>\n")
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    flushTable()
                    inCodeBlock = true
                    codeBlockLang = trimmed.removePrefix("```").trim()
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                continue
            }

            if (trimmed.startsWith("|")) {
                inTable = true
                tableLines.add(trimmed)
                continue
            } else {
                flushTable()
            }

            when {
                trimmed.startsWith("# ") -> sb.append("<h1>${formatInlineHtml(trimmed.removePrefix("# ").trim())}</h1>\n")
                trimmed.startsWith("## ") -> sb.append("<h2>${formatInlineHtml(trimmed.removePrefix("## ").trim())}</h2>\n")
                trimmed.startsWith("### ") -> sb.append("<h3>${formatInlineHtml(trimmed.removePrefix("### ").trim())}</h3>\n")
                trimmed.startsWith("#### ") -> sb.append("<h4>${formatInlineHtml(trimmed.removePrefix("#### ").trim())}</h4>\n")
                trimmed.startsWith("##### ") -> sb.append("<h5>${formatInlineHtml(trimmed.removePrefix("##### ").trim())}</h5>\n")
                trimmed.startsWith("###### ") -> sb.append("<h6>${formatInlineHtml(trimmed.removePrefix("###### ").trim())}</h6>\n")
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> sb.append("<hr>\n")
                trimmed.startsWith("> ") -> sb.append("<blockquote><p>${formatInlineHtml(trimmed.removePrefix("> ").trim())}</p></blockquote>\n")
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> sb.append("<ul><li>${formatInlineHtml(trimmed.substring(2).trim())}</li></ul>\n")
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val text = trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), "")
                    sb.append("<ol><li>${formatInlineHtml(text)}</li></ol>\n")
                }
                trimmed.isEmpty() -> {}
                else -> sb.append("<p>${formatInlineHtml(trimmed)}</p>\n")
            }
        }

        if (inCodeBlock) {
            sb.append("<pre><code>${escapeHtml(codeBlockLines.joinToString("\n"))}</code></pre>\n")
        }
        flushTable()

        return sb.toString()
    }

    private fun formatInlineHtml(text: String): String {
        var res = escapeHtml(text)
        // Bold: **text**
        res = res.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        // Italic: *text*
        res = res.replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        // Inline code: `code`
        res = res.replace(Regex("`(.*?)`"), "<code>$1</code>")
        // Links: [text](url)
        res = res.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\" target=\"_blank\">$1</a>")
        return res
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

