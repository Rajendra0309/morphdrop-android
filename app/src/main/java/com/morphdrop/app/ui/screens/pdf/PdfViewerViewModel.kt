package com.morphdrop.app.ui.screens.pdf

import android.app.Application
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.pdf.PdfPrintAdapter
import com.morphdrop.app.domain.model.SearchMatch
import com.morphdrop.app.domain.repository.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

data class PdfViewerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 1,
    val isPasswordProtected: Boolean = false,
    val fileName: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val currentSearchIndex: Int = -1,
    val isSearching: Boolean = false
)

sealed interface PdfViewerEvent {
    data class ShowSnackbar(val message: String) : PdfViewerEvent
    data class ScrollToPage(val pageIndex: Int) : PdfViewerEvent
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfViewerEvent>()
    val events: SharedFlow<PdfViewerEvent> = _events.asSharedFlow()

    fun showToast(message: String) {
        viewModelScope.launch {
            _events.emit(PdfViewerEvent.ShowSnackbar(message))
        }
    }

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var decryptedFile: File? = null
    private var currentUri: Uri? = null

    init {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(application)
        }
    }

    fun loadPdf(uri: Uri, password: String? = null) {
        currentUri = uri
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

    fun renderPage(pageIndex: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        return try {
            val page = renderer.openPage(pageIndex)
            val density = application.resources.displayMetrics.density
            val scale = density.coerceIn(2f, 4f)
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: OutOfMemoryError) {
            try {
                // Fallback to 1.5x if memory is tight
                val page = renderer.openPage(pageIndex)
                val width = (page.width * 1.5f).toInt()
                val height = (page.height * 1.5f).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } catch (e2: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPageText(pageIndex: Int): String = withContext(Dispatchers.IO) {
        try {
            if (pdfRenderer == null) return@withContext ""
            // We need the PDDocument for text stripping
            // If decryptedFile exists, use it, otherwise open currentUri
            val fileToOpen = decryptedFile
            val document = if (fileToOpen != null) {
                PDDocument.load(fileToOpen)
            } else {
                val stream = application.contentResolver.openInputStream(currentUri ?: return@withContext "")
                PDDocument.load(stream)
            }
            
            val stripper = PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            val text = stripper.getText(document)
            document.close()
            text
        } catch (e: Exception) {
            ""
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

    fun updateCurrentPage(page: Int) {
        _uiState.update { it.copy(currentPage = page + 1) }
    }

    override fun onCleared() {
        pdfRenderer?.close()
        fileDescriptor?.close()
        decryptedFile?.delete()
    }
}
