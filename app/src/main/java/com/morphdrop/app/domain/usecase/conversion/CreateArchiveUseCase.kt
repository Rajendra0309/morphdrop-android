package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.archive.ArchiveEngine
import com.morphdrop.app.data.archive.ArchiveEntrySource
import com.morphdrop.app.data.archive.ArchiveFormat
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Creates a ZIP or TAR.GZ archive from the given files, fully offline.
 * The archive is built into a cache file first, then saved to Downloads,
 * so a large archive never sits in memory. TAR entries need their size
 * up front, so each input is stat-ed before creation starts.
 *
 * Deliberate limit: only ZIP and TAR.GZ are created. 7z creation and RAR
 * creation are out of scope (see ArchiveEngine).
 */
class CreateArchiveUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val engine: ArchiveEngine
) {
    suspend operator fun invoke(
        inputUris: List<Uri>,
        format: ArchiveFormat,
        outputFileName: String,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ): Uri = withContext(Dispatchers.IO) {
        require(inputUris.isNotEmpty()) { "Select at least one file to archive." }
        require(format == ArchiveFormat.ZIP || format == ArchiveFormat.TAR_GZ) {
            "Only ZIP and TAR.GZ archives can be created."
        }
        val ext = if (format == ArchiveFormat.ZIP) "zip" else "tar.gz"
        // Compound extensions included: stripping one dot-component
        // from "notes (v2)_converted.tar.gz" leaves "..._converted.tar",
        // and re-appending "tar.gz" doubled it to ".tar.tar.gz".
        // (ZIP was immune only because "zip" has no dot.)
        val baseName = FileHelper.stripArchiveExtension(outputFileName)

        val tmp = File(context.cacheDir, "archive_create_${System.currentTimeMillis()}.tmp")
        try {
            var totalBytes = 0L
            val entries = inputUris.map { uri ->
                val name = FileHelper.getFileName(context, uri)
                    .ifBlank { "file" }
                val size = context.contentResolver.openFileDescriptor(uri, "r")
                    ?.use { pfd -> pfd.statSize.takeIf { it >= 0 } ?: 0L }
                    ?: 0L
                totalBytes += size
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open '$name'.")
                ArchiveEngine.SizedArchiveEntrySource(name, stream, size)
            }
            try {
                // The engine closes each stream after its entry is written;
                // the finally below also closes any stream it never reached
                // (a failure or cancellation mid-list), since closing a
                // stream twice is harmless.
                tmp.outputStream().use { out ->
                    if (format == ArchiveFormat.ZIP) {
                        engine.createZip(entries, out, totalBytes, onProgress, shouldContinue)
                    } else {
                        engine.createTarGz(entries, out, totalBytes, onProgress, shouldContinue)
                    }
                }
            } finally {
                entries.forEach { runCatching { it.inputStream.close() } }
            }
            FileHelper.saveStreamToFile(context, settingsRepository, "$baseName.$ext", tmp)
        } finally {
            tmp.delete()
        }
    }
}
