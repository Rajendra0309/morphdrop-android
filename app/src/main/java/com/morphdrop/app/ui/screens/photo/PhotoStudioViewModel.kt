package com.morphdrop.app.ui.screens.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.usecase.conversion.PhotoStudioUseCase
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class StudioMode { PHOTO, SIGNATURE }

data class StudioPreset(val label: String, val width: Int, val height: Int)

object StudioPresets {
    val photo = listOf(
        StudioPreset("Passport · 35×45 mm", 413, 531),
        StudioPreset("Square ID", 600, 600),
        StudioPreset("Print · 4×6 in", 1200, 1800)
    )
    val signature = listOf(
        StudioPreset("Standard", 560, 160),
        StudioPreset("Wide", 800, 200),
        StudioPreset("Compact", 300, 80)
    )
    val sizeLimitsKb: List<Int?> = listOf(null, 20, 50, 100, 200)
    const val CUSTOM_PRESET = -1
    const val CUSTOM_SIZE_LIMIT = -1
}

data class StudioBackground(val label: String, val color: Int) {
    companion object {
        val options = listOf(
            StudioBackground("White", Color.WHITE),
            StudioBackground("Light Blue", Color.rgb(220, 233, 245)),
            StudioBackground("Light Grey", Color.rgb(240, 240, 240))
        )
    }
}

data class PhotoStudioUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val previewBitmap: Bitmap? = null,
    val mode: StudioMode = StudioMode.PHOTO,
    val presetIndex: Int = 0,
    val customWidth: String = "",
    val customHeight: String = "",
    val fillFrame: Boolean = true,
    val backgroundIndex: Int = 0,
    val sizeLimitIndex: Int = 2, // 50 KB default for photos
    val customSizeKb: String = "",
    val isProcessing: Boolean = false,
    val isSuccess: Boolean = false,
    val resultUri: Uri? = null,
    val resultFileName: String = "",
    val resultSize: Long = 0L,
    val resultWidth: Int = 0,
    val resultHeight: Int = 0,
    val errorMessage: String? = null
) {
    val presets: List<StudioPreset>
        get() = if (mode == StudioMode.PHOTO) StudioPresets.photo else StudioPresets.signature

    val targetWidth: Int
        get() = if (presetIndex == StudioPresets.CUSTOM_PRESET) {
            customWidth.toIntOrNull() ?: 0
        } else {
            presets.getOrNull(presetIndex)?.width ?: 0
        }

    val targetHeight: Int
        get() = if (presetIndex == StudioPresets.CUSTOM_PRESET) {
            customHeight.toIntOrNull() ?: 0
        } else {
            presets.getOrNull(presetIndex)?.height ?: 0
        }

    val targetSizeKb: Int?
        get() = if (sizeLimitIndex == StudioPresets.CUSTOM_SIZE_LIMIT) {
            customSizeKb.toIntOrNull()?.takeIf { it > 0 }
        } else {
            StudioPresets.sizeLimitsKb.getOrNull(sizeLimitIndex)
        }

    val isSizeLimitCustom: Boolean
        get() = sizeLimitIndex == StudioPresets.CUSTOM_SIZE_LIMIT

    val canCreate: Boolean
        get() = selectedUri != null && !isProcessing &&
            targetWidth in 16..8000 && targetHeight in 16..8000 &&
            (!isSizeLimitCustom || targetSizeKb != null)
}

@HiltViewModel
class PhotoStudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val photoStudioUseCase: PhotoStudioUseCase,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoStudioUiState())
    val state: StateFlow<PhotoStudioUiState> = _state.asStateFlow()

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            val name = FileHelper.getFileName(context, uri)
            val size = FileHelper.getFileSize(context, uri)
            val preview = withContext(Dispatchers.IO) { decodePreview(uri) }
            _state.update {
                it.copy(
                    selectedUri = uri,
                    fileName = name,
                    fileSize = size,
                    previewBitmap = preview,
                    isSuccess = false,
                    resultUri = null,
                    errorMessage = if (preview == null) "Could not read this image. Try a different one." else null
                )
            }
        }
    }

    private fun decodePreview(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 900 && bounds.outHeight / (sample * 2) >= 900) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun setMode(mode: StudioMode) {
        _state.update {
            it.copy(
                mode = mode,
                presetIndex = 0,
                fillFrame = mode == StudioMode.PHOTO,
                sizeLimitIndex = if (mode == StudioMode.PHOTO) 2 else 1,
                isSuccess = false,
                resultUri = null,
                errorMessage = null
            )
        }
    }

    fun setPreset(index: Int) = _state.update { it.copy(presetIndex = index) }

    fun setCustomWidth(value: String) =
        _state.update { it.copy(customWidth = value.filter { ch -> ch.isDigit() }.take(4)) }

    fun setCustomHeight(value: String) =
        _state.update { it.copy(customHeight = value.filter { ch -> ch.isDigit() }.take(4)) }

    fun setFillFrame(fill: Boolean) = _state.update { it.copy(fillFrame = fill) }

    fun setBackground(index: Int) = _state.update { it.copy(backgroundIndex = index) }

    fun setSizeLimit(index: Int) = _state.update { it.copy(sizeLimitIndex = index) }

    fun setCustomSizeKb(value: String) =
        _state.update { it.copy(customSizeKb = value.filter { ch -> ch.isDigit() }.take(5)) }

    fun createImage() {
        val current = _state.value
        val uri = current.selectedUri ?: return
        if (!current.canCreate) return

        val targetWidth = current.targetWidth
        val targetHeight = current.targetHeight
        val targetSizeKb = current.targetSizeKb
        val background = StudioBackground.options.getOrNull(current.backgroundIndex)?.color ?: Color.WHITE
        val baseName = current.fileName.substringBeforeLast('.', "image")
            .ifBlank { "image" }
        val outputName = "${baseName}_${targetWidth}x${targetHeight}.jpg"
        val startTime = System.currentTimeMillis()

        _state.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = photoStudioUseCase(
                    inputUri = uri,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    fillFrame = current.fillFrame,
                    backgroundColor = background,
                    targetSizeKb = targetSizeKb,
                    outputFileName = outputName
                )

                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "photo_studio",
                            inputFileName = current.fileName,
                            outputFileNames = outputName,
                            outputUris = result.outputUri.toString(),
                            displayName = outputName,
                            timestamp = System.currentTimeMillis(),
                            duration = System.currentTimeMillis() - startTime,
                            success = true
                        )
                    )
                    com.morphdrop.app.ui.widget.WidgetUpdateHelper.updateAllWidgets(context)
                } catch (_: Exception) {}

                _state.update {
                    it.copy(
                        isProcessing = false,
                        isSuccess = true,
                        resultUri = result.outputUri,
                        resultFileName = outputName,
                        resultSize = result.sizeBytes,
                        resultWidth = result.width,
                        resultHeight = result.height
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = e.localizedMessage ?: "Could not create the image. Try a different photo."
                        )
                    }
                }
            }
        }
    }

    fun resetResult() {
        _state.update { it.copy(isSuccess = false, resultUri = null, errorMessage = null) }
    }
}
