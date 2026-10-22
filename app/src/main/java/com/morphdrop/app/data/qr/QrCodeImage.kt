package com.morphdrop.app.data.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.tasks.await

/** A decoded code: its text content plus a user-facing format name. */
data class ScannedCode(val text: String, val formatName: String)

/**
 * QR and barcode rendering/decoding.
 *
 * Generation uses ZXing (small, deterministic, fully offline). Scanning
 * from an image prefers ML Kit — already used in this app for OCR — and
 * falls back to ZXing when ML Kit finds nothing, which covers some
 * low-contrast codes ML Kit passes over.
 */
object QrCodeImage {

    private const val QR_SIZE_PX = 1080

    /** Renders [payload] as a black-on-white QR bitmap, or null if it cannot be encoded. */
    fun generateQrBitmap(payload: String): Bitmap? {
        if (payload.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                QR_SIZE_PX,
                QR_SIZE_PX,
                hints
            )
            val pixels = IntArray(QR_SIZE_PX * QR_SIZE_PX)
            for (y in 0 until QR_SIZE_PX) {
                val rowOffset = y * QR_SIZE_PX
                for (x in 0 until QR_SIZE_PX) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, QR_SIZE_PX, 0, 0, QR_SIZE_PX, QR_SIZE_PX)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Scans a still image for any barcode or QR code. Null when none is found. */
    suspend fun scanBitmap(bitmap: Bitmap): ScannedCode? {
        scanWithMlKit(bitmap)?.let { return it }
        return scanWithZxing(bitmap)
    }

    private suspend fun scanWithMlKit(bitmap: Bitmap): ScannedCode? {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return try {
            val barcodes = scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.let { barcode ->
                ScannedCode(
                    text = barcode.rawValue.orEmpty(),
                    formatName = formatDisplayName(barcode.format)
                )
            }
        } catch (e: Exception) {
            null
        } finally {
            scanner.close()
        }
    }

    private fun scanWithZxing(bitmap: Bitmap): ScannedCode? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binary)
            val text = result.text?.takeIf { it.isNotBlank() } ?: return null
            ScannedCode(text = text, formatName = formatDisplayName(result.barcodeFormat))
        } catch (e: Exception) {
            null
        }
    }

    fun formatDisplayName(mlKitFormat: Int): String = when (mlKitFormat) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        else -> "Barcode"
    }

    private fun formatDisplayName(zxingFormat: BarcodeFormat?): String = when (zxingFormat) {
        BarcodeFormat.QR_CODE -> "QR Code"
        BarcodeFormat.AZTEC -> "Aztec"
        BarcodeFormat.CODABAR -> "Codabar"
        BarcodeFormat.CODE_39 -> "Code 39"
        BarcodeFormat.CODE_93 -> "Code 93"
        BarcodeFormat.CODE_128 -> "Code 128"
        BarcodeFormat.DATA_MATRIX -> "Data Matrix"
        BarcodeFormat.EAN_8 -> "EAN-8"
        BarcodeFormat.EAN_13 -> "EAN-13"
        BarcodeFormat.ITF -> "ITF"
        BarcodeFormat.PDF_417 -> "PDF417"
        BarcodeFormat.UPC_A -> "UPC-A"
        BarcodeFormat.UPC_E -> "UPC-E"
        else -> "Barcode"
    }
}
