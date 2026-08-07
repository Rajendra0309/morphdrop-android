package com.morphdrop.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.morphdrop.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileHelper {

    fun readFileFromUri(context: Context, uri: Uri): InputStream {
        if (uri.scheme == "file" && (uri.path != null)) {
            val file = File(uri.path!!)
            if (file.exists()) {
                return file.inputStream()
            }
        }
        return try {
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Cannot open input stream for URI: $uri")
        } catch (e: Exception) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw e
                java.io.FileInputStream(pfd.fileDescriptor)
            } catch (ex: Exception) {
                throw FileNotFoundException("Cannot read URI ($uri): ${ex.localizedMessage ?: e.localizedMessage}")
            }
        }
    }

    fun sanitizeForPdfBox(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '•' -> sb.append("* ")
                '…' -> sb.append("...")
                '“', '”' -> sb.append('"')
                '‘', '’' -> sb.append('\'')
                '–', '—' -> sb.append('-')
                '\u00A0' -> sb.append(' ')
                '\t' -> sb.append("    ")
                '│', '┃', '┆', '┇', '┊', '┠', '┨', '┯', '┰', '┱', '┲', '┳', '┴', '┵', '┶', '┷', '┸', '┹', '┺', '┻', '┼', '┽', '┾', '┿', '╀', '╁', '╂', '╃', '╄', '╅', '╆', '╇', '╈', '╉', '╋' -> sb.append('|')
                '─', '━', '┄', '┅', '┈', '┉', '═' -> sb.append('-')
                '┌', '┍', '┎', '┏', '┐', '┑', '┒', '┓', '└', '┕', '┖', '┗', '┘', '┙', '┚', '┛', '├', '┝', '┞', '┟', '┢', '┤', '┥', '┦', '┧', '┪', '┬', '┴', '╔', '╦', '╗', '╠', '╬', '╣', '╚', '╩', '╝' -> sb.append('+')
                '►', '▶' -> sb.append('>')
                '◄', '◀' -> sb.append('<')
                '▲', '▴' -> sb.append('^')
                '▼', '▾' -> sb.append('v')
                '→' -> sb.append("->")
                '←' -> sb.append("<-")
                '↑' -> sb.append("^")
                '↓' -> sb.append("v")
                else -> {
                    val code = c.code
                    if (code in 32..126 || code in 160..255 || c == '\n' || c == '\r') {
                        sb.append(c)
                    } else {
                        sb.append('?')
                    }
                }
            }
        }
        return sb.toString()
    }

    fun saveToCache(context: Context, fileName: String, data: ByteArray): Uri {
        val file = File(context.cacheDir, fileName)
        file.writeBytes(data)
        return Uri.fromFile(file)
    }

    suspend fun saveToFile(context: Context, settingsRepository: SettingsRepository, fileName: String, data: ByteArray): Uri {
        val folderName = settingsRepository.outputFolderName.first()
        return saveToFile(context, folderName, fileName, data)
    }

    fun saveToFile(context: Context, folderName: String, fileName: String, data: ByteArray): Uri {
        val resolver = context.contentResolver
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExtension(fileName)) ?: "application/octet-stream"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/" + folderName)
            }
            
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, contentValues) ?: throw FileNotFoundException("Could not create MediaStore entry")
            
            resolver.openOutputStream(uri)?.use { it.write(data) }
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), folderName)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(data)
            
            return Uri.fromFile(file)
        }
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
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "unknown"
        }
        var name = "unknown"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: "unknown"
                }
            }
        } catch (_: SecurityException) {
            name = "unknown"
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file" && (uri.path != null)) {
            val file = File(uri.path!!)
            if (file.exists()) return file.length()
        }
        var size = -1L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) size = cursor.getLong(idx)
                }
            }
        } catch (_: SecurityException) {
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareFile(context: Context, uri: Uri): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(context, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(intent, "Share file via")
    }

    fun createOutputDirectory(folderName: String): Uri {
        val externalDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val folder = File(externalDir, folderName)
        if (!folder.exists()) folder.mkdirs()
        return Uri.fromFile(folder)
    }

    fun getOutputFolderUri(folderName: String): Uri {
        val externalDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val folder = File(externalDir, folderName)
        return Uri.fromFile(folder)
    }

    fun openFolderIntent(folderName: String): Intent {
        val uri = getOutputFolderUri(folderName)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
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

    private fun getFileExtension(fileName: String): String = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun getFileNameWithoutExtension(fileName: String): String = fileName.substringBeforeLast('.')

    fun deleteFileByName(context: Context, folderName: String, fileName: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            
            // 1. Delete if it is a single file with that exact display name
            val selectionFile = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgsFile = arrayOf(fileName, "%$folderName%")
            try {
                resolver.delete(collection, selectionFile, selectionArgsFile)
            } catch (_: Exception) {
                // Ignore failure
            }

            // 2. Delete if it is a folder (delete all files inside that folder path)
            // RELATIVE_PATH in MediaStore will be like "Download/MorphDrop/fileName/"
            val selectionFolder = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgsFolder = arrayOf("%$folderName/$fileName/%")
            try {
                resolver.delete(collection, selectionFolder, selectionArgsFolder)
            } catch (_: Exception) {
                // Ignore failure
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), folderName)
            val file = File(dir, fileName)
            if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }
}
