package com.morphdrop.app.ui.screens.signpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.morphdrop.app.domain.usecase.conversion.SignPdfUseCase
import com.morphdrop.app.util.FileHelper
import com.morphdrop.app.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

data class SignPdfState(
    val pdfUri: Uri? = null,
    val pdfName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pagePreview: Bitmap? = null,
    val signatureBitmap: Bitmap? = null,
    val position: String = SignPdfUseCase.POS_BOTTOM_RIGHT,
    val scope: String = SignPdfUseCase.SCOPE_THIS_PAGE,
    val widthPercent: Int = 30,
    val isLoadingPreview: Boolean = false,
    val errorMessage: String? = null
) {
    val canSign: Boolean get() = pdfUri != null && signatureBitmap != null
}

@HiltViewModel
class SignPdfViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SignPdfState())
    val state: StateFlow<SignPdfState> = _state.asStateFlow()

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            val name = runCatching { FileHelper.getFileName(appContext, uri) }.getOrDefault("document.pdf")
            val count = countPages(uri)
            if (count <= 0) {
                _state.update {
                    it.copy(errorMessage = "This file could not be opened as a PDF.", pdfUri = null)
                }
                return@launch
            }
            _state.update {
                it.copy(
                    pdfUri = uri,
                    pdfName = name,
                    pageCount = count,
                    currentPage = 0,
                    errorMessage = null
                )
            }
            renderPreview(uri, 0)
        }
    }

    fun goToPage(index: Int) {
        val s = _state.value
        val target = index.coerceIn(0, (s.pageCount - 1).coerceAtLeast(0))
        if (target == s.currentPage || s.pdfUri == null) return
        _state.update { it.copy(currentPage = target) }
        renderPreview(s.pdfUri, target)
    }

    fun onSignatureDrawn(bitmap: Bitmap) {
        _state.update { it.copy(signatureBitmap = trimTransparentBounds(bitmap), errorMessage = null) }
    }

    fun onSignatureImagePicked(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                _state.update { it.copy(errorMessage = "That image could not be read as a signature.") }
            } else {
                val scaled = if (bitmap.width > 1200) {
                    val ratio = 1200f / bitmap.width
                    Bitmap.createScaledBitmap(
                        bitmap, 1200, (bitmap.height * ratio).toInt().coerceAtLeast(1), true
                    )
                } else {
                    bitmap
                }
                _state.update { it.copy(signatureBitmap = scaled, errorMessage = null) }
            }
        }
    }

    fun onPositionChanged(position: String) = _state.update { it.copy(position = position) }
    fun onScopeChanged(scope: String) = _state.update { it.copy(scope = scope) }
    fun onWidthPercentChanged(percent: Int) = _state.update { it.copy(widthPercent = percent) }

    /** Saves the signature to a cache file and enqueues the worker. Returns the work id. */
    fun startSign(): UUID? {
        val s = _state.value
        val pdfUri = s.pdfUri ?: return null
        val signature = s.signatureBitmap ?: return null
        val signatureFile = File(appContext.cacheDir, "sign_signature_${System.currentTimeMillis()}.png")
        FileOutputStream(signatureFile).use { out ->
            signature.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val baseName = s.pdfName.substringBeforeLast('.', s.pdfName)
        val data = Data.Builder()
            .putString(ConversionWorker.KEY_CONVERSION_TYPE, "sign_pdf")
            .putString(ConversionWorker.KEY_INPUT_URI, pdfUri.toString())
            .putString(ConversionWorker.KEY_OUTPUT_FILE_NAME, "signed_$baseName")
            .putString(ConversionWorker.KEY_SIGNATURE_FILE, signatureFile.absolutePath)
            .putString(ConversionWorker.KEY_SIGN_SCOPE, s.scope)
            .putInt(ConversionWorker.KEY_SIGN_PAGE_INDEX, s.currentPage)
            .putString(ConversionWorker.KEY_SIGN_POSITION, s.position)
            .putInt(ConversionWorker.KEY_SIGN_WIDTH_PERCENT, s.widthPercent)
            .build()
        val request = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(data)
            .addTag("conversion_work")
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
        return request.id
    }

    private fun renderPreview(uri: Uri, index: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPreview = true) }
            val bitmap = withContext(Dispatchers.IO) { renderPage(uri, index) }
            _state.update { it.copy(pagePreview = bitmap, isLoadingPreview = false) }
        }
    }

    private fun renderPage(uri: Uri, index: Int): Bitmap? = runCatching {
        val pfd = appContext.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use {
            PdfRenderer(it).use { renderer ->
                if (index < 0 || index >= renderer.pageCount) return null
                val page = renderer.openPage(index)
                try {
                    val scale = min(1080f / page.width.coerceAtLeast(1), 1600f / page.height.coerceAtLeast(1))
                        .coerceAtMost(3f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        }
    }.getOrNull()

    private suspend fun countPages(uri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            } ?: 0
        }.getOrDefault(0)
    }

    /** Crops empty transparent margins so the placed signature hugs the ink. */
    private fun trimTransparentBounds(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((pixels[y * width + x] ushr 24) > 8) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return bitmap
        val pad = 6
        val left = (minX - pad).coerceAtLeast(0)
        val top = (minY - pad).coerceAtLeast(0)
        val right = (maxX + pad).coerceAtMost(width - 1)
        val bottom = (maxY + pad).coerceAtMost(height - 1)
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
}
