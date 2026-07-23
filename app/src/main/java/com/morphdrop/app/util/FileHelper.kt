package com.morphdrop.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileHelper {

    fun readFileFromUri(context: Context, uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open input stream for URI: $uri")
    }

    fun saveToCache(context: Context, fileName: String, data: ByteArray): Uri {
        val file = File(context.cacheDir, fileName)
        file.writeBytes(data)
        return Uri.fromFile(file)
    }

    fun saveToFile(context: Context, directoryUri: Uri, fileName: String, data: ByteArray): Uri {
        val directoryPath = directoryUri.path ?: context.cacheDir.path
        val dir = File(directoryPath)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(data)
        return Uri.fromFile(file)
    }

    fun saveToFile(context: Context, folderName: String, fileName: String, data: ByteArray): Uri {
        val dir = File(context.cacheDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(data)
        return Uri.fromFile(file)
    }

    fun saveToUri(context: Context, outputUri: Uri, data: ByteArray) {
        context.contentResolver.openOutputStream(outputUri)?.use { it.write(data) }
            ?: throw FileNotFoundException("Cannot open output stream for URI: $outputUri")
    }

    fun openOutputStream(context: Context, outputUri: Uri): OutputStream {
        return context.contentResolver.openOutputStream(outputUri)
            ?: throw FileNotFoundException("Cannot open output stream for URI: $outputUri")
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: "unknown"
                }
            }
        } catch (e: SecurityException) {
            name = "unknown"
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size = -1L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) size = cursor.getLong(idx)
                }
            }
        } catch (e: SecurityException) {
            size = -1L
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExtension(getFileName(context, uri)))
            ?: "application/octet-stream"
    }

    fun openFile(context: Context, uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(context, uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareFile(context: Context, uri: Uri): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(context, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Share file via")
    }

    fun createOutputDirectory(context: Context, folderName: String): Uri {
        val dir = File(context.cacheDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        return Uri.fromFile(dir)
    }

    fun saveToDirectory(context: Context, directoryName: String, fileName: String, data: ByteArray): Uri {
        return saveToFile(context, directoryName, fileName, data)
    }

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val group = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.size - 1)
        val value = sizeBytes / 1024.0.pow(group.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[group])
    }

    private fun getFileExtension(fileName: String): String = fileName.substringAfterLast('.', "")

    fun getFileNameWithoutExtension(fileName: String): String = fileName.substringBeforeLast('.')
}
