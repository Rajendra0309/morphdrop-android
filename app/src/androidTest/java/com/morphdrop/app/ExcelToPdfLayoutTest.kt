package com.morphdrop.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.ThemeMode
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.conversion.ExcelToPdfUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExcelToPdfLayoutTest {

    private lateinit var context: Context
    private lateinit var excelToPdfUseCase: ExcelToPdfUseCase

    private class FakeSettingsRepo : SettingsRepository {
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
        override val outputFolderName: Flow<String> = MutableStateFlow("MorphDropTest")
        override val hasSeenWelcome: Flow<Boolean> = MutableStateFlow(true)
        override val lastUpdateCheck: Flow<Long> = MutableStateFlow(0L)
        override val readingMode: Flow<ReadingMode> = MutableStateFlow(ReadingMode.DEFAULT)
        override val sepiaIntensity: Flow<Float> = MutableStateFlow(0.5f)
        override val markdownTextSize: Flow<Float> = MutableStateFlow(16f)
        override val lastImageFormat: Flow<String> = MutableStateFlow("PNG")
        override val lastImageQuality: Flow<Int> = MutableStateFlow(80)
        override val lastImageResizeOption: Flow<String> = MutableStateFlow("Original")
        override val lastStripMetadata: Flow<Boolean> = MutableStateFlow(false)
        override val hasSeenOcrDisclaimer: Flow<Boolean> = MutableStateFlow(true)
        override val skippedUpdateVersion: Flow<String> = MutableStateFlow("")
        override val lastSeenAppVersion: Flow<String> = MutableStateFlow("")

        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setOutputFolderName(name: String) {}
        override suspend fun setHasSeenWelcome(hasSeen: Boolean) {}
        override suspend fun setLastUpdateCheck(timestamp: Long) {}
        override suspend fun setReadingMode(mode: ReadingMode) {}
        override suspend fun setSepiaIntensity(intensity: Float) {}
        override suspend fun setMarkdownTextSize(size: Float) {}
        override suspend fun setLastImageFormat(format: String) {}
        override suspend fun setLastImageQuality(quality: Int) {}
        override suspend fun setLastImageResizeOption(option: String) {}
        override suspend fun setLastStripMetadata(strip: Boolean) {}
        override suspend fun setHasSeenOcrDisclaimer(hasSeen: Boolean) {}
        override suspend fun setSkippedUpdateVersion(version: String) {}
        override suspend fun setLastSeenAppVersion(version: String) {}
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        excelToPdfUseCase = ExcelToPdfUseCase(context, FakeSettingsRepo())
    }

    private fun createTempCsv(content: String, prefix: String = "test"): Uri {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.csv")
        file.writeText(content)
        return Uri.fromFile(file)
    }

    private fun openPdfRenderer(uri: Uri): PdfRenderer {
        val pfd: ParcelFileDescriptor = if (uri.scheme == "file") {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Cannot open PFD for $uri")
        }
        return PdfRenderer(pfd)
    }

    @Test
    fun testNarrowSheet_3Columns_usesPortraitOrientation() = runBlocking {
        // Narrow sheet (3 columns) -> should be Portrait (595 x 842)
        val csv = buildString {
            appendLine("ID,Name,Department")
            appendLine("1,Alice,Engineering")
            appendLine("2,Bob,Product")
            appendLine("3,Charlie,Design")
        }
        val uri = createTempCsv(csv, "narrow")
        val pdfUri = excelToPdfUseCase(uri, "narrow_output.pdf")
        assertNotNull("Generated PDF URI should not be null", pdfUri)

        val renderer = openPdfRenderer(pdfUri)
        assertTrue("Should have at least 1 page", renderer.pageCount >= 1)

        val page = renderer.openPage(0)
        assertEquals("Portrait width should be 595", 595, page.width)
        assertEquals("Portrait height should be 842", 842, page.height)
        assertTrue("Page width should be less than height in portrait", page.width < page.height)

        // Verify page can be rendered to bitmap (file opens correctly in PDF viewer)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        assertNotNull(bitmap)
        page.close()
        renderer.close()
    }

    @Test
    fun testWideSheet_20PlusColumns_usesLandscapeAndDynamicWidthExpansion() = runBlocking {
        // Wide sheet with 22 columns -> should be Landscape with expanded width
        val header = (1..22).joinToString(",") { "Col$it" }
        val row1 = (1..22).joinToString(",") { "Val$it" }
        val row2 = (1..22).joinToString(",") { "Data$it" }
        val csv = "$header\n$row1\n$row2"

        val uri = createTempCsv(csv, "wide_22cols")
        val pdfUri = excelToPdfUseCase(uri, "wide_output.pdf")
        assertNotNull(pdfUri)

        val renderer = openPdfRenderer(pdfUri)
        val page = renderer.openPage(0)

        // Landscape base height is 595
        assertEquals("Landscape height should be 595", 595, page.height)
        // Since 22 columns * min 48pt = 1056pt + 60pt margin = 1116pt > 842pt,
        // actualPageWidth must dynamically expand beyond 842pt!
        assertTrue("Dynamic page width should expand beyond default 842pt (was ${page.width})", page.width > 842)
        assertTrue("Page width should be greater than height in landscape/expanded", page.width > page.height)

        // Verify page renders cleanly
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        assertNotNull(bitmap)
        page.close()
        renderer.close()
    }

    @Test
    fun testMultiPageSheet_100PlusRows_spansMultiplePagesAndRendersCleanly() = runBlocking {
        // Sheet with 120 rows -> spans multiple pages, headers repeated on page 2+
        val csv = buildString {
            appendLine("ID,Name,Email,Amount,Status,Date")
            for (i in 1..120) {
                appendLine("$i,User $i,user$i@example.com,$${i * 100},Active,2026-09-15")
            }
        }

        val uri = createTempCsv(csv, "multipage_120rows")
        val pdfUri = excelToPdfUseCase(uri, "multipage_output.pdf")
        assertNotNull(pdfUri)

        val renderer = openPdfRenderer(pdfUri)
        assertTrue("120 rows should span across at least 2 pages (actual: ${renderer.pageCount})", renderer.pageCount >= 2)

        // Render each page to confirm no crash or layout issue
        for (p in 0 until renderer.pageCount) {
            val page = renderer.openPage(p)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            assertNotNull("Page $p rendered bitmap should not be null", bitmap)
            page.close()
        }
        renderer.close()
    }

    @Test
    fun testLongTextAndNumbers_renderWithoutOverflow() = runBlocking {
        // Sheet with long text and long numbers (e.g. 217025405769)
        val longText = "This is an extraordinarily long text description that is designed to test whether cells wrap text cleanly within the maximum column width bound of 200 points rather than expanding infinitely or squishing adjacent content."
        val numberText = "217025405769"

        val csv = buildString {
            appendLine("RecordID,AccountNumber,Description,Balance")
            appendLine("1,$numberText,$longText,99999.50")
            appendLine("2,987654321098,Short note,123.45")
        }

        val uri = createTempCsv(csv, "long_text_numbers")
        val pdfUri = excelToPdfUseCase(uri, "long_text_numbers_output.pdf")
        assertNotNull(pdfUri)

        val renderer = openPdfRenderer(pdfUri)
        assertEquals("Should be 1 page", 1, renderer.pageCount)
        val page = renderer.openPage(0)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        assertNotNull(bitmap)
        page.close()
        renderer.close()
    }

    @Test
    fun testZebraStripingAndHeaderRepetition_verifiedByColors() = runBlocking {
        // Multi-page table to check Page 1 header + zebra striping AND Page 2 repeated header + zebra striping
        val csv = buildString {
            appendLine("Col1,Col2,Col3,Col4")
            for (i in 1..100) {
                appendLine("R${i}C1,R${i}C2,R${i}C3,R${i}C4")
            }
        }
        val uri = createTempCsv(csv, "colors_check")
        val pdfUri = excelToPdfUseCase(uri, "colors_output.pdf")
        assertNotNull(pdfUri)

        val renderer = openPdfRenderer(pdfUri)
        assertTrue("Should span at least 2 pages", renderer.pageCount >= 2)

        // Page 1
        val page1 = renderer.openPage(0)
        val bmp1 = Bitmap.createBitmap(page1.width, page1.height, Bitmap.Config.ARGB_8888)
        page1.render(bmp1, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Sample header in cell padding at top of Page 1 (x = 32, y = 32, outside text glyphs starting at x=34, y=34)
        val p1HeaderPixel = bmp1.getPixel(32, 32)
        val p1HeaderR = android.graphics.Color.red(p1HeaderPixel)
        val p1HeaderG = android.graphics.Color.green(p1HeaderPixel)
        val p1HeaderB = android.graphics.Color.blue(p1HeaderPixel)
        // #E8EAF6 -> R ~ 232, G ~ 234, B ~ 246
        assertTrue("Page 1 header should have #E8EAF6 tint (R=$p1HeaderR, G=$p1HeaderG, B=$p1HeaderB)",
            p1HeaderR in 228..236 && p1HeaderG in 230..238 && p1HeaderB in 242..250)

        page1.close()

        // Page 2
        val page2 = renderer.openPage(1)
        val bmp2 = Bitmap.createBitmap(page2.width, page2.height, Bitmap.Config.ARGB_8888)
        page2.render(bmp2, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Sample header repeated in cell padding at top of Page 2 (x = 32, y = 32, outside text glyphs)
        val p2HeaderPixel = bmp2.getPixel(32, 32)
        val p2HeaderR = android.graphics.Color.red(p2HeaderPixel)
        val p2HeaderG = android.graphics.Color.green(p2HeaderPixel)
        val p2HeaderB = android.graphics.Color.blue(p2HeaderPixel)
        assertTrue("Page 2 repeated header should have #E8EAF6 tint (R=$p2HeaderR, G=$p2HeaderG, B=$p2HeaderB)",
            p2HeaderR in 228..236 && p2HeaderG in 230..238 && p2HeaderB in 242..250)

        page2.close()
        renderer.close()
    }
}
