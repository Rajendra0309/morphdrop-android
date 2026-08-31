package com.morphdrop.app.ui.screens.pdf

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import com.morphdrop.app.data.local.dao.PdfAnnotationDao
import com.morphdrop.app.domain.model.PdfAnnotation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.morphdrop.app.data.local.entity.PdfAnnotationEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.pdf.PdfPrintAdapter
import com.morphdrop.app.data.local.entity.BookmarkEntity
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.SearchMatch
import com.morphdrop.app.domain.repository.BookmarkRepository
import com.morphdrop.app.domain.repository.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import com.morphdrop.app.data.pdf.PdfTextExtractor
import com.morphdrop.app.data.pdf.PdfWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

data class VisiblePageState(
    val boundsInWindow: androidx.compose.ui.geometry.Rect,
    val normalizedViewport: android.graphics.RectF,
    val highResBitmap: android.graphics.Bitmap?,
    val renderedViewport: android.graphics.RectF?,
    val renderedScale: Float?,
    val scale: Float
)

enum class AnnotationTool {
    DRAW, HIGHLIGHT, ERASE
}

data class PdfViewerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 1,
    val isPasswordProtected: Boolean = false,
    val fileName: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val tocList: List<com.morphdrop.app.data.pdf.PdfTocItem> = emptyList(),
    val currentSearchIndex: Int = -1,
    val isSearching: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.DEFAULT,
    val sepiaIntensity: Float = 0.5f,
    val textureIntensity: Float = 0.5f,
    val isImmersiveMode: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isBookmarked: Boolean = false,
    val isAnnotationMode: Boolean = false,
    val annotationTool: AnnotationTool = AnnotationTool.DRAW,
    val brushColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Red,
    val brushSize: Float = 3f,
    val showAnnotations: Boolean = true
)

sealed interface PdfViewerEvent {
    data class ShowSnackbar(val message: String) : PdfViewerEvent
    data class ScrollToPage(val pageIndex: Int) : PdfViewerEvent
    data class ThumbnailGenerated(val pageIndex: Int) : PdfViewerEvent
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val annotationDao: PdfAnnotationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val pdfUiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfViewerEvent>()
    val events: SharedFlow<PdfViewerEvent> = _events.asSharedFlow()

    private val _visiblePages = MutableStateFlow<Map<Int, VisiblePageState>>(emptyMap())
    val visiblePages = _visiblePages.asStateFlow()

    private val _clearSelectionEvent = MutableSharedFlow<Unit>()
    val clearSelectionEvent = _clearSelectionEvent.asSharedFlow()

    private val _dbAnnotations = MutableStateFlow<List<PdfAnnotation>>(emptyList())
    private val _unsavedAnnotations = MutableStateFlow<List<PdfAnnotation>>(emptyList())

