package com.morphdrop.app.data.archive

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.utils.CountingInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** One entry inside an archive, for the preview list and for progress. */
data class ArchiveEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean
)

/** A file to put into a new archive: its name inside the archive and its bytes. */
open class ArchiveEntrySource(
    val name: String,
    val inputStream: InputStream
)

enum class ArchiveFormat {
    ZIP, TAR, TAR_GZ, SEVENZ;

    companion object {
        /** Detects the format from the filename alone. Returns null for anything else. */
        fun detect(fileName: String): ArchiveFormat? {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR_GZ
                lower.endsWith(".zip") -> ZIP
                lower.endsWith(".tar") -> TAR
                lower.endsWith(".7z") -> SEVENZ
                else -> null
            }
        }
    }
}

sealed class ArchiveException(message: String) : Exception(message) {
    class UnsupportedFormat(message: String) : ArchiveException(message)
    class EmptyArchive(message: String) : ArchiveException(message)
    class UnsafeEntry(message: String) : ArchiveException(message)
    class TooLarge(message: String) : ArchiveException(message)
    class CorruptArchive(message: String) : ArchiveException(message)
    class OutputWriteException(message: String, cause: Throwable? = null) : ArchiveException(message)
}

/**
 * Archive engine, built on Apache Commons Compress 1.28.0 (pure Java,
 * Apache 2.0, no native code — fits the offline-first rule).
 *
 * Deliberate limits, stated wherever the user can see them:
 * - RAR is NOT supported by this library at all. A .rar file is rejected
 *   with a plain message instead of being offered and failing.
 * - 7z writing is not offered; 7z extraction supports the subset of
 *   algorithms Commons Compress knows (LZMA/LZMA2/BZIP2/Deflate, plus
 *   uncompressed). Encrypted 7z archives are not supported.
 * - Extraction is guarded against zip-slip (an entry whose path escapes
 *   the destination is rejected) and decompression bombs (a cap on total
 *   uncompressed bytes and on entry count).
 *
 * This class takes streams and files, never a Context or a Uri, so it is
 * unit-testable on the JVM without Robolectric. The use cases own the
 * Uri -> stream / temp-file translation.
 */
