package com.morphdrop.app.ui.screens.conversion

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.FileType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ConversionConfigState(
    val conversionType: ConversionType? = null,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "",
    val selectedFileSize: Long = -1,
    val outputFormat: String = "",
    val quality: Int = 85,
    val pageRangeStart: String = "",
    val pageRangeEnd: String = "",
    val outputFileName: String = "",
    val isConvertEnabled: Boolean = false,
    val showQualitySlider: Boolean = false,
    val showPageRange: Boolean = false,
    val availableOutputFormats: List<String> = emptyList()
)

@HiltViewModel
class ConversionConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversionTypeId: String = savedStateHandle["conversionTypeId"] ?: ""

    private val _state = MutableStateFlow(ConversionConfigState())
    val state: StateFlow<ConversionConfigState> = _state.asStateFlow()

    init {
        val type = ConversionType.defaultList.find { it.id == conversionTypeId }
        if (type != null) {
            _state.update {
                it.copy(
                    conversionType = type,
                    outputFormat = type.outputType.extension,
                    showQualitySlider = isImageOutput(type),
                    showPageRange = type.inputType == FileType.PDF,
                    availableOutputFormats = getFormatsForType(type)
                )
            }
        }
    }

    fun onFileSelected(uri: Uri, fileName: String, fileSize: Long) {
        val baseName = fileName.substringBeforeLast('.')
        val outputExt = _state.value.outputFormat
        _state.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = fileName,
                selectedFileSize = fileSize,
                outputFileName = "${baseName}_converted.$outputExt",
                isConvertEnabled = true
            )
        }
    }

    fun onOutputFormatChanged(format: String) {
        val baseName = _state.value.outputFileName.substringBeforeLast('.')
        _state.update {
            it.copy(
                outputFormat = format,
                outputFileName = "$baseName.$format"
            )
        }
    }

    fun onQualityChanged(quality: Int) {
        _state.update { it.copy(quality = quality) }
    }

    fun onPageRangeStartChanged(value: String) {
        _state.update { it.copy(pageRangeStart = value) }
    }

    fun onPageRangeEndChanged(value: String) {
        _state.update { it.copy(pageRangeEnd = value) }
    }

    fun onOutputFileNameChanged(name: String) {
        _state.update { it.copy(outputFileName = name) }
    }

    fun getPageRange(): IntRange? {
        val start = _state.value.pageRangeStart.toIntOrNull() ?: return null
        val end = _state.value.pageRangeEnd.toIntOrNull() ?: return null
        if (start < 1 || end < start) return null
        return start..end
    }

    private fun isImageOutput(type: ConversionType): Boolean {
        return type.outputType in listOf(FileType.PNG, FileType.JPG, FileType.WEBP, FileType.BMP) ||
                type.id == "compress_images"
    }

    private fun getFormatsForType(type: ConversionType): List<String> {
        return when (type.id) {
            "pdf_to_images" -> listOf("png", "jpg")
            "image_converter" -> listOf("png", "jpg", "webp", "bmp")
            else -> listOf(type.outputType.extension)
        }
    }
}
