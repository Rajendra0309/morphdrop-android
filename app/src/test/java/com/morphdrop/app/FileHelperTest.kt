package com.morphdrop.app

import com.morphdrop.app.util.FileHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class FileHelperTest {

    @Test
    fun `display format keeps compound tar gz extension`() {
        assertEquals("tar.gz", FileHelper.displayFormatOfFileName("packed.tar.gz"))
        assertEquals("tar.gz", FileHelper.displayFormatOfFileName("PACKED.TAR.GZ"))
        assertEquals("tar.gz", FileHelper.displayFormatOfFileName("archive.tgz"))
    }

    @Test
    fun `display format falls back to the plain extension`() {
        assertEquals("zip", FileHelper.displayFormatOfFileName("packed.zip"))
        // Case is preserved by the helper; callers lowercase it.
        assertEquals("PDF", FileHelper.displayFormatOfFileName("doc.PDF"))
        assertEquals("", FileHelper.displayFormatOfFileName("noext"))
    }

    @Test
    fun `strip archive extension removes compound extensions whole`() {
        assertEquals("packed", FileHelper.stripArchiveExtension("packed.tar.gz"))
        assertEquals("packed", FileHelper.stripArchiveExtension("packed.tgz"))
        assertEquals("doc", FileHelper.stripArchiveExtension("doc.pdf"))
        assertEquals("noext", FileHelper.stripArchiveExtension("noext"))
    }
}
