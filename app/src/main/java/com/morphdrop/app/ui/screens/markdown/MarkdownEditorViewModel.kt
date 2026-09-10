package com.morphdrop.app.ui.screens.markdown

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import javax.inject.Inject

enum class EditorViewMode {
    EDIT_ONLY,
    SPLIT_VIEW,
    PREVIEW_ONLY
}

data class EditorSnapshot(
    val text: String,
    val selection: TextRange
)

data class MarkdownEditorState(
    val fileName: String = "Untitled.md",
    val fileUri: Uri? = null,
    val isNewDocument: Boolean = true,
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val lastSavedMessage: String = "",
    val errorMessage: String? = null,
    val viewMode: EditorViewMode = EditorViewMode.EDIT_ONLY,
    val isLargeFile: Boolean = false,
    val showLargeFileWarning: Boolean = false,
    val showLineNumbers: Boolean = true,
    val isWordWrapEnabled: Boolean = false,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val lineCount: Int = 1,
    val cursorLine: Int = 1,
    val cursorCol: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showUnsavedDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showStatsDialog: Boolean = false,
    val showTableDialog: Boolean = false,
    val showLinkDialog: Boolean = false,
    val showImageDialog: Boolean = false
)

@HiltViewModel
class MarkdownEditorViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MarkdownEditorState())
    val uiState: StateFlow<MarkdownEditorState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private val maxHistorySize = 100

    private var lastSnapshotTime = 0L
    private val snapshotDebounceMs = 600L

    init {
        // Periodic auto-save every 60 seconds
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                val state = _uiState.value
                if (state.hasUnsavedChanges && state.fileUri != null && !state.isSaving && !state.isLoading) {
                    saveDocument()
                }
            }
        }
    }

    fun initDocument(uriString: String?, isNew: Boolean) {
        if (!uriString.isNullOrBlank()) {
            loadFromUri(Uri.parse(uriString))
        } else if (isNew || _uiState.value.textFieldValue.text.isEmpty()) {
            createNewDocument()
        }
    }

    fun createNewDocument(templateTitle: String = "Untitled.md") {
        val initialText = """
            # Untitled Document

            Start writing your markdown here...
        """.trimIndent() + "\n"

        val initialTfv = TextFieldValue(initialText, TextRange(initialText.length))
        undoStack.clear()
        redoStack.clear()

        _uiState.update {
            it.copy(
                fileName = templateTitle,
                fileUri = null,
                isNewDocument = true,
                textFieldValue = initialTfv,
                hasUnsavedChanges = false,
                isLoading = false,
                isLargeFile = false,
                showLargeFileWarning = false,
                lastSavedMessage = "",
                canUndo = false,
                canRedo = false
            )
        }
        updateTextStatistics(initialTfv)
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val context = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver

                    // Try to take persistable URI permission if possible
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                        // Ignore if not a persistable URI grant
                    }

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
                            Log.e("MarkdownEditor", "Error querying display name", e)
                        }
                    }
                    val isMarkdownExt = displayName.endsWith(".md", ignoreCase = true) ||
                            displayName.endsWith(".markdown", ignoreCase = true) ||
                            displayName.endsWith(".mdown", ignoreCase = true) ||
                            displayName.endsWith(".mkd", ignoreCase = true) ||
                            !displayName.contains(".")

                    if (!isMarkdownExt) {
                        throw IllegalArgumentException("Cannot open unsupported file ($displayName). Only Markdown (.md) files are supported.")
                    }

                    if (!displayName.endsWith(".md", ignoreCase = true) && !displayName.contains(".")) {
                        displayName = "$displayName.md"
                    }

                    var fileSize = 0L
                    try {
                        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            fileSize = pfd.statSize
                        }
                    } catch (_: Exception) {}

                    // Binary content detector: check first 1KB for binary signatures or null bytes
                    contentResolver.openInputStream(uri)?.use { checkStream ->
                        val buffer = ByteArray(1024)
                        val bytesRead = checkStream.read(buffer)
                        if (bytesRead > 0) {
                            val headerStr = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)
                            val isBinary = headerStr.startsWith("%PDF") ||
                                    headerStr.startsWith("PK\u0003\u0004") ||
                                    headerStr.startsWith("\u0089PNG") ||
                                    buffer.take(bytesRead).any { it == 0.toByte() }

                            if (isBinary) {
                                throw IllegalArgumentException("The selected file ($displayName) is a binary file, not a valid Markdown text document.")
                            }
                        }
                    }

                    val textContent = contentResolver.openInputStream(uri)?.use { stream ->
                        try {
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        } catch (_: Exception) {
                            contentResolver.openInputStream(uri)?.use { fallbackStream ->
                                BufferedReader(InputStreamReader(fallbackStream, Charset.defaultCharset())).readText()
                            } ?: ""
                        }
                    } ?: throw IllegalStateException("Unable to open file stream")

                    val lineCount = textContent.count { it == '\n' } + 1
                    val isLarge = fileSize > (1024 * 1024) || lineCount > 5000

                    withContext(Dispatchers.Main) {
                        val initialTfv = TextFieldValue(textContent, TextRange(0))
                        undoStack.clear()
                        redoStack.clear()

                        _uiState.update {
                            it.copy(
                                fileName = displayName,
                                fileUri = uri,
                                isNewDocument = false,
                                textFieldValue = initialTfv,
                                hasUnsavedChanges = false,
                                isLoading = false,
                                isLargeFile = isLarge,
                                showLargeFileWarning = isLarge,
                                lastSavedMessage = "Opened $displayName",
                                canUndo = false,
                                canRedo = false
                            )
                        }
                        updateTextStatistics(initialTfv)
                    }
                }
            } catch (e: Exception) {
                Log.e("MarkdownEditor", "Failed to load document", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to open file: ${e.localizedMessage ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    fun onTextChanged(newValue: TextFieldValue) {
        val oldValue = _uiState.value.textFieldValue

        // Record history snapshot if content changed significantly or debounce expired
        if (oldValue.text != newValue.text) {
            val now = System.currentTimeMillis()
            val textDiff = kotlin.math.abs(oldValue.text.length - newValue.text.length)
            if (now - lastSnapshotTime > snapshotDebounceMs || textDiff > 5) {
                pushUndoSnapshot(EditorSnapshot(oldValue.text, oldValue.selection))
                lastSnapshotTime = now
            }
        }

        _uiState.update {
            it.copy(
                textFieldValue = newValue,
                hasUnsavedChanges = if (oldValue.text != newValue.text) true else it.hasUnsavedChanges
            )
        }
        updateTextStatistics(newValue)
    }

    fun setFileName(name: String) {
        var cleanName = name.trim()
        if (cleanName.isEmpty()) cleanName = "Untitled.md"
        if (!cleanName.endsWith(".md", ignoreCase = true)) {
            cleanName = "$cleanName.md"
        }
        _uiState.update { it.copy(fileName = cleanName, hasUnsavedChanges = true) }
    }

    fun setViewMode(mode: EditorViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun toggleLineNumbers() {
        _uiState.update { it.copy(showLineNumbers = !it.showLineNumbers) }
    }

    fun toggleWordWrap() {
        _uiState.update { it.copy(isWordWrapEnabled = !it.isWordWrapEnabled) }
    }

    fun setRenameDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showRenameDialog = visible) }
    }

    fun dismissLargeFileWarning() {
        _uiState.update { it.copy(showLargeFileWarning = false) }
    }

    fun setUnsavedDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showUnsavedDialog = visible) }
    }

    fun setStatsDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showStatsDialog = visible) }
    }

    fun setTableDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showTableDialog = visible) }
    }

    fun setLinkDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showLinkDialog = visible) }
    }

    fun setImageDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showImageDialog = visible) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- Undo / Redo Operations ---

    private fun pushUndoSnapshot(snapshot: EditorSnapshot) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
        redoStack.clear()
        _uiState.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentTfv = _uiState.value.textFieldValue
        val previousSnapshot = undoStack.removeLast()

        if (redoStack.size >= maxHistorySize) {
            redoStack.removeFirst()
        }
        redoStack.addLast(EditorSnapshot(currentTfv.text, currentTfv.selection))

        val newTfv = TextFieldValue(previousSnapshot.text, previousSnapshot.selection)
        _uiState.update {
            it.copy(
                textFieldValue = newTfv,
                hasUnsavedChanges = true,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
        updateTextStatistics(newTfv)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentTfv = _uiState.value.textFieldValue
        val nextSnapshot = redoStack.removeLast()

        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(EditorSnapshot(currentTfv.text, currentTfv.selection))

        val newTfv = TextFieldValue(nextSnapshot.text, nextSnapshot.selection)
        _uiState.update {
            it.copy(
                textFieldValue = newTfv,
                hasUnsavedChanges = true,
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
        updateTextStatistics(newTfv)
    }

    // --- Formatting and Insertion Tools ---

    fun applyInlineFormat(prefix: String, suffix: String, defaultPlaceholder: String = "text") {
        val current = _uiState.value.textFieldValue
        pushUndoSnapshot(EditorSnapshot(current.text, current.selection))

        val text = current.text
        val selection = current.selection
        val isCollapsed = selection.collapsed

        val selectedText = if (!isCollapsed) {
            text.substring(selection.min, selection.max)
        } else {
            defaultPlaceholder
        }

        // Toggle unwrapping if already wrapped
        val start = selection.min
        val end = selection.max
        val hasPrefixBefore = start >= prefix.length && text.substring(start - prefix.length, start) == prefix
        val hasSuffixAfter = end + suffix.length <= text.length && text.substring(end, end + suffix.length) == suffix

        val newTfv = if (hasPrefixBefore && hasSuffixAfter) {
            // Unwrap
            val newText = text.substring(0, start - prefix.length) + selectedText + text.substring(end + suffix.length)
            TextFieldValue(
                text = newText,
                selection = TextRange(start - prefix.length, start - prefix.length + selectedText.length)
            )
        } else {
            // Wrap
            val replacement = "$prefix$selectedText$suffix"
            val newText = text.substring(0, start) + replacement + text.substring(end)
            val cursorStart = if (isCollapsed) start + prefix.length else start
            val cursorEnd = if (isCollapsed) cursorStart + selectedText.length else start + replacement.length
            TextFieldValue(
                text = newText,
                selection = TextRange(cursorStart, cursorEnd)
            )
        }

        _uiState.update { it.copy(textFieldValue = newTfv, hasUnsavedChanges = true) }
        updateTextStatistics(newTfv)
    }

    fun applyLinePrefix(prefix: String) {
        val current = _uiState.value.textFieldValue
        pushUndoSnapshot(EditorSnapshot(current.text, current.selection))

        val text = current.text
        val selection = current.selection

        // Find line start and line end
        val lineStart = text.lastIndexOf('\n', selection.min - 1).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', selection.max).let { if (it == -1) text.length else it }

        val lineText = text.substring(lineStart, lineEnd)

        // Toggle prefix if line already starts with it
        val newText = if (lineText.startsWith(prefix)) {
            text.substring(0, lineStart) + lineText.removePrefix(prefix) + text.substring(lineEnd)
        } else {
            // Cycle through headings if prefix is #
            if (prefix.trim() == "#" && lineText.startsWith("### ")) {
                text.substring(0, lineStart) + lineText.removePrefix("### ") + text.substring(lineEnd)
            } else if (prefix.trim() == "#" && lineText.startsWith("## ")) {
                text.substring(0, lineStart) + "### " + lineText.removePrefix("## ") + text.substring(lineEnd)
            } else if (prefix.trim() == "#" && lineText.startsWith("# ")) {
                text.substring(0, lineStart) + "## " + lineText.removePrefix("# ") + text.substring(lineEnd)
            } else {
                text.substring(0, lineStart) + prefix + lineText + text.substring(lineEnd)
            }
        }

        val cursorOffset = if (lineText.startsWith(prefix)) -prefix.length else prefix.length
        val newCursor = (selection.min + cursorOffset).coerceIn(0, newText.length)
        val newTfv = TextFieldValue(newText, TextRange(newCursor))

        _uiState.update { it.copy(textFieldValue = newTfv, hasUnsavedChanges = true) }
        updateTextStatistics(newTfv)
    }

    fun insertText(insertString: String, selectPlaceholder: Boolean = false, placeholderLength: Int = 0) {
        val current = _uiState.value.textFieldValue
        pushUndoSnapshot(EditorSnapshot(current.text, current.selection))

        val text = current.text
        val selection = current.selection
        val newText = text.substring(0, selection.min) + insertString + text.substring(selection.max)

        val newSelection = if (selectPlaceholder && placeholderLength > 0) {
            TextRange(selection.min, selection.min + placeholderLength)
        } else {
            val nextCursor = selection.min + insertString.length
            TextRange(nextCursor)
        }

        val newTfv = TextFieldValue(newText, newSelection)
        _uiState.update { it.copy(textFieldValue = newTfv, hasUnsavedChanges = true) }
        updateTextStatistics(newTfv)
    }

    fun insertTable(rows: Int = 3, cols: Int = 3) {
        val validRows = rows.coerceIn(1, 20)
        val validCols = cols.coerceIn(1, 10)

        val sb = java.lang.StringBuilder("\n")
        // Header row
        sb.append("|")
        for (c in 1..validCols) {
            sb.append(" Header $c |")
        }
        sb.append("\n|")
        // Separator row
        for (c in 1..validCols) {
            sb.append(" --- |")
        }
        sb.append("\n")
        // Data rows
        for (r in 1..validRows) {
            sb.append("|")
            for (c in 1..validCols) {
                sb.append(" Cell $r,$c |")
            }
            sb.append("\n")
        }
        sb.append("\n")

        insertText(sb.toString())
    }

    fun insertLink(title: String, url: String) {
        val cleanTitle = if (title.isBlank()) "link" else title
        val cleanUrl = if (url.isBlank()) "https://" else url
        insertText("[$cleanTitle]($cleanUrl)")
    }

    fun insertImage(altText: String, url: String) {
        val cleanAlt = if (altText.isBlank()) "image description" else altText
        val cleanUrl = if (url.isBlank()) "https://" else url
        insertText("![$cleanAlt]($cleanUrl)")
    }

    fun insertHorizontalRule() {
        insertText("\n\n---\n\n")
    }

    fun insertTabSpaces() {
        insertText("    ")
    }

    /**
     * Handles Smart Enter key behavior:
     * - Continues unordered list `- ` or `* `
     * - Continues ordered list `1. `, `2. `
     * - Continues task list `- [ ] `
     * - If bullet line is empty, removes prefix to cleanly exit list
     */
    fun handleSmartEnter(): Boolean {
        val current = _uiState.value.textFieldValue
        val text = current.text
        val selection = current.selection
        if (!selection.collapsed) return false

        val cursor = selection.start
        val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it == -1) 0 else it + 1 }
        val currentLine = text.substring(lineStart, cursor)

        // Patterns to match
        val taskPattern = Regex("""^(\s*-\s*\[[ xX]?\]\s+)""")
        val unorderedPattern = Regex("""^(\s*[-*+]\s+)""")
        val orderedPattern = Regex("""^(\s*(\d+)\.\s+)""")

        val taskMatch = taskPattern.find(currentLine)
        val unorderedMatch = unorderedPattern.find(currentLine)
        val orderedMatch = orderedPattern.find(currentLine)

        when {
            taskMatch != null -> {
                val prefix = taskMatch.value
                val contentAfter = currentLine.removePrefix(prefix).trim()
                if (contentAfter.isEmpty()) {
                    // Empty list item: exit list
                    val newText = text.substring(0, lineStart) + text.substring(cursor)
                    val newTfv = TextFieldValue(newText, TextRange(lineStart))
                    _uiState.update { it.copy(textFieldValue = newTfv, hasUnsavedChanges = true) }
                    updateTextStatistics(newTfv)
                    return true
                } else {
                    // Continue task item
                    insertText("\n- [ ] ")
                    return true
                }
            }
            unorderedMatch != null -> {
                val prefix = unorderedMatch.value
                val contentAfter = currentLine.removePrefix(prefix).trim()
                if (contentAfter.isEmpty()) {
                    // Empty list item: exit list
                    val newText = text.substring(0, lineStart) + text.substring(cursor)
                    val newTfv = TextFieldValue(newText, TextRange(lineStart))
                    _uiState.update { it.copy(textFieldValue = newTfv, hasUnsavedChanges = true) }
                    updateTextStatistics(newTfv)
                    return true
                } else {
                    // Continue unordered item
                    insertText("\n$prefix")
                    return true
                }
            }
            orderedMatch != null -> {
                val prefix = orderedMatch.value
                val numStr = orderedMatch.groupValues[2]
                val contentAfter = currentLine.removePrefix(prefix).trim()
                if (contentAfter.isEmpty()) {
                    // Empty list item: exit list
                    val newText = text.substring(0, lineStart) + text.substring(cursor)
                    val newTfv = TextFieldValue(newText, TextRange(lineStart))
                    _uiState.update { it.copy(textFieldValue = newTfv, hasUnsavedChanges = true) }
                    updateTextStatistics(newTfv)
                    return true
                } else {
                    val nextNum = (numStr.toIntOrNull() ?: 1) + 1
                    val indent = orderedMatch.groupValues[1].takeWhile { it.isWhitespace() }
                    insertText("\n$indent$nextNum. ")
                    return true
                }
            }
        }
        return false
    }

    // --- File Saving ---

    fun saveDocument(onComplete: (Boolean) -> Unit = {}) {
        val state = _uiState.value
        val uri = state.fileUri

        if (uri == null) {
            // Need "Save As" if no URI exists yet
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val context = getApplication<Application>()

            try {
                withContext(Dispatchers.IO) {
                    val contentBytes = state.textFieldValue.text.toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                        outputStream.write(contentBytes)
                        outputStream.flush()
                    } ?: throw IllegalStateException("Failed to open output stream for $uri")
                }

                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            lastSavedMessage = "Saved at $timestamp"
                        )
                    }
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e("MarkdownEditor", "Failed to save file", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Save failed: ${e.localizedMessage ?: "Unknown error"}"
                        )
                    }
                    onComplete(false)
                }
            }
        }
    }

    fun saveDocumentAs(targetUri: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val context = getApplication<Application>()

            try {
                withContext(Dispatchers.IO) {
                    // Request persistable permission
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            targetUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val contentBytes = _uiState.value.textFieldValue.text.toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
                        outputStream.write(contentBytes)
                        outputStream.flush()
                    } ?: throw IllegalStateException("Failed to open output stream")

                    var targetName = targetUri.lastPathSegment?.substringAfterLast("/") ?: "Document.md"
                    if (targetUri.scheme == "content") {
                        try {
                            context.contentResolver.query(targetUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex != -1) {
                                        val name = cursor.getString(nameIndex)
                                        if (!name.isNullOrBlank()) targetName = name
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date())

                        _uiState.update {
                            it.copy(
                                fileName = targetName,
                                fileUri = targetUri,
                                isNewDocument = false,
                                isSaving = false,
                                hasUnsavedChanges = false,
                                lastSavedMessage = "Saved to $targetName at $timestamp"
                            )
                        }
                        onComplete(true)
                    }
                }
            } catch (e: Exception) {
                Log.e("MarkdownEditor", "Failed to save document as", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Save As failed: ${e.localizedMessage ?: "Unknown error"}"
                        )
                    }
                    onComplete(false)
                }
            }
        }
    }

    private fun updateTextStatistics(tfv: TextFieldValue) {
        val text = tfv.text
        val cursor = tfv.selection.min.coerceIn(0, text.length)

        val words = if (text.isBlank()) 0 else text.trim().split(Regex("""\s+""")).size
        val chars = text.length

        var lines = 1
        var curLine = 1
        var curCol = 1
        var lineStartPos = 0

        for (i in 0 until text.length) {
            if (i == cursor) {
                curLine = lines
                curCol = cursor - lineStartPos + 1
            }
            if (text[i] == '\n') {
                lines++
                lineStartPos = i + 1
            }
        }
        if (cursor == text.length) {
            curLine = lines
            curCol = cursor - lineStartPos + 1
        }

        _uiState.update {
            it.copy(
                wordCount = words,
                charCount = chars,
                lineCount = lines,
                cursorLine = curLine,
                cursorCol = curCol
            )
        }
    }
}