class ArchiveEngine(
    private val maxTotalUncompressedBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    // The primary constructor keeps its defaults for tests (a small cap
    // proves the TooLarge guard); Hilt needs exactly one @Inject
    // constructor, so it gets its own no-arg one here.
    @Inject
    constructor() : this(DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_ENTRIES)
    companion object {
        /** 1 GiB of uncompressed output per extraction — a phone has no business unpacking more. */
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 1_000_000_000L
        const val DEFAULT_MAX_ENTRIES: Int = 10_000

        /**
         * Whether [tail] — the last bytes of a file — contains a ZIP End of
         * Central Directory record (signature 50 4B 05 06). Every valid
         * ZIP ends with one; a truncated archive loses it, while the
         * local-header stream still yields the entries that survived —
         * so an extract can succeed and silently drop files. The record
         * itself is 22 bytes, but the scan covers the last 64 KiB like
         * the standard readers do, so trailing junk and ZIP64 locators
         * don't produce a false alarm. The exact number of lost entries
         * is unknowable without the central directory, so callers must
         * phrase the warning as "looks truncated", never as a count.
         */
        fun hasZipEndRecord(tail: ByteArray): Boolean {
            if (tail.size < 22) return false
            // Scan backwards from tail.size - 22 down to 0
            for (i in (tail.size - 22) downTo 0) {
                if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4B.toByte() &&
                    tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
                ) {
                    val commentLen = (tail[i + 20].toInt() and 0xFF) or ((tail[i + 21].toInt() and 0xFF) shl 8)
                    if (i + 22 + commentLen <= tail.size) {
                        return true
                    }
                }
            }
            return false
        }
    }

    // ---- listing (preview) ----

    /** Lists an archive's entries without extracting anything. */
    fun listEntries(input: InputStream, format: ArchiveFormat): List<ArchiveEntryInfo> {
        val entries = mutableListOf<ArchiveEntryInfo>()
        try {
            when (format) {
                ArchiveFormat.ZIP -> {
                    ZipArchiveInputStream(input).use { zip ->
                        var entry = try {
                            zip.nextEntry
                        } catch (e: LinkageError) {
                            throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                        }
                        var pending: ZipArchiveEntry? = null
                        var count = 0
                        while (entry != null) {
                            count++
                            if (count > maxEntries) break
                            pending?.let { prev ->
                                entries += ArchiveEntryInfo(prev.name, prev.size.coerceAtLeast(0), prev.isDirectory)
                            }
                            pending = entry
                            entry = try {
                                zip.nextEntry
                            } catch (e: LinkageError) {
                                throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                            }
                        }
                        pending?.let { prev ->
                            entries += ArchiveEntryInfo(prev.name, prev.size.coerceAtLeast(0), prev.isDirectory)
                        }
                    }
                }
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> {
                    tarInputStream(input, format).use { tar ->
                        var entry: TarArchiveEntry? = try {
                            tar.nextEntry
                        } catch (e: LinkageError) {
                            throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                        }
                        var count = 0
                        while (entry != null) {
                            count++
                            if (count > maxEntries) break
                            entries += ArchiveEntryInfo(entry.name, entry.size.coerceAtLeast(0), entry.isDirectory)
                            entry = try {
                                tar.nextEntry
                            } catch (e: LinkageError) {
                                throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                            }
                        }
                    }
                }
                ArchiveFormat.SEVENZ -> throw ArchiveException.UnsupportedFormat(
                    "7z archives need a seekable file — use listEntriesFromFile instead."
                )
            }
        } catch (e: LinkageError) {
            throw ArchiveException.UnsupportedFormat("Unsupported archive entry format: ${e.message}")
        }
        if (entries.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
        return entries
    }

    /** Lists a 7z archive's entries. SevenZFile needs a seekable file, not a stream. */
    fun listEntriesFromFile(file: File): List<ArchiveEntryInfo> {
        val entries = mutableListOf<ArchiveEntryInfo>()
        try {
            java.nio.channels.FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ).use { channel ->
                SevenZFile.builder()
                    .setSeekableByteChannel(channel)
                    .setMaxMemoryLimitKiB(256 * 1024)
                    .get().use { sevenZ ->
                        var entry = sevenZ.nextEntry
                        var count = 0
                        while (entry != null) {
                            count++
                            if (count > maxEntries) break
                            entries += ArchiveEntryInfo(entry.name, entry.size.coerceAtLeast(0), entry.isDirectory)
                            entry = sevenZ.nextEntry
                        }
                    }
            }
        } catch (e: org.apache.commons.compress.MemoryLimitException) {
            throw ArchiveException.TooLarge("7z archive exceeds memory limits to unpack.")
        } catch (e: ArchiveException) {
            throw e
        } catch (e: Exception) {
            throw ArchiveException.CorruptArchive("This file couldn't be read as a 7z archive: ${e.message}")
        }
        if (entries.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
        return entries
    }

    // ---- creation ----

    /** Creates a ZIP archive from the given entries, streamed to [output]. */
    fun createZip(
        entries: List<ArchiveEntrySource>,
        output: OutputStream,
        totalInputBytes: Long,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ) {
        ZipArchiveOutputStream(output).use { zip ->
            zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
            var written = 0L
            val seenNames = mutableSetOf<String>()
            for (source in entries) {
                if (shouldContinue?.invoke() == false) throw kotlinx.coroutines.CancellationException("Archive creation cancelled")
                val entryName = uniqueName(source.name, seenNames)
                zip.putArchiveEntry(ZipArchiveEntry(entryName))
                source.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (shouldContinue?.invoke() == false) throw kotlinx.coroutines.CancellationException("Archive creation cancelled")
                        zip.write(buffer, 0, read)
                        written += read
                        if (totalInputBytes > 0) onProgress?.invoke((written.toFloat() / totalInputBytes).coerceIn(0f, 1f))
                    }
                }
                zip.closeArchiveEntry()
            }
            zip.finish()
        }
    }

    /** Creates a TAR.GZ archive from the given entries, streamed to [output]. */
    fun createTarGz(
        entries: List<ArchiveEntrySource>,
        output: OutputStream,
        totalInputBytes: Long,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ) {
        GzipCompressorOutputStream(output).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                var written = 0L
                val seenNames = mutableSetOf<String>()
                for (source in entries) {
                    if (shouldContinue?.invoke() == false) throw kotlinx.coroutines.CancellationException("Archive creation cancelled")
                    val entryName = uniqueName(source.name, seenNames)
                    // Tar entries need a declared size up front; the caller
                    // supplies it through the stream's available bytes only
                    // when known, so count from a temp copy instead — the
                    // use case passes a File-backed stream and sets the
                    // size via ArchiveEntrySourceWithSize below.
                    val size = (source as? SizedArchiveEntrySource)?.sizeBytes
                        ?: throw IllegalArgumentException("TAR creation needs the entry size up front")
                    val entry = TarArchiveEntry(entryName)
                    entry.size = size
                    tar.putArchiveEntry(entry)
                    source.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (shouldContinue?.invoke() == false) throw kotlinx.coroutines.CancellationException("Archive creation cancelled")
                            tar.write(buffer, 0, read)
                            written += read
                            if (totalInputBytes > 0) onProgress?.invoke((written.toFloat() / totalInputBytes).coerceIn(0f, 1f))
                        }
                    }
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
    }

    /** An entry source that also declares its size, which TAR requires up front. */
    class SizedArchiveEntrySource(
        name: String,
        inputStream: InputStream,
        val sizeBytes: Long
    ) : ArchiveEntrySource(name, inputStream)

    // ---- extraction ----

    /**
     * Extracts a ZIP / TAR / TAR.GZ archive from [input] into [outputDir].
     * Returns the extracted files (directories excluded). Throws
     * [ArchiveException.UnsafeEntry] on a zip-slip path, [ArchiveException.TooLarge]
     * past the uncompressed-size or entry-count cap, and
     * [ArchiveException.CorruptArchive] when the stream cannot be read as
     * an archive at all. A cancelled job leaves its partial output behind
     * for the caller to delete — the use case owns that cleanup.
     */
    fun extract(
        input: InputStream,
        format: ArchiveFormat,
        outputDir: File,
        totalInputBytes: Long = -1,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ): List<File> {
        val counting = CountingInputStream(input)
        val extracted = mutableListOf<File>()
        var totalWritten = 0L
        var entryCount = 0
        val seenDestPaths = mutableSetOf<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        fun resolveDestFile(name: String, isDirectory: Boolean): File {
            val rawDest = File(outputDir, name)
            val destPath = rawDest.canonicalPath
            val basePath = outputDir.canonicalPath.let {
                if (it.endsWith(File.separator)) it else it + File.separator
            }
            if (destPath != outputDir.canonicalPath && !destPath.startsWith(basePath)) {
                throw ArchiveException.UnsafeEntry("Archive entry '$name' would escape the destination folder.")
            }
            if (isDirectory) return rawDest
            var dest = rawDest
            if (destPath in seenDestPaths) {
                val parent = dest.parentFile
                val fileName = dest.name
                val stem = fileName.substringBeforeLast('.', fileName)
                val ext = fileName.substringAfterLast('.', "").let { if (it.isNotEmpty() && fileName.contains('.')) ".$it" else "" }
                var n = 2
                while (true) {
                    val candidate = File(parent, "$stem ($n)$ext")
                    if (candidate.canonicalPath !in seenDestPaths) {
                        dest = candidate
                        break
                    }
                    n++
                }
            }
            seenDestPaths.add(dest.canonicalPath)
            return dest
        }

        fun writeEntry(entryName: String, isDirectory: Boolean, readBytes: (ByteArray) -> Int) {
            entryCount++
            if (entryCount > maxEntries) throw ArchiveException.TooLarge("This archive has too many entries to extract safely.")
            val dest = resolveDestFile(entryName, isDirectory)
            if (isDirectory || entryName.endsWith("/")) {
                dest.mkdirs()
                return
            }
            try {
                dest.parentFile?.mkdirs()
            } catch (e: Exception) {
                throw ArchiveException.OutputWriteException("Failed to create directory for '${dest.name}': ${e.message}", e)
            }
            try {
                dest.outputStream().use { out ->
                    var read: Int
                    while (readBytes(buffer).also { read = it } != -1) {
                        if (shouldContinue?.invoke() == false) throw kotlinx.coroutines.CancellationException("Extraction cancelled")
                        totalWritten += read
                        if (totalWritten > maxTotalUncompressedBytes) {
                            throw ArchiveException.TooLarge("This archive would unpack to more than ${maxTotalUncompressedBytes / 1_000_000} MB.")
                        }
                        try {
                            out.write(buffer, 0, read)
                        } catch (e: Exception) {
                            throw ArchiveException.OutputWriteException("Failed to write to '${dest.name}': ${e.message}", e)
                        }
                        if (totalInputBytes > 0) {
                            onProgress?.invoke((counting.bytesRead.toFloat() / totalInputBytes).coerceIn(0f, 1f))
                        }
                    }
                }
            } catch (e: ArchiveException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is java.io.FileNotFoundException || e is java.io.SyncFailedException) {
                    throw ArchiveException.OutputWriteException("Failed to open output file '${dest.name}': ${e.message}", e)
                }
                throw e
            }
            extracted += dest
        }

        try {
            when (format) {
                ArchiveFormat.ZIP -> {
                    ZipArchiveInputStream(counting).use { zip ->
                        var entry = try {
                            zip.nextEntry
                        } catch (e: LinkageError) {
                            throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                        } catch (e: Exception) {
                            if (extracted.isNotEmpty()) null else throw e
                        }
                        if (entry == null && extracted.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
                        while (entry != null) {
                            writeEntry(entry.name, entry.isDirectory) { buf -> zip.read(buf) }
                            entry = try {
                                zip.nextEntry
                            } catch (e: LinkageError) {
                                throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                            } catch (e: Exception) {
                                if (extracted.isNotEmpty()) break
                                throw e
                            }
                        }
                    }
                }
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> {
                    tarInputStream(counting, format).use { tar ->
                        var entry: TarArchiveEntry? = try {
                            tar.nextEntry
                        } catch (e: LinkageError) {
                            throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                        } catch (e: Exception) {
                            if (extracted.isNotEmpty()) null else throw e
                        }
                        if (entry == null && extracted.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
                        while (entry != null) {
                            writeEntry(entry.name, entry.isDirectory) { buf -> tar.read(buf) }
                            entry = try {
                                tar.nextEntry
                            } catch (e: LinkageError) {
                                throw ArchiveException.UnsupportedFormat("Unsupported compression algorithm in archive entry: ${e.message}")
                            } catch (e: Exception) {
                                if (extracted.isNotEmpty()) break
                                throw e
                            }
                        }
                    }
                }
                ArchiveFormat.SEVENZ -> throw ArchiveException.UnsupportedFormat(
                    "7z archives need a seekable file — use extractFromFile instead."
                )
            }
        } catch (e: ArchiveException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ArchiveException.CorruptArchive("This file couldn't be read as an archive — it may be corrupt or incomplete.")
        }
        if (extracted.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
        return extracted
    }

    /** Extracts a 7z archive from a seekable [file] into [outputDir]. Same guards as [extract]. */
    fun extractFromFile(
        file: File,
        outputDir: File,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ): List<File> {
        val extracted = mutableListOf<File>()
        var totalWritten = 0L
        var entryCount = 0
        val seenDestPaths = mutableSetOf<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        fun resolveDestFile(name: String, isDirectory: Boolean): File {
            val rawDest = File(outputDir, name)
            val destPath = rawDest.canonicalPath
            val basePath = outputDir.canonicalPath.let {
                if (it.endsWith(File.separator)) it else it + File.separator
            }
            if (destPath != outputDir.canonicalPath && !destPath.startsWith(basePath)) {
                throw ArchiveException.UnsafeEntry("Archive entry '$name' would escape the destination folder.")
            }
            if (isDirectory) return rawDest
            var dest = rawDest
            if (destPath in seenDestPaths) {
                val parent = dest.parentFile
                val fileName = dest.name
                val stem = fileName.substringBeforeLast('.', fileName)
                val ext = fileName.substringAfterLast('.', "").let { if (it.isNotEmpty() && fileName.contains('.')) ".$it" else "" }
                var n = 2
                while (true) {
                    val candidate = File(parent, "$stem ($n)$ext")
                    if (candidate.canonicalPath !in seenDestPaths) {
                        dest = candidate
                        break
                    }
                    n++
                }
            }
            seenDestPaths.add(dest.canonicalPath)
            return dest
        }

        try {
            java.nio.channels.FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ).use { channel ->
                SevenZFile.builder()
                    .setSeekableByteChannel(channel)
                    .setMaxMemoryLimitKiB(256 * 1024)
                    .get().use { sevenZ ->
                        val allEntries = sevenZ.entries.toList()
                        if (allEntries.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
                        val totalSize = allEntries.sumOf { it.size.coerceAtLeast(0) }
                        var next = sevenZ.nextEntry
                        while (next != null) {
                            val name = next.name
                            val isDir = next.isDirectory
                            entryCount++
                            if (entryCount > maxEntries) throw ArchiveException.TooLarge("This archive has too many entries to extract safely.")
                            val dest = resolveDestFile(name, isDir)
                            if (!isDir) {
                                try {
                                    dest.parentFile?.mkdirs()
                                } catch (e: Exception) {
                                    throw ArchiveException.OutputWriteException("Failed to create directory for '${dest.name}': ${e.message}", e)
                                }
                                try {
                                    dest.outputStream().use { out ->
                                        var read: Int
                                        while (sevenZ.read(buffer).also { read = it } != -1) {
                                            if (shouldContinue?.invoke() == false) throw kotlinx.coroutines.CancellationException("Extraction cancelled")
                                            totalWritten += read
                                            if (totalWritten > maxTotalUncompressedBytes) {
                                                throw ArchiveException.TooLarge("This archive would unpack to more than ${maxTotalUncompressedBytes / 1_000_000} MB.")
                                            }
                                            try {
                                                out.write(buffer, 0, read)
                                            } catch (e: Exception) {
                                                throw ArchiveException.OutputWriteException("Failed to write to '${dest.name}': ${e.message}", e)
                                            }
                                        }
                                    }
                                } catch (e: ArchiveException) {
                                    throw e
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    if (e is java.io.FileNotFoundException || e is java.io.SyncFailedException) {
                                        throw ArchiveException.OutputWriteException("Failed to open output file '${dest.name}': ${e.message}", e)
                                    }
                                    throw e
                                }
                                extracted += dest
                            } else {
                                dest.mkdirs()
                            }
                            if (totalSize > 0) onProgress?.invoke((totalWritten.toFloat() / totalSize).coerceIn(0f, 1f))
                            next = sevenZ.nextEntry
                        }
                    }
            }
        } catch (e: org.apache.commons.compress.MemoryLimitException) {
            throw ArchiveException.TooLarge("7z archive exceeds memory limits to unpack.")
        } catch (e: ArchiveException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ArchiveException.CorruptArchive("This file couldn't be read as a 7z archive — it may be corrupt or incomplete.")
        }
        if (extracted.isEmpty()) throw ArchiveException.EmptyArchive("This archive contains no files.")
        return extracted
    }

    private fun tarInputStream(input: InputStream, format: ArchiveFormat): TarArchiveInputStream {
        return if (format == ArchiveFormat.TAR_GZ) {
            TarArchiveInputStream(GzipCompressorInputStream(input))
        } else {
            TarArchiveInputStream(input)
        }
    }

    private fun uniqueName(name: String, seen: MutableSet<String>): String {
        if (seen.add(name)) return name
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isNotEmpty() && name.contains('.')) ".$it" else "" }
        var n = 2
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (seen.add(candidate)) return candidate
            n++
        }
    }
}
