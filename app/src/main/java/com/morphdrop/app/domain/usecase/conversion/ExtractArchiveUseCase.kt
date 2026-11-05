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
    ): List<Uri> = withContext(Dispatchers.IO) {
        val inputName = FileHelper.getFileName(context, inputUri)
        val totalInputBytes = context.contentResolver.openFileDescriptor(inputUri, "r")
            ?.use { pfd -> pfd.statSize.takeIf { it >= 0 } ?: -1L }
            ?: -1L
        val folderName = outputFolderName.ifBlank {
            inputName.substringBeforeLast('.', inputName).ifBlank { "extracted" }
        }
        val baseFolder = settingsRepository.outputFolderName.first()
        val workDir = File(context.cacheDir, "archive_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val extractedFiles: List<File> = if (format == ArchiveFormat.SEVENZ) {
                // SevenZFile needs a seekable file, not a stream.
                val tmp = File(context.cacheDir, "archive_input_${System.currentTimeMillis()}.7z")
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
            if (extractedFiles.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
            // Preserve subfolders inside the archive in Downloads.
            extractedFiles.map { file ->
                val relative = file.relativeTo(workDir).path
                val parent = relative.substringBeforeLast('/', "")
                val targetFolder = if (parent.isBlank()) "$baseFolder/$folderName" else "$baseFolder/$folderName/$parent"
                FileHelper.saveStreamToFile(context, targetFolder, file.name, file)
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Lists an archive's entries (names, sizes) without extracting anything. */
    suspend fun listEntries(inputUri: Uri, format: ArchiveFormat) = withContext(Dispatchers.IO) {
        if (format == ArchiveFormat.SEVENZ) {
            val tmp = File(context.cacheDir, "archive_input_${System.currentTimeMillis()}.7z")
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
