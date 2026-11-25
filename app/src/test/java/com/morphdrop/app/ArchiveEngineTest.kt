package com.morphdrop.app

import com.morphdrop.app.data.archive.ArchiveEngine
import com.morphdrop.app.data.archive.ArchiveEntrySource
import com.morphdrop.app.data.archive.ArchiveException
import com.morphdrop.app.data.archive.ArchiveFormat
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class ArchiveEngineTest {

    private val engine = ArchiveEngine()

    @Test
    fun `detect format from filename`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect("report.zip"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.detect("backup.tar.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.detect("backup.tgz"))
        assertEquals(ArchiveFormat.TAR, ArchiveFormat.detect("backup.tar"))
        assertEquals(ArchiveFormat.SEVENZ, ArchiveFormat.detect("data.7z"))
        assertEquals(null, ArchiveFormat.detect("data.rar"))
        assertEquals(null, ArchiveFormat.detect("notes.txt"))
    }

    @Test
    fun `zip round trip keeps nested paths and contents`() {
        val zipBytes = ByteArrayOutputStream().also { out ->
            engine.createZip(
                listOf(
                    ArchiveEntrySource("hello.txt", ByteArrayInputStream("hello".toByteArray())),
                    ArchiveEntrySource("docs/readme.md", ByteArrayInputStream("# readme".toByteArray()))
                ),
                out, totalInputBytes = 13
            )
        }.toByteArray()

        val listed = engine.listEntries(ByteArrayInputStream(zipBytes), ArchiveFormat.ZIP)
        assertEquals(listOf("hello.txt", "docs/readme.md"), listed.map { it.name })

        val dir = createTempDir("archive-test").apply { mkdirs() }
        try {
            val extracted = engine.extract(ByteArrayInputStream(zipBytes), ArchiveFormat.ZIP, dir, zipBytes.size.toLong())
            assertEquals(2, extracted.size)
            assertEquals("hello", File(dir, "hello.txt").readText())
            assertEquals("# readme", File(dir, "docs/readme.md").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `tar gz round trip`() {
        val bytes = ByteArrayOutputStream().also { out ->
            engine.createTarGz(
                listOf(
                    ArchiveEngine.SizedArchiveEntrySource("a.txt", ByteArrayInputStream("aaa".toByteArray()), 3)
                ),
                out, totalInputBytes = 3
            )
        }.toByteArray()
        val dir = createTempDir("archive-test").apply { mkdirs() }
        try {
            val extracted = engine.extract(ByteArrayInputStream(bytes), ArchiveFormat.TAR_GZ, dir, bytes.size.toLong())
            assertEquals(1, extracted.size)
            assertEquals("aaa", File(dir, "a.txt").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `duplicate names in a created zip get a suffix`() {
        val bytes = ByteArrayOutputStream().also { out ->
            engine.createZip(
                listOf(
                    ArchiveEntrySource("photo.jpg", ByteArrayInputStream("1".toByteArray())),
                    ArchiveEntrySource("photo.jpg", ByteArrayInputStream("2".toByteArray()))
                ),
                out, totalInputBytes = 2
            )
        }.toByteArray()
        val listed = engine.listEntries(ByteArrayInputStream(bytes), ArchiveFormat.ZIP)
        assertEquals(listOf("photo.jpg", "photo (2).jpg"), listed.map { it.name })
    }

    @Test
    fun `zip slip entry is rejected`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipArchiveOutputStream(out).use { zip ->
                zip.putArchiveEntry(ZipArchiveEntry("../evil.txt"))
                zip.write("evil".toByteArray())
                zip.closeArchiveEntry()
                zip.finish()
            }
        }.toByteArray()
        val testRoot = createTempDir("archive-test-root").apply { mkdirs() }
        val dir = File(testRoot, "output").apply { mkdirs() }
        try {
            assertThrows(ArchiveException.UnsafeEntry::class.java) {
                engine.extract(ByteArrayInputStream(bytes), ArchiveFormat.ZIP, dir, bytes.size.toLong())
            }
            assertTrue(!File(dir, "evil.txt").exists())
            assertTrue(!File(testRoot, "evil.txt").exists())
        } finally {
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun `uncompressed size cap is enforced`() {
        val smallEngine = ArchiveEngine(maxTotalUncompressedBytes = 10)
        val bytes = ByteArrayOutputStream().also { out ->
            smallEngine.createZip(
                listOf(ArchiveEntrySource("big.txt", ByteArrayInputStream("0123456789ABCDEF".toByteArray()))),
                out, totalInputBytes = 16
            )
        }.toByteArray()
        val dir = createTempDir("archive-test").apply { mkdirs() }
        try {
            assertThrows(ArchiveException.TooLarge::class.java) {
                smallEngine.extract(ByteArrayInputStream(bytes), ArchiveFormat.ZIP, dir, bytes.size.toLong())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `empty archive is rejected`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipArchiveOutputStream(out).use { it.finish() }
        }.toByteArray()
        assertThrows(ArchiveException.EmptyArchive::class.java) {
            engine.listEntries(ByteArrayInputStream(bytes), ArchiveFormat.ZIP)
        }
    }

    @Test
    fun `plain tar extract`() {
        val bytes = ByteArrayOutputStream().also { out ->
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(out).use { tar ->
                val data = "plain".toByteArray()
                val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("plain.txt")
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
                tar.finish()
            }
        }.toByteArray()
        val listed = engine.listEntries(ByteArrayInputStream(bytes), ArchiveFormat.TAR)
        assertEquals(listOf("plain.txt"), listed.map { it.name })
        val dir = createTempDir("archive-test").apply { mkdirs() }
        try {
            val extracted = engine.extract(ByteArrayInputStream(bytes), ArchiveFormat.TAR, dir, bytes.size.toLong())
            assertEquals(1, extracted.size)
            assertEquals("plain", File(dir, "plain.txt").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `created zip carries entry sizes in its local headers`() {
        // The preview reads through a streaming reader, which only sees
        // local headers: without setSize() every entry listed as 0 B.
        val zipBytes = ByteArrayOutputStream().also { out ->
            engine.createZip(
                listOf(
                    ArchiveEngine.SizedArchiveEntrySource(
                        "hello.txt", ByteArrayInputStream("hello".toByteArray()), 5
                    ),
                    ArchiveEngine.SizedArchiveEntrySource(
                        "docs/readme.md", ByteArrayInputStream("# readme".toByteArray()), 8
                    )
                ),
                out, totalInputBytes = 13
            )
        }.toByteArray()

        val listed = engine.listEntries(ByteArrayInputStream(zipBytes), ArchiveFormat.ZIP)
        assertEquals(listOf(5L, 8L), listed.map { it.sizeBytes })
    }

    @Test
    fun `zip end record is found in a valid tail and missed in a truncated one`() {
        // Minimal End of Central Directory record: signature + 18 zero bytes.
        val validTail = byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18)
        assertTrue(ArchiveEngine.hasZipEndRecord(validTail))
        // Junk after the record doesn't hide it (trailing data, ZIP64 locator).
        assertTrue(ArchiveEngine.hasZipEndRecord(validTail + byteArrayOf(1, 2, 3)))
        // A tail cut before the record — the truncated case — has no signature.
        assertFalse(ArchiveEngine.hasZipEndRecord(ByteArray(64) { 0x41 }))
        assertFalse(ArchiveEngine.hasZipEndRecord(ByteArray(0)))
    }

    @Test
    fun `sevenz list and extract round trip`() {
        val sevenZFile = File.createTempFile("archive-test", ".7z")
        SevenZOutputFile(sevenZFile).use { out ->
            val data = "seven".toByteArray()
            val entry = SevenZArchiveEntry()
            entry.name = "seven.txt"
            entry.size = data.size.toLong()
            out.putArchiveEntry(entry)
            out.write(data)
            out.closeArchiveEntry()
        }
        val dir = createTempDir("archive-test").apply { mkdirs() }
        try {
            val listed = engine.listEntriesFromFile(sevenZFile)
            assertEquals(listOf("seven.txt"), listed.map { it.name })
            val extracted = engine.extractFromFile(sevenZFile, dir)
            assertEquals(1, extracted.size)
            assertEquals("seven", File(dir, "seven.txt").readText())
        } finally {
            dir.deleteRecursively()
            sevenZFile.delete()
        }
    }
}