    val annotations: StateFlow<List<PdfAnnotation>> = combine(_dbAnnotations, _unsavedAnnotations) { db, unsaved ->
        db + unsaved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    sealed class AnnotationAction {
        data class Add(val annotation: PdfAnnotation) : AnnotationAction()
        data class Remove(val annotation: PdfAnnotation) : AnnotationAction()
    }

    private val _undoStack = MutableStateFlow<List<AnnotationAction>>(emptyList())
    private val _redoStack = MutableStateFlow<List<AnnotationAction>>(emptyList())
    val canUndo = _undoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val canRedo = _redoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges = _hasUnsavedChanges.asStateFlow()

    private val gson = Gson()

    fun showToast(message: String) {
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.ShowSnackbar(message))
        }
    }

    private val highResJobs = mutableMapOf<Int, Job>()

    fun updateVisiblePage(
        pageIndex: Int,
        boundsInWindow: androidx.compose.ui.geometry.Rect,
        normalizedViewport: android.graphics.RectF,
        scale: Float
    ) {
        val current = _visiblePages.value[pageIndex]
        val needsNewRender = current == null || current.scale != scale || current.normalizedViewport != normalizedViewport

        val newState = VisiblePageState(
            boundsInWindow = boundsInWindow,
            normalizedViewport = normalizedViewport,
            highResBitmap = current?.highResBitmap,
            renderedViewport = current?.renderedViewport,
            renderedScale = current?.renderedScale,
            scale = scale
        )

        _visiblePages.update { it + (pageIndex to newState) }

        if (needsNewRender) {
            highResJobs[pageIndex]?.cancel()
            highResJobs[pageIndex] = viewModelScope.launch {
                delay(150) // Debounce rapid scrolling/zooming
                val bitmap = renderHighRes(pageIndex, scale, normalizedViewport)
                if (bitmap != null && _visiblePages.value[pageIndex]?.scale == scale) {
                    _visiblePages.update {
                        it + (pageIndex to it[pageIndex]!!.copy(
                            highResBitmap = bitmap,
                            renderedViewport = normalizedViewport,
                            renderedScale = scale
                        ))
                    }
                }
            }
        }
    }

    fun removeVisiblePage(pageIndex: Int) {
        highResJobs[pageIndex]?.cancel()
        highResJobs.remove(pageIndex)
        _visiblePages.update { it - pageIndex }
    }

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    // Text Extraction
    private val textExtractor = PdfTextExtractor(application)
    private val pageDataCache = android.util.LruCache<Int, com.morphdrop.app.data.pdf.PdfPageData>(10)

    fun getPageWords(pageIndex: Int): List<com.morphdrop.app.data.pdf.PdfWord> {
        return pageDataCache.get(pageIndex)?.words ?: emptyList()
    }

    fun getPageLinks(pageIndex: Int): List<com.morphdrop.app.data.pdf.PdfLink> {
        return pageDataCache.get(pageIndex)?.links ?: emptyList()
    }

    fun getPageText(pageIndex: Int): String {
        return getPageWords(pageIndex).joinToString(" ") { it.text }
    }

    suspend fun loadPageData(pageIndex: Int) {
        if (pageDataCache.get(pageIndex) != null) return
        val uri = currentUri ?: return
        val targetUri = if (decryptedFile != null) Uri.fromFile(decryptedFile) else uri
        try {
            val previewBitmap = renderPreview(pageIndex)
            val data = textExtractor.extractDataFromPage(targetUri, pageIndex, "", previewBitmap)
            pageDataCache.put(pageIndex, data)
        } catch (e: Exception) {
            Log.e("PdfViewerViewModel", "Failed to extract data for page $pageIndex", e)
        }
    }
    private var decryptedFile: File? = null
    private var currentUri: Uri? = null

    private var bookmarkJob: Job? = null

    // Cache to hold roughly 5-7 large page bitmaps (increase size to 1/5 of max memory)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 5 // 1/5th of max memory
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val thumbnailCacheSize = maxMemory / 8 // 1/8th of max memory for thumbnails
    private val thumbnailCache = object : LruCache<Int, Bitmap>(thumbnailCacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val _thumbnailsReady = MutableStateFlow<Set<Int>>(emptySet())
    val thumbnailsReady: StateFlow<Set<Int>> = _thumbnailsReady.asStateFlow()

    private var pregenerateThumbnailsJob: Job? = null

    init {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(application)
        }

        viewModelScope.launch {
            settingsRepository.readingMode.collect { mode ->
                _uiState.update { it.copy(readingMode = mode) }
            }
        }

        viewModelScope.launch {
            settingsRepository.sepiaIntensity.collect { intensity ->
                _uiState.update { it.copy(sepiaIntensity = intensity) }
            }
        }
    }

    fun loadPdf(uri: Uri, password: String? = null) {
        currentUri = uri
        loadBookmarks(uri)
        loadAnnotations(uri)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isPasswordProtected = false) }
            try {
                withContext(Dispatchers.IO) {
                    if (password != null) {
                        decryptAndLoad(uri, password)
                    } else {
                        val pfd = application.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            fileDescriptor = pfd

                            // Check encryption
                            val isEncrypted = application.contentResolver.openInputStream(uri)?.use { stream ->
                                try {
                                    val doc = PDDocument.load(stream)
                                    val encrypted = doc.isEncrypted
                                    doc.close()
                                    encrypted
                                } catch (e: Exception) {
                                    false
                                }
                            } ?: false

                            if (isEncrypted) {
                                _uiState.update { it.copy(isLoading = false, isPasswordProtected = true) }
                            } else {
                                pdfRenderer = PdfRenderer(pfd)
                                val totalPages = pdfRenderer?.pageCount ?: 0
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        totalPages = totalPages,
                                        fileName = getFileName(uri),
                                        isPasswordProtected = false
                                    )
                                }
                                startThumbnailGeneration(totalPages)
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        val tempFile = File(application.cacheDir, "toc_temp_${System.currentTimeMillis()}.pdf")
                                        application.contentResolver.openInputStream(uri)?.use { input ->
                                            tempFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        val toc = com.morphdrop.app.data.pdf.PdfTocExtractor.extractToc(tempFile)
                                        tempFile.delete()
                                        _uiState.update { it.copy(tocList = toc) }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PdfViewerViewModel", "Failed to extract TOC", e)
                                    }
                                }
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = "Unable to open file") }
                        }
                    }
                }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(isLoading = false, isPasswordProtected = true) }
            } catch (e: Exception) {
                Log.e("PdfViewerViewModel", "Error loading PDF", e)
                _uiState.update { it.copy(isLoading = false, error = "Damaged or unsupported PDF") }
            }
        }
    }

    private var annotationsJob: Job? = null

    private fun loadAnnotations(uri: Uri) {
        annotationsJob?.cancel()
        annotationsJob = viewModelScope.launch {
            annotationDao.getAnnotationsForPdf(uri.toString()).collect { entities ->
                val parsedAnnotations = entities.mapNotNull { entity ->
                    try {
                        val color = androidx.compose.ui.graphics.Color(entity.color)
                        if (entity.type == "HIGHLIGHT") {
                            val rects: List<androidx.compose.ui.geometry.Rect> = gson.fromJson(entity.data, object : TypeToken<List<androidx.compose.ui.geometry.Rect>>() {}.type)
                            PdfAnnotation.Highlight(
                                id = entity.id,
                                pageIndex = entity.pageIndex,
                                color = color,
                                boundingBoxes = rects
                            )
                        } else if (entity.type == "DRAWING") {
                            try {
                                val drawingData: DrawingData = gson.fromJson(entity.data, DrawingData::class.java)
                                PdfAnnotation.Drawing(
                                    id = entity.id,
                                    pageIndex = entity.pageIndex,
                                    color = color,
                                    pathPoints = drawingData.points,
                                    strokeWidth = drawingData.strokeWidth
                                )
                            } catch (e: Exception) {
                                // Fallback for old databases
                                val points: List<PdfAnnotation.Drawing.Point> = gson.fromJson(entity.data, object : TypeToken<List<PdfAnnotation.Drawing.Point>>() {}.type)
                                PdfAnnotation.Drawing(
                                    id = entity.id,
                                    pageIndex = entity.pageIndex,
                                    color = color,
                                    pathPoints = points,
                                    strokeWidth = 3f
                                )
                            }
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                _dbAnnotations.value = parsedAnnotations
            }
        }
    }

    fun addHighlightAnnotation(pageIndex: Int, color: androidx.compose.ui.graphics.Color, rects: List<androidx.compose.ui.geometry.Rect>) {
        val uri = currentUri ?: return
        val highlight = PdfAnnotation.Highlight(
            pageIndex = pageIndex,
            color = color,
            boundingBoxes = rects
        )
        _unsavedAnnotations.value = _unsavedAnnotations.value + highlight
        _hasUnsavedChanges.value = true
        _undoStack.value = _undoStack.value + AnnotationAction.Add(highlight)
        _redoStack.value = emptyList()
        showToast("Text highlighted")
    }

    fun saveAnnotations() {
        val uri = currentUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _unsavedAnnotations.value.forEach { annotation ->
                if (annotation is PdfAnnotation.Highlight) {
                    val entity = PdfAnnotationEntity(
                        id = annotation.id,
                        pdfUri = uri.toString(),
                        pageIndex = annotation.pageIndex,
                        type = "HIGHLIGHT",
                        color = annotation.color.toArgb(),
                        data = gson.toJson(annotation.boundingBoxes)
                    )
                    annotationDao.insertAnnotation(entity)
                } else if (annotation is PdfAnnotation.Drawing) {
                    val drawingData = DrawingData(annotation.pathPoints, annotation.strokeWidth)
                    val entity = PdfAnnotationEntity(
                        id = annotation.id,
                        pdfUri = uri.toString(),
                        pageIndex = annotation.pageIndex,
                        type = "DRAWING",
                        color = annotation.color.toArgb(),
                        data = gson.toJson(drawingData)
                    )
                    annotationDao.insertAnnotation(entity)
                }
            }
            _unsavedAnnotations.value = emptyList()
            _hasUnsavedChanges.value = false
            loadAnnotations(uri)
            showToast("Annotations saved")
        }
    }

    fun discardAnnotations() {
        _unsavedAnnotations.value = emptyList()
        _hasUnsavedChanges.value = false
        showToast("Changes discarded")
    }

    fun removeAnnotation(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            annotationDao.deleteAnnotation(id)
            _unsavedAnnotations.value = _unsavedAnnotations.value.filter { it.id != id }
            loadAnnotations(currentUri ?: return@launch)
        }
    }

    fun toggleAnnotationMode() {
        _uiState.update { 
            it.copy(
                isAnnotationMode = !it.isAnnotationMode,
                annotationTool = AnnotationTool.DRAW // Default to pen
            )
        }
    }

    fun toggleAnnotationVisibility() {
        _uiState.update { it.copy(showAnnotations = !it.showAnnotations) }
    }

    fun setAnnotationTool(tool: AnnotationTool) {
        _uiState.update { it.copy(annotationTool = tool) }
    }

    fun setBrushColor(color: androidx.compose.ui.graphics.Color) {
        _uiState.update { it.copy(brushColor = color) }
    }

    fun setBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size) }
    }

    fun undo() {
        val stack = _undoStack.value
        if (stack.isEmpty()) return

        val lastAction = stack.last()
        _undoStack.value = stack.dropLast(1)
        _redoStack.value = _redoStack.value + lastAction

        when (lastAction) {
            is AnnotationAction.Add -> {
                _unsavedAnnotations.value = _unsavedAnnotations.value.filter { it.id != lastAction.annotation.id }
            }
            is AnnotationAction.Remove -> {
                _unsavedAnnotations.value = _unsavedAnnotations.value + lastAction.annotation
            }
        }
        _hasUnsavedChanges.value = _unsavedAnnotations.value.isNotEmpty()
    }

    fun redo() {
        val stack = _redoStack.value
        if (stack.isEmpty()) return

        val lastAction = stack.last()
        _redoStack.value = stack.dropLast(1)
        _undoStack.value = _undoStack.value + lastAction

        when (lastAction) {
            is AnnotationAction.Add -> {
                _unsavedAnnotations.value = _unsavedAnnotations.value + lastAction.annotation
            }
            is AnnotationAction.Remove -> {
                _unsavedAnnotations.value = _unsavedAnnotations.value.filter { it.id != lastAction.annotation.id }
            }
        }
        _hasUnsavedChanges.value = true
    }

    fun addDrawingAnnotation(pageIndex: Int, color: androidx.compose.ui.graphics.Color, strokeWidth: Float, pathPoints: List<PdfAnnotation.Drawing.Point>) {
        val drawing = PdfAnnotation.Drawing(
            pageIndex = pageIndex,
            color = color,
            pathPoints = pathPoints,
            strokeWidth = strokeWidth
        )
        _unsavedAnnotations.value = _unsavedAnnotations.value + drawing
        _hasUnsavedChanges.value = true
        _undoStack.value = _undoStack.value + AnnotationAction.Add(drawing)
        _redoStack.value = emptyList()
    }

    fun exportPdf(targetUri: android.net.Uri) {
        viewModelScope.launch {
            val sourceFile = decryptedFile ?: return@launch
            val success = com.morphdrop.app.data.pdf.PdfExporter.exportAnnotatedPdf(
                context = application,
                sourceFile = sourceFile,
                targetUri = targetUri,
                annotations = annotations.value
            )
            if (success) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "PDF exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Failed to export PDF", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private data class DrawingData(val points: List<PdfAnnotation.Drawing.Point>, val strokeWidth: Float)

    private suspend fun decryptAndLoad(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: throw Exception("Stream null")
            val document = PDDocument.load(inputStream, password)
            document.setAllSecurityToBeRemoved(true)

            val tempFile = File(application.cacheDir, "decrypted_${System.currentTimeMillis()}.pdf")
            document.save(tempFile)
            val totalPages = document.numberOfPages
            document.close()
            decryptedFile = tempFile

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = pfd
            pdfRenderer = PdfRenderer(pfd)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    totalPages = totalPages,
                    fileName = getFileName(uri),
                    isPasswordProtected = false,
                    error = null
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val toc = com.morphdrop.app.data.pdf.PdfTocExtractor.extractToc(tempFile)
                    _uiState.update { it.copy(tocList = toc) }
                } catch (e: Exception) {
                    android.util.Log.e("PdfViewerViewModel", "Failed to extract TOC", e)
                }
            }
        } catch (e: Exception) {
            Log.e("PdfViewerViewModel", "Decryption failed", e)
            _uiState.update { it.copy(isLoading = false, error = "Incorrect password", isPasswordProtected = true) }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Document.pdf"
        try {
            application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("PdfViewerViewModel", "Error getting file name", e)
        }
        return name
    }

    fun renderPreview(pageIndex: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        val cacheKey = "$pageIndex-preview"
        bitmapCache.get(cacheKey)?.let { return it }

        return try {
            val page = renderer.openPage(pageIndex)
            // Use 2.0f density multiplier to give it crisp base resolution before zoom high-res kicks in.
            val density = application.resources.displayMetrics.density * 2.0f
            val width = (page.width * density).toInt()
            val height = (page.height * density).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            bitmapCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e("PdfViewerViewModel", "Preview render failed", e)
            null
        }
    }

    suspend fun renderHighRes(pageIndex: Int, zoomScale: Float, normalizedViewport: android.graphics.RectF? = null): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        // Round zoom scale to 1 decimal place to avoid excessive re-renders for tiny changes
        val roundedScale = Math.round(zoomScale * 10f) / 10f
        if (roundedScale <= 1.0f) return@withContext null // No high-res needed if not zoomed in

        val cacheKey = if (normalizedViewport != null) {
            val l = Math.round(normalizedViewport.left * 10f) / 10f
            val t = Math.round(normalizedViewport.top * 10f) / 10f
            "$pageIndex-highres-$roundedScale-$l-$t"
        } else {
            "$pageIndex-highres-$roundedScale"
        }

        bitmapCache.get(cacheKey)?.let { return@withContext it }

        try {
            val page = renderer.openPage(pageIndex)
            val renderDensity = application.resources.displayMetrics.density * 2.0f // Boost density for ultra-sharp text

            val bitmap: Bitmap
            val matrix = android.graphics.Matrix()

            if (normalizedViewport != null) {
                val fullWidth = page.width * renderDensity * roundedScale
                val fullHeight = page.height * renderDensity * roundedScale

                val viewWidth = (fullWidth * normalizedViewport.width()).toInt()
                val viewHeight = (fullHeight * normalizedViewport.height()).toInt()

                if (viewWidth <= 0 || viewHeight <= 0) { page.close(); return@withContext null }

                // Allow up to 4096px for max crispness (avoiding OOM)
                val maxViewDim = 4096

                var targetW = viewWidth
                var targetH = viewHeight

                if (targetW > maxViewDim || targetH > maxViewDim) {
                    val ratio = maxViewDim.toFloat() / maxOf(targetW, targetH)
                    targetW = (targetW * ratio).toInt()
                    targetH = (targetH * ratio).toInt()
                }

                bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

                // Scale to full zoomed size
                matrix.postScale(fullWidth / page.width, fullHeight / page.height)
                // Translate to crop the viewport
                matrix.postTranslate(-normalizedViewport.left * fullWidth, -normalizedViewport.top * fullHeight)

                if (targetW != viewWidth) {
                    val ratio = targetW.toFloat() / viewWidth
                    matrix.postScale(ratio, ratio)
                }
            } else {
                var width = (page.width * renderDensity * roundedScale).toInt()
                var height = (page.height * renderDensity * roundedScale).toInt()

                val maxDim = 4096
                if (width > maxDim || height > maxDim) {
                    val ratio = maxDim.toFloat() / maxOf(width, height)
                    width = (width * ratio).toInt()
                    height = (height * ratio).toInt()
                }

                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val scaleX = width.toFloat() / page.width
                val scaleY = height.toFloat() / page.height
                matrix.postScale(scaleX, scaleY)
            }

            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            bitmapCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e("PdfViewerViewModel", "High-res render failed", e)
            null
        }
    }



    fun search(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), currentSearchIndex = -1) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearching = true) }
            val matches = mutableListOf<SearchMatch>()
            try {
                val fileToOpen = decryptedFile
                val document = if (fileToOpen != null) {
                    PDDocument.load(fileToOpen)
                } else {
                    val stream = application.contentResolver.openInputStream(currentUri ?: return@launch)
                    PDDocument.load(stream)
                }

                val stripper = object : PDFTextStripper() {
                    var currentPageIndex = 0
                    var currentWidth = 0f
                    var currentHeight = 0f

                    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                        if (text != null && text.contains(query, ignoreCase = true) && textPositions != null) {
                            val firstPos = textPositions[0]
                            val lastPos = textPositions[textPositions.size - 1]

                            val rect = RectF(
                                firstPos.x,
                                firstPos.y,
                                lastPos.x + lastPos.width,
                                lastPos.y + lastPos.height
                            )
                            matches.add(SearchMatch(currentPageIndex, listOf(rect), currentWidth, currentHeight))
                        }
                        super.writeString(text, textPositions)
                    }
                }

                for (i in 0 until document.numberOfPages) {
                    val pdPage = document.getPage(i)
                    stripper.currentWidth = pdPage.cropBox.width
                    stripper.currentHeight = pdPage.cropBox.height
                    stripper.currentPageIndex = i
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    stripper.getText(document)
                }
                document.close()
            } catch (e: Exception) {
                Log.e("PdfViewerViewModel", "Search failed", e)
            }
            _uiState.update { it.copy(searchResults = matches, currentSearchIndex = if (matches.isNotEmpty()) 0 else -1, isSearching = false) }

            if (matches.isNotEmpty()) {
                _events.emit(PdfViewerEvent.ScrollToPage(matches[0].pageIndex))
            }
        }
    }

    fun navigateSearch(forward: Boolean) {
        val matches = _uiState.value.searchResults
        if (matches.isEmpty()) return

        val nextIndex = if (forward) {
            (_uiState.value.currentSearchIndex + 1) % matches.size
        } else {
            (_uiState.value.currentSearchIndex - 1 + matches.size) % matches.size
        }

        _uiState.update { it.copy(currentSearchIndex = nextIndex) }
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.ScrollToPage(matches[nextIndex].pageIndex))
        }
    }

    fun downloadPdf() {
        val uri = currentUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = application.contentResolver.openInputStream(uri) ?: throw Exception("File not found")
                val fileName = _uiState.value.fileName

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MorphDrop")
                    }
                }

                val contentResolver = application.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val outputUri = contentResolver.insert(collection, values)
                    ?: throw Exception("Unable to create file in Downloads")

                contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                showToast("Saved to Downloads/MorphDrop")
            } catch (e: Exception) {
                Log.e("PdfViewerViewModel", "Download failed", e)
                showToast("Download failed")
            }
        }
    }

    fun printPdf(context: android.content.Context) {
        val uri = currentUri ?: return
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
        val jobName = "MorphDrop - ${_uiState.value.fileName}"

        try {
            val fileToPrint = decryptedFile ?: run {
                val tempFile = File(context.cacheDir, "print_${System.currentTimeMillis()}.pdf")
                application.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }

            printManager.print(jobName, PdfPrintAdapter(fileToPrint, jobName), null)
        } catch (e: Exception) {
            Log.e("PdfViewerViewModel", "Print failed", e)
            showToast("Print failed")
        }
    }

    fun toggleImmersiveMode() {
        _uiState.update { it.copy(isImmersiveMode = !it.isImmersiveMode) }
    }

    fun clearAllSelections() {
        viewModelScope.launch {
            _clearSelectionEvent.emit(Unit)
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            settingsRepository.setReadingMode(mode)
        }
    }

    private var intensityJob: Job? = null
    fun setSepiaIntensity(intensity: Float) {
        _uiState.update { it.copy(sepiaIntensity = intensity) }
        intensityJob?.cancel()
        intensityJob = viewModelScope.launch {
            delay(100) // Debounce
            settingsRepository.setSepiaIntensity(intensity)
        }
    }

    fun setTextureIntensity(intensity: Float) {
        _uiState.update { it.copy(textureIntensity = intensity) }
    }

    private fun loadBookmarks(uri: Uri) {
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            bookmarkRepository.getBookmarksForFile(uri.toString()).collectLatest { bookmarks ->
                _uiState.update { state ->
                    state.copy(
                        bookmarks = bookmarks,
                        isBookmarked = bookmarks.any { it.pageNumber == state.currentPage }
                    )
                }
            }
        }
    }

    fun toggleBookmark() {
        toggleBookmarkForPage(_uiState.value.currentPage)
    }

    fun toggleBookmarkForPage(pageNumber: Int) {
        val uri = currentUri ?: return
        viewModelScope.launch {
            val isBookmarked = _uiState.value.bookmarks.any { it.pageNumber == pageNumber }
            if (isBookmarked) {
                bookmarkRepository.removeBookmark(uri.toString(), pageNumber)
            } else {
                bookmarkRepository.addBookmark(uri.toString(), pageNumber)
            }
        }
    }

    fun sharePdf(context: android.content.Context) {
        val uri = currentUri ?: return
        com.morphdrop.app.data.pdf.ShareSheetHelper.sharePdf(context, uri, _uiState.value.fileName)
    }

    fun shareCurrentPage(context: android.content.Context) {
        val pageIndex = _uiState.value.currentPage - 1
        viewModelScope.launch {
            val bitmap = renderPreview(pageIndex) ?: return@launch
            com.morphdrop.app.data.pdf.ShareSheetHelper.sharePageAsImage(context, bitmap, pageIndex)
        }
    }

    fun updateCurrentPage(page: Int) {
        val pageNum = page + 1
        _uiState.update { it.copy(
            currentPage = pageNum,
            isBookmarked = it.bookmarks.any { bookmark -> bookmark.pageNumber == pageNum }
        ) }
    }

    fun scrollToPage(pageIndex: Int) {
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.ScrollToPage(pageIndex))
        }
    }

    fun getThumbnail(pageIndex: Int): Bitmap? {
        val cached = thumbnailCache.get(pageIndex)
        if (cached != null) return cached

        // If not cached, trigger single generation with high priority
        viewModelScope.launch(Dispatchers.IO) {
            generateThumbnailSync(pageIndex)
        }
        return null
    }

    private fun startThumbnailGeneration(totalPages: Int) {
        pregenerateThumbnailsJob?.cancel()
        pregenerateThumbnailsJob = viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until totalPages) {
                if (thumbnailCache.get(i) == null) {
                    generateThumbnailSync(i)
                    delay(50) // Yield to prevent completely blocking the IO thread
                }
            }
        }
    }

    private fun generateThumbnailSync(pageIndex: Int) {
        val renderer = pdfRenderer ?: return
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return

        synchronized(renderer) {
            try {
                val page = renderer.openPage(pageIndex)
                val width = 200
                val height = (width * (page.height.toFloat() / page.width)).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                thumbnailCache.put(pageIndex, bitmap)
                _thumbnailsReady.update { it + pageIndex }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun renderThumbnail(pageIndex: Int): Bitmap? {
        return getThumbnail(pageIndex)
    }

    override fun onCleared() {
        pdfRenderer?.close()
        fileDescriptor?.close()
        decryptedFile?.delete()
    }
}
