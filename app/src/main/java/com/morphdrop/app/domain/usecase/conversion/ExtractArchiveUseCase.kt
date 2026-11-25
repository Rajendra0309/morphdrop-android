package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.archive.ArchiveEngine
import com.morphdrop.app.data.archive.ArchiveException
import com.morphdrop.app.data.archive.ArchiveFormat
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Extracts a ZIP, TAR, TAR.GZ or 7z archive into a folder in Downloads,
 * fully offline. Extraction runs into a cache folder first, then each
 * file is saved through MediaStore, so nothing is held in memory and a
 * cancelled job leaves no partial output behind (the cache folder is
 * deleted on any failure or cancellation).
 *
 * Safety comes from ArchiveEngine: zip-slip paths are rejected and total
 * unpacked size / entry count are capped. A .rar file is rejected with a
 * plain message — RAR is not supported by the archive library at all.
 */
/**
 * The result of an extraction: the saved files, plus a warning when the
 * archive was recovered only in part (a truncated ZIP, for example).
 * The warning is a note, not an error — the recovered files are real
 * and are kept.
 */
data class ExtractResult(val uris: List<Uri>, val warning: String? = null)

class ExtractArchiveUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val engine: ArchiveEngine
) {
    suspend operator fun invoke(
        inputUri: Uri,
        format: ArchiveFormat,
        outputFolderName: String,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ): ExtractResult = withContext(Dispatchers.IO) {
        val inputName = FileHelper.getFileName(context, inputUri)
        val totalInputBytes = runCatching {
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                pfd.statSize.takeIf { it >= 0 } ?: -1L
            }
        }.getOrNull() ?: -1L
        // Compound extensions included: "packed.tar.gz" extracts to
        // "packed_extracted", not "packed.tar_extracted".
        val folderName = outputFolderName.ifBlank {
            FileHelper.stripArchiveExtension(inputName).ifBlank { "extracted" }
        }
        val baseFolder = settingsRepository.outputFolderName.first()
        val workDir = File(context.cacheDir, "archive_extract_${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val saved = mutableListOf<Uri>()
        val createdFilePaths = mutableSetOf<String>()
        try {
            val extractResult = if (format == ArchiveFormat.SEVENZ) {
                // SevenZFile needs a seekable file, not a stream.
                val tmp = File.createTempFile("archive_input_", ".7z", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        tmp.outputStream().use { out -> input.copyTo(out) }
                    } ?: throw IllegalArgumentException("Could not open '$inputName'.")
                    engine.extractFromFile(tmp, workDir, onProgress, shouldContinue)
                } finally {
                    tmp.delete()
                }
            } else {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    engine.extract(input, format, workDir, totalInputBytes, onProgress, shouldContinue)
                } ?: throw IllegalArgumentException("Could not open '$inputName'.")
            }
            val extractedFiles = extractResult.files
            if (extractedFiles.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
            // Preserve subfolders inside the archive in Downloads.
            try {
                for (file in extractedFiles) {
                    if (shouldContinue?.invoke() == false) {
                        throw kotlinx.coroutines.CancellationException("Archive extraction cancelled.")
                    }
                    val relative = file.relativeTo(workDir).path
                    val parent = relative.substringBeforeLast('/', "")
                    val targetFolder = if (parent.isBlank()) "$baseFolder/$folderName" else "$baseFolder/$folderName/$parent"
                    val uri = FileHelper.saveStreamToFile(context, targetFolder, file.name, file)
                    saved += uri
                    if (uri.scheme == "file") {
                        uri.path?.let { createdFilePaths.add(it) }
                    }
                    if (shouldContinue?.invoke() == false) {
                        throw kotlinx.coroutines.CancellationException("Archive extraction cancelled.")
                    }
                }
            } catch (e: Throwable) {
                for (uri in saved) {
                    if (uri.scheme == "file") {
                        val path = uri.path
                        if (path != null && path in createdFilePaths) {
                            runCatching {
                                val f = File(path)
                                if (f.exists()) f.delete()
                            }
                        }
                    } else {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                    }
                }
                throw e
            }
            // A truncated ZIP still yields its complete entries, so the
            // extract above succeeds — and without this check the Result
            // screen would call it "Complete" while files are missing.
            // The exact lost count is unknowable (the index is gone), so
            // the note says what is known, not a number. Clean archives
            // complete with warning = null.
            val zipTruncated = format == ArchiveFormat.ZIP && zipLooksTruncated(inputUri)
            val hadPartialFailure = extractResult.hadReadFailure || zipTruncated
            val warning = if (hadPartialFailure) {
                "Recovered ${saved.size} ${if (saved.size == 1) "entry" else "entries"} — " +
                    "this archive had read errors or is incomplete, so more files may have been lost."
            } else {
                null
            }
            ExtractResult(saved, warning)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * True when the file's tail holds no End of Central Directory
     * record — the signature of a ZIP cut short. Reads at most the last
     * 128 KiB through the file descriptor, so it costs almost nothing.
     * Any read failure returns false: an unreadable file is the
     * extractor's problem, not this check's.
     */
    private fun zipLooksTruncated(inputUri: Uri): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return false
            try {
                val size = pfd.statSize
                if (size < 0) return false
                if (size < 22) return true
                val tailSize = minOf(size, 131_072L).toInt()
                val tail = ByteArray(tailSize)
                // Positional read: no stream to close, no skipping.
                val read = android.system.Os.pread(
                    pfd.fileDescriptor, tail, 0, tailSize, size - tailSize
                )
                !ArchiveEngine.hasZipEndRecord(tail.copyOf(read.coerceAtLeast(0)))
            } finally {
                pfd.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Lists an archive's entries (names, sizes) without extracting anything. */
    suspend fun listEntries(inputUri: Uri, format: ArchiveFormat) = withContext(Dispatchers.IO) {
        if (format == ArchiveFormat.SEVENZ) {
            val tmp = File.createTempFile("archive_input_", ".7z", context.cacheDir)
            try {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IllegalArgumentException("Could not open the archive.")
                engine.listEntriesFromFile(tmp)
            } finally {
                tmp.delete()
            }
        } else {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                engine.listEntries(input, format)
            } ?: throw IllegalArgumentException("Could not open the archive.")
        }
    }
}
