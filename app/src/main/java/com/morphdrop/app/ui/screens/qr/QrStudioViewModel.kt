package com.morphdrop.app.ui.screens.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.data.qr.QrCodeImage
import com.morphdrop.app.data.qr.QrPayloads
import com.morphdrop.app.data.qr.ScannedCode
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.domain.repository.SettingsRepository
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
import java.io.ByteArrayOutputStream
import javax.inject.Inject

enum class QrContentType(val label: String) {
    TEXT("Text"),
    LINK("Link"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    PHONE("Phone"),
    EMAIL("Email"),
    SMS("SMS")
}

enum class QrScanMode { CAMERA, IMAGE }

data class QrStudioUiState(
    val selectedTab: Int = 0,
    // Create
    val contentType: QrContentType = QrContentType.TEXT,
    val text: String = "",
    val link: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurity: String = "WPA",
    val wifiHidden: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val phone: String = "",
    val emailAddress: String = "",
    val emailSubject: String = "",
    val smsNumber: String = "",
    val smsMessage: String = "",
    val generatedBitmap: Bitmap? = null,
    val generatedPayload: String = "",
    val savedFileName: String? = null,
    // Scan
    val scanMode: QrScanMode = QrScanMode.CAMERA,
    val isScanningImage: Boolean = false,
    val scannedCode: ScannedCode? = null,
    val scanError: String? = null,
    val errorMessage: String? = null
) {
    val payload: String
        get() = when (contentType) {
            QrContentType.TEXT -> QrPayloads.text(text)
            QrContentType.LINK -> if (link.isBlank()) "" else QrPayloads.url(link)
            QrContentType.WIFI -> QrPayloads.wifi(wifiSsid, wifiPassword, wifiSecurity, wifiHidden)
            QrContentType.CONTACT -> QrPayloads.contact(contactName, contactPhone, contactEmail)
            QrContentType.PHONE -> QrPayloads.phone(phone)
            QrContentType.EMAIL -> QrPayloads.email(emailAddress, emailSubject, "")
            QrContentType.SMS -> QrPayloads.sms(smsNumber, smsMessage)
        }

    val canGenerate: Boolean
        get() = when (contentType) {
            QrContentType.TEXT -> text.isNotBlank()
            QrContentType.LINK -> link.isNotBlank()
            QrContentType.WIFI -> wifiSsid.isNotBlank()
            QrContentType.CONTACT ->
                contactName.isNotBlank() || contactPhone.isNotBlank() || contactEmail.isNotBlank()
            QrContentType.PHONE -> phone.count { it.isDigit() } >= 5
            QrContentType.EMAIL -> emailAddress.contains("@")
            QrContentType.SMS -> smsNumber.count { it.isDigit() } >= 5
        }
}

@HiltViewModel
class QrStudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QrStudioUiState())
    val state: StateFlow<QrStudioUiState> = _state.asStateFlow()

    fun setTab(index: Int) = _state.update { it.copy(selectedTab = index) }

    fun setContentType(type: QrContentType) =
        _state.update {
            it.copy(contentType = type, generatedBitmap = null, generatedPayload = "", savedFileName = null)
        }

    /**
     * Any field edit invalidates a previously generated code: leaving the
     * old bitmap on screen would let Save store a QR that no longer
     * matches the form.
     */
    fun update(transform: QrStudioUiState.() -> QrStudioUiState) =
        _state.update {
            it.transform().copy(savedFileName = null, generatedBitmap = null, generatedPayload = "")
        }

    fun generate() {
        val current = _state.value
        if (!current.canGenerate) return
        val payload = current.payload
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { QrCodeImage.generateQrBitmap(payload) }
            _state.update {
                it.copy(
                    generatedBitmap = bitmap,
                    generatedPayload = payload,
                    errorMessage = if (bitmap == null) {
                        "Could not create a QR code from this content. Try shortening it."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun saveGeneratedQr() {
        val current = _state.value
        val bitmap = current.generatedBitmap ?: return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream()
                        .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        .toByteArray()
                }
                val fileName = "qr_code_${System.currentTimeMillis()}.png"
                val uri = FileHelper.saveToFile(context, settingsRepository, fileName, bytes)
                try {
                    historyRepository.insertHistory(
                        ConversionHistoryEntity(
                            conversionType = "qr_studio",
                            inputFileName = "QR Code (${current.contentType.label})",
                            outputFileNames = fileName,
                            outputUris = uri.toString(),
                            displayName = fileName,
                            timestamp = System.currentTimeMillis(),
                            success = true
                        )
                    )
                    com.morphdrop.app.ui.widget.WidgetUpdateHelper.updateAllWidgets(context)
                } catch (_: Exception) {}
                _state.update { it.copy(savedFileName = fileName, errorMessage = null) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.localizedMessage ?: "Could not save the QR code image.") }
            }
        }
    }

    fun setScanMode(mode: QrScanMode) =
        _state.update { it.copy(scanMode = mode, scanError = null, scannedCode = null) }

    fun showScanError(message: String) = _state.update { it.copy(scanError = message) }

    fun onCameraCodeScanned(code: ScannedCode) {
        if (_state.value.scannedCode?.text == code.text) return
        _state.update { it.copy(scannedCode = code, scanError = null) }
    }

    fun clearScanResult() = _state.update { it.copy(scannedCode = null, scanError = null) }

    fun scanImage(uri: Uri) {
        _state.update { it.copy(isScanningImage = true, scanError = null, scannedCode = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bitmap = decodeSampled(uri)
                if (bitmap == null) {
                    null
                } else {
                    try {
                        QrCodeImage.scanBitmap(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            _state.update {
                it.copy(
                    isScanningImage = false,
                    scannedCode = result,
                    scanError = if (result == null) {
                        "No QR code or barcode found in this image. Try a clearer image."
                    } else {
                        null
                    }
                )
            }
        }
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 1600) {
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

    /** Loads scanned text into the Create tab so a scanned code can be re-shared as a fresh QR. */
    fun createFromScanned() {
        val code = _state.value.scannedCode ?: return
        _state.update {
            it.copy(
                selectedTab = 0,
                contentType = QrContentType.TEXT,
                text = code.text,
                generatedBitmap = null,
                savedFileName = null
            )
        }
    }
}
