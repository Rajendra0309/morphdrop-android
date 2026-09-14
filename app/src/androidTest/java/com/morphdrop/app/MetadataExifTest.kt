package com.morphdrop.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morphdrop.app.domain.model.ReadingMode
import com.morphdrop.app.domain.model.ThemeMode
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.domain.usecase.conversion.MetadataUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class MetadataExifTest {

    private lateinit var context: Context
    private lateinit var metadataUseCase: MetadataUseCase

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
        metadataUseCase = MetadataUseCase(FakeSettingsRepo())
    }

    @Test
    fun testImageMetadataInspection_extractsCameraAndGpsDetails(): Unit = runBlocking {
        // Create sample JPEG with Xiaomi 12 Pro camera and EXIF data
        val testFile = File(context.cacheDir, "test_camera_photo.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(testFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Write EXIF tags into the JPEG
        val exif = ExifInterface(testFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "Xiaomi")
        exif.setAttribute(ExifInterface.TAG_MODEL, "Xiaomi 12 Pro")
        exif.setAttribute(ExifInterface.TAG_F_NUMBER, "1.9")
        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.021276") // ~1/47s
        exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, "299")
        exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "7.10/1")
        exif.setAttribute(ExifInterface.TAG_FLASH, "0")
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:11 12:46:28")
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "12/1,58/1,23/1")
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "77/1,35/1,45/1")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
        exif.saveAttributes()

        // Inspect metadata
        val metadata = metadataUseCase.inspectMetadata(context, Uri.fromFile(testFile))

        // Assert all camera and hardware details are correctly extracted
        assertTrue("hasMetadata must be true", metadata.hasMetadata)
        assertEquals("Make should be Xiaomi", "Xiaomi", metadata.cameraMake)
        assertEquals("Model should be Xiaomi 12 Pro", "Xiaomi 12 Pro", metadata.cameraModel)
        assertEquals("Aperture should be f/1.9", "f/1.9", metadata.fNumber)
        assertEquals("Exposure should be 1/47s", "1/47s", metadata.exposureTime)
        assertEquals("ISO should be ISO 299", "ISO 299", metadata.isoSpeed)
        assertEquals("Flash should be No flash", "No flash", metadata.flash)
        assertEquals("Date should match", "2026:09:11 12:46:28", metadata.dateOriginal)
        assertNotNull("Latitude should not be null", metadata.latitude)
        assertNotNull("Longitude should not be null", metadata.longitude)

        // Cleanup
        testFile.delete()
    }
}
