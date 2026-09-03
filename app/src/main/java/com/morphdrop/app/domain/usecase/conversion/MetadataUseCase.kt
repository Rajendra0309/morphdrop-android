package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.morphdrop.app.domain.model.FileMetadata
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.model.MetadataEditParams
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    suspend fun inspectMetadata(context: Context, uri: Uri): FileMetadata = withContext(Dispatchers.IO) {
        val fileName = FileHelper.getFileName(context, uri)
        val fileSize = FileHelper.getFileSize(context, uri)
        val fileSizeFormatted = FileHelper.formatFileSize(fileSize)
        val mimeType = FileHelper.getMimeType(context, uri)
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        
        val matchingFileType = FileType.entries.firstOrNull { 
            it.extension.equals(extension, ignoreCase = true) 
        }

        // Calculate file hash (SHA-256 preview)
        val fileHash = calculateFileHash(context, uri)

        val isImage = mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "webp", "tiff", "tif", "heic", "heif", "dng", "bmp")
        val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || extension == "pdf"
        val isMedia = mimeType.startsWith("video/") || mimeType.startsWith("audio/") || extension in listOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "m4v", "mp3", "flac", "wav", "aac", "ogg")

        if (isImage) {
            inspectImageMetadata(
                context = context,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                fileSizeFormatted = fileSizeFormatted,
                mimeType = mimeType,
                extension = extension,
                fileType = matchingFileType,
                fileHash = fileHash
            )
        } else if (isPdf) {
            inspectPdfMetadata(
                context = context,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                fileSizeFormatted = fileSizeFormatted,
                mimeType = mimeType,
                extension = extension,
                fileType = matchingFileType,
                fileHash = fileHash
            )
        } else if (isMedia) {
            inspectMediaMetadata(
                context = context,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                fileSizeFormatted = fileSizeFormatted,
                mimeType = mimeType,
                extension = extension,
                fileType = matchingFileType,
                fileHash = fileHash
            )
        } else {
            // Generic file inspection
            FileMetadata(
                fileName = fileName,
                fileSize = fileSize,
                fileSizeFormatted = fileSizeFormatted,
                mimeType = mimeType,
                fileExtension = extension,
                fileType = matchingFileType,
                fileHash = fileHash,
                hasMetadata = false,
                isScrubbable = false,
                isEditable = false
            )
        }
    }

    private fun inspectImageMetadata(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        fileSizeFormatted: String,
        mimeType: String,
        extension: String,
        fileType: FileType?,
        fileHash: String?
    ): FileMetadata {
        var cameraMake: String? = null
        var cameraModel: String? = null
        var software: String? = null
        var lensModel: String? = null
        var focalLength: String? = null
        var fNumber: String? = null
        var isoSpeed: String? = null
        var exposureTime: String? = null
        var flash: String? = null
        var dateOriginal: String? = null
        var dateDigitized: String? = null
        var dateCreated: String? = null
        var artist: String? = null
        var description: String? = null
        var copyright: String? = null
        var userComment: String? = null
        var dpi: String? = null
        var lat: Double? = null
        var lng: Double? = null
        var altitude: Double? = null
        var locationAddress: String? = null

        val realPath = getRealFilePathFromUri(context, uri)

        var originalUri = uri
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && uri.scheme == "content") {
            try {
                if (android.provider.DocumentsContract.isDocumentUri(context, uri) && uri.authority == "com.android.providers.media.documents") {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val contentUri = when (type) {
                            "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        }
                        if (contentUri != null) {
                            originalUri = android.content.ContentUris.withAppendedId(contentUri, split[1].toLong())
                        }
                    }
                }
                originalUri = android.provider.MediaStore.setRequireOriginal(originalUri)
            } catch (_: Exception) {
                // If setRequireOriginal fails (e.g. no location permission), originalUri remains the last parsed URI
            }
        }

        var tempExifFile: File? = null
        try {
            val exif = try {
                if (!realPath.isNullOrBlank()) {
                    ExifInterface(realPath)
                } else {
                    throw Exception("No real path")
                }
            } catch (e: Exception) {
                try {
                    context.contentResolver.openFileDescriptor(originalUri, "r")?.use { pfd ->
                        ExifInterface(pfd.fileDescriptor)
                    } ?: throw Exception("Null file descriptor")
                } catch (e2: Exception) {
                    try {
                        FileHelper.readFileFromUri(context, originalUri).use { inputStream ->
                            ExifInterface(inputStream)
                        }
                    } catch (e3: Exception) {
                        // Fallback to temp file if stream reading fails for EXIF
                        val ext = "." + (FileHelper.getFileName(context, originalUri).substringAfterLast('.', "jpg"))
                        tempExifFile = File.createTempFile("exif_temp_", ext, context.cacheDir)
                        context.contentResolver.openInputStream(originalUri)?.use { input ->
                            tempExifFile!!.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        ExifInterface(tempExifFile!!.absolutePath)
                    }
                }
            }

            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() }
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
            software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()?.takeIf { it.isNotEmpty() }
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
            
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { 
                val mm = parseRational(it)
                if (mm != null) String.format(Locale.US, "%.2f mm", mm) else "$it mm"
            }
            fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { 
                val f = parseRational(it)
                if (f != null) String.format(Locale.US, "f/%.1f", f) else "f/$it"
            }
            @Suppress("DEPRECATION")
            isoSpeed = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { "ISO $it" }
            exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { 
                val sec = parseRational(it)
                if (sec != null && sec < 1.0 && sec > 0.0) "1/${(1.0 / sec).toInt()}s" else "$it s"
            }
            flash = exif.getAttribute(ExifInterface.TAG_FLASH)?.toIntOrNull()?.let { 
                if (it % 2 == 1) "Flash fired" else "No flash"
            }

            dateOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.trim()?.takeIf { it.isNotEmpty() }
            dateDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)?.trim()?.takeIf { it.isNotEmpty() }
            dateCreated = dateOriginal ?: exif.getAttribute(ExifInterface.TAG_DATETIME)?.trim()?.takeIf { it.isNotEmpty() }

            artist = exif.getAttribute(ExifInterface.TAG_ARTIST)?.trim()?.takeIf { it.isNotEmpty() }
            description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.trim()?.takeIf { it.isNotEmpty() }
            copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT)?.trim()?.takeIf { it.isNotEmpty() }
            userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim()?.takeIf { it.isNotEmpty() }

            val xRes = exif.getAttribute(ExifInterface.TAG_X_RESOLUTION)
            val yRes = exif.getAttribute(ExifInterface.TAG_Y_RESOLUTION)
            if (xRes != null && yRes != null && xRes.isNotEmpty() && yRes.isNotEmpty()) {
                dpi = "${xRes.substringBefore('/')} x ${yRes.substringBefore('/')} DPI"
            }

            val latLngArray = FloatArray(2)
            if (exif.getLatLong(latLngArray)) {
                lat = latLngArray[0].toDouble()
                lng = latLngArray[1].toDouble()
            } else {
                exif.latLong?.let {
                    lat = it[0].toDouble()
                    lng = it[1].toDouble()
                }
            }

            if (lat == null || lng == null) {
                val latDms = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val lngDms = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                val lngRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

                if (lat == null) lat = parseDmsToDecimal(latDms, latRef)
                if (lng == null) lng = parseDmsToDecimal(lngDms, lngRef)
            }

            if (exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null) {
                altitude = exif.getAltitude(0.0)
            }
        } catch (_: Exception) {
            // Ignore EXIF parsing errors gracefully
        } finally {
            try { tempExifFile?.delete() } catch (_: Exception) {}
        }

        // MediaStore Query Fallback for recently taken photos if EXIF stream attributes were redacted
        if ((lat == null || lng == null || dateCreated == null) && uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    originalUri,
                    arrayOf(
                        android.provider.MediaStore.Images.ImageColumns.LATITUDE,
                        android.provider.MediaStore.Images.ImageColumns.LONGITUDE,
                        android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val latIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.ImageColumns.LATITUDE)
                        val lngIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.ImageColumns.LONGITUDE)
                        val dateIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN)

                        if (lat == null && latIdx >= 0 && !cursor.isNull(latIdx)) {
                            val latVal = cursor.getDouble(latIdx)
                            if (latVal != 0.0) lat = latVal
                        }
                        if (lng == null && lngIdx >= 0 && !cursor.isNull(lngIdx)) {
                            val lngVal = cursor.getDouble(lngIdx)
                            if (lngVal != 0.0) lng = lngVal
                        }
                        if (dateCreated == null && dateIdx >= 0 && !cursor.isNull(dateIdx)) {
                            val dateVal = cursor.getLong(dateIdx)
                            if (dateVal > 0) {
                                dateCreated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(dateVal))
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (lat != null && lng != null) {
            locationAddress = geocodeLocationAddress(context, lat!!, lng!!)
        }

        // Image dimensions
        var width: Int? = null
        var height: Int? = null
        var colorSpace: String? = null
        try {
            FileHelper.readFileFromUri(context, uri).use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    width = options.outWidth
                    height = options.outHeight
                    colorSpace = options.outColorSpace?.name ?: "sRGB"
                }
            }
        } catch (_: Exception) {}

        val hasMetadata = listOfNotNull(
            cameraMake, cameraModel, software, lensModel,
            focalLength, fNumber, isoSpeed, exposureTime, flash,
            lat, lng, dateCreated, dateOriginal, artist, description, copyright, userComment
        ).isNotEmpty()

        return FileMetadata(
            fileName = fileName,
            fileSize = fileSize,
            fileSizeFormatted = fileSizeFormatted,
            mimeType = mimeType,
            fileExtension = extension,
            fileType = fileType,
            fileHash = fileHash,
            cameraMake = cameraMake,
            cameraModel = cameraModel,
            software = software,
            lensModel = lensModel,
            focalLength = focalLength,
            fNumber = fNumber,
            isoSpeed = isoSpeed,
            exposureTime = exposureTime,
            flash = flash,
            latitude = lat,
            longitude = lng,
            gpsAltitude = altitude,
            locationAddress = locationAddress,
            dateCreated = dateCreated,
            dateOriginal = dateOriginal,
            dateDigitized = dateDigitized,
            author = artist,
            title = description,
            subject = userComment,
            copyright = copyright,
            dpi = dpi,
            width = width,
            height = height,
            colorSpace = colorSpace,
            hasMetadata = hasMetadata,
            isScrubbable = true,
            isEditable = true
        )
    }

    private fun inspectPdfMetadata(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        fileSizeFormatted: String,
        mimeType: String,
        extension: String,
        fileType: FileType?,
        fileHash: String?
    ): FileMetadata {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }

        var author: String? = null
        var title: String? = null
        var subject: String? = null
        var creator: String? = null
        var producer: String? = null
        var keywords: String? = null
        var dateCreated: String? = null
        var dateModified: String? = null
        var pageCount: Int? = null
        var width: Int? = null
        var height: Int? = null

        try {
            FileHelper.readFileFromUri(context, uri).use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val pages = document.numberOfPages
                    pageCount = pages
                    if (pages > 0) {
                        val firstPage = document.getPage(0)
                        width = firstPage.mediaBox.width.toInt()
                        height = firstPage.mediaBox.height.toInt()
                    }

                    val info: PDDocumentInformation? = document.documentInformation
                    if (info != null) {
                        author = info.author?.trim()?.takeIf { it.isNotEmpty() }
                        title = info.title?.trim()?.takeIf { it.isNotEmpty() }
                        subject = info.subject?.trim()?.takeIf { it.isNotEmpty() }
                        creator = info.creator?.trim()?.takeIf { it.isNotEmpty() }
                        producer = info.producer?.trim()?.takeIf { it.isNotEmpty() }
                        keywords = info.keywords?.trim()?.takeIf { it.isNotEmpty() }

                        info.creationDate?.let {
                            dateCreated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it.time)
                        }
                        info.modificationDate?.let {
                            dateModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it.time)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val hasMetadata = listOfNotNull(
            author, title, subject, creator, producer, keywords, dateCreated
        ).isNotEmpty()

        return FileMetadata(
            fileName = fileName,
            fileSize = fileSize,
            fileSizeFormatted = fileSizeFormatted,
            mimeType = mimeType,
            fileExtension = extension,
            fileType = fileType,
            fileHash = fileHash,
            author = author,
            title = title,
            subject = subject,
            creator = creator,
            producer = producer,
            keywords = keywords,
            dateCreated = dateCreated,
            dateModified = dateModified,
            pageCount = pageCount,
            width = width,
            height = height,
            hasMetadata = hasMetadata,
            isScrubbable = true,
            isEditable = true
        )
    }

    private fun inspectMediaMetadata(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        fileSizeFormatted: String,
        mimeType: String,
        extension: String,
        fileType: FileType?,
        fileHash: String?
    ): FileMetadata {
        val retriever = MediaMetadataRetriever()
        var durationFormatted: String? = null
        var bitrateFormatted: String? = null
        var width: Int? = null
        var height: Int? = null
        var dateCreated: String? = null
        var author: String? = null
        var title: String? = null
        var lat: Double? = null
        var lng: Double? = null

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)

                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (durationMs != null && durationMs > 0) {
                    val sec = (durationMs / 1000) % 60
                    val min = (durationMs / (1000 * 60)) % 60
                    val hrs = (durationMs / (1000 * 60 * 60))
                    durationFormatted = if (hrs > 0) {
                        String.format(Locale.US, "%d:%02d:%02d", hrs, min, sec)
                    } else {
                        String.format(Locale.US, "%d:%02d", min, sec)
                    }
                }

                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                bitrateFormatted = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { "${it / 1000} kbps" }

                dateCreated = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.trim()
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?.trim()
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()

                val locationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.trim()
                if (!locationStr.isNullOrEmpty()) {
                    parseIso6709Location(locationStr)?.let { (latitude, longitude) ->
                        lat = latitude
                        lng = longitude
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val hasMetadata = listOfNotNull(
            durationFormatted, bitrateFormatted, width, height, dateCreated, author, title, lat, lng
        ).isNotEmpty()

        return FileMetadata(
            fileName = fileName,
            fileSize = fileSize,
            fileSizeFormatted = fileSizeFormatted,
            mimeType = mimeType,
            fileExtension = extension,
            fileType = fileType,
            fileHash = fileHash,
            duration = durationFormatted,
            bitrate = bitrateFormatted,
            width = width,
            height = height,
            latitude = lat,
            longitude = lng,
            dateCreated = dateCreated,
            author = author,
            title = title,
            hasMetadata = hasMetadata,
            isScrubbable = false,
            isEditable = false
        )
    }

    suspend fun scrubMetadataAndSave(context: Context, inputUri: Uri, outputFileName: String): Uri = withContext(Dispatchers.IO) {
        val tempCacheUri = scrubMetadata(context, inputUri, "temp_scrubbed_${System.currentTimeMillis()}_$outputFileName")
        val file = File(tempCacheUri.path!!)
        val bytes = file.readBytes()
        val savedUri = FileHelper.saveToFile(context, settingsRepository, outputFileName, bytes)
        try { file.delete() } catch (_: Exception) {}
        savedUri
    }

    suspend fun editMetadataAndSave(
        context: Context,
        inputUri: Uri,
        editParams: MetadataEditParams,
        outputFileName: String
    ): Uri = withContext(Dispatchers.IO) {
        val tempCacheUri = editMetadata(context, inputUri, editParams, "temp_edited_${System.currentTimeMillis()}_$outputFileName")
        val file = File(tempCacheUri.path!!)
        val bytes = file.readBytes()
        val savedUri = FileHelper.saveToFile(context, settingsRepository, outputFileName, bytes)
        try { file.delete() } catch (_: Exception) {}
        savedUri
    }

    suspend fun scrubMetadata(context: Context, inputUri: Uri, outputFileName: String): Uri = withContext(Dispatchers.IO) {
        val mimeType = FileHelper.getMimeType(context, inputUri)
        val extension = FileHelper.getFileName(context, inputUri).substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || extension == "pdf"

        val tempFile = File(context.cacheDir, outputFileName)
        if (tempFile.exists()) tempFile.delete()

        if (isPdf) {
            scrubPdfMetadata(context, inputUri, tempFile)
        } else {
            scrubImageMetadata(context, inputUri, tempFile)
        }

        Uri.fromFile(tempFile)
    }

    private fun scrubImageMetadata(context: Context, inputUri: Uri, outputFile: File) {
        // 1. Copy source to outputFile
        FileHelper.readFileFromUri(context, inputUri).use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }

        // 2. Clear EXIF attributes using ExifInterface
        try {
            val exif = ExifInterface(outputFile.absolutePath)
            val tagsToClear = listOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_LENS_MAKE,
                ExifInterface.TAG_LENS_MODEL,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_USER_COMMENT,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_PROCESSING_METHOD
            )
            tagsToClear.forEach { tag ->
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
        } catch (_: Exception) {}

        // 3. Decode and re-compress bitmap to guarantee 100% removal of unhandled metadata chunks
        try {
            var bitmap: Bitmap? = null
            FileHelper.readFileFromUri(context, inputUri).use { input ->
                bitmap = BitmapFactory.decodeStream(input)
            }
            if (bitmap != null) {
                val format = when (outputFile.extension.lowercase(Locale.ROOT)) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                FileOutputStream(outputFile).use { out ->
                    bitmap!!.compress(format, 95, out)
                }
                bitmap!!.recycle()
            }
        } catch (_: Exception) {}
    }

    private fun scrubPdfMetadata(context: Context, inputUri: Uri, outputFile: File) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }

        FileHelper.readFileFromUri(context, inputUri).use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                document.documentInformation = PDDocumentInformation()
                try {
                    document.documentCatalog.metadata = null
                } catch (_: Exception) {}
                document.save(outputFile)
            }
        }
    }

    suspend fun editMetadata(
        context: Context,
        inputUri: Uri,
        editParams: MetadataEditParams,
        outputFileName: String
    ): Uri = withContext(Dispatchers.IO) {
        val mimeType = FileHelper.getMimeType(context, inputUri)
        val extension = FileHelper.getFileName(context, inputUri).substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || extension == "pdf"

        val tempFile = File(context.cacheDir, outputFileName)
        if (tempFile.exists()) tempFile.delete()

        // Copy source to tempFile first
        FileHelper.readFileFromUri(context, inputUri).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        if (isPdf) {
            editPdfMetadata(tempFile, editParams)
        } else {
            editImageMetadata(tempFile, editParams)
        }

        Uri.fromFile(tempFile)
    }

    private fun editImageMetadata(file: File, params: MetadataEditParams) {
        try {
            val exif = ExifInterface(file.absolutePath)

            if (!params.author.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_ARTIST, params.author.trim())
            }
            if (!params.title.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, params.title.trim())
            }
            if (!params.subject.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, params.subject.trim())
            }
            if (!params.software.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, params.software.trim())
            }
            if (!params.copyright.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_COPYRIGHT, params.copyright.trim())
            }
            if (!params.cameraMake.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_MAKE, params.cameraMake.trim())
            }
            if (!params.cameraModel.isNullOrEmpty()) {
                exif.setAttribute(ExifInterface.TAG_MODEL, params.cameraModel.trim())
            }
            if (!params.dateCreated.isNullOrEmpty()) {
                val formattedDate = formatExifDate(params.dateCreated)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
                exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
            }

            if (params.latitude != null && params.longitude != null) {
                exif.setLatLong(params.latitude, params.longitude)
                val latDms = convertDecimalToDMS(params.latitude)
                val lngDms = convertDecimalToDMS(params.longitude)
                val latRef = if (params.latitude >= 0) "N" else "S"
                val lngRef = if (params.longitude >= 0) "E" else "W"
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latDms)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lngDms)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
            }

            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun editPdfMetadata(file: File, params: MetadataEditParams) {
        try {
            PDDocument.load(file).use { document ->
                val info = document.documentInformation ?: PDDocumentInformation()
                
                if (!params.author.isNullOrEmpty()) {
                    info.author = params.author.trim()
                }
                if (!params.title.isNullOrEmpty()) {
                    info.title = params.title.trim()
                }
                if (!params.subject.isNullOrEmpty()) {
                    info.subject = params.subject.trim()
                }
                if (!params.software.isNullOrEmpty()) {
                    info.creator = params.software.trim()
                }
                if (!params.dateCreated.isNullOrEmpty()) {
                    parseDateToCalendar(params.dateCreated)?.let {
                        info.creationDate = it
                    }
                }

                document.documentInformation = info
                document.save(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseIso6709Location(location: String): Pair<Double, Double>? {
        return try {
            // ISO 6709 string format e.g. "+37.7510-122.4200/" or "+37.7510-122.4200+00.000/"
            val regex = """([+-]\d+\.\d+)([+-]\d+\.\d+)""".toRegex()
            val match = regex.find(location)
            if (match != null) {
                val lat = match.groupValues[1].toDoubleOrNull()
                val lng = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) Pair(lat, lng) else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDmsToDecimal(dms: String?, ref: String?): Double? {
        if (dms.isNullOrEmpty()) return null
        return try {
            val parts = dms.split(",")
            if (parts.size < 3) return null
            val d = parseRational(parts[0]) ?: return null
            val m = parseRational(parts[1]) ?: return null
            val s = parseRational(parts[2]) ?: return null
            var dec = d + (m / 60.0) + (s / 3600.0)
            if (ref.equals("S", ignoreCase = true) || ref.equals("W", ignoreCase = true)) {
                dec = -dec
            }
            dec
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRational(rationalStr: String): Double? {
        return try {
            val split = rationalStr.trim().split("/")
            if (split.size == 2) {
                split[0].toDouble() / split[1].toDouble()
            } else {
                rationalStr.trim().toDoubleOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getRealFilePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme == "content") {
            try {
                val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx >= 0) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrEmpty() && File(path).exists() && File(path).canRead()) {
                                return path
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun geocodeLocationAddress(context: Context, lat: Double, lng: Double): String? {
        return try {
            val geocoder = android.location.Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val feature = addr.featureName
                val thoroughfare = addr.thoroughfare
                val subLocality = addr.subLocality
                val locality = addr.locality
                val adminArea = addr.adminArea
                val postalCode = addr.postalCode
                val country = addr.countryName

                val parts = listOfNotNull(
                    thoroughfare ?: feature,
                    subLocality,
                    locality,
                    adminArea,
                    postalCode,
                    country
                ).filter { it.isNotBlank() }.distinct()

                if (parts.isNotEmpty()) parts.joinToString(", ") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateFileHash(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileHelper.readFileFromUri(context, uri).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalRead > 1_000_000) break // Limit hash calculation to first 1MB for speed
                }
            }
            digest.digest().take(8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatExifDate(input: String): String {
        return try {
            val clean = input.trim().replace('-', ':')
            if (clean.length == 10) {
                "$clean 12:00:00"
            } else {
                clean
            }
        } catch (_: Exception) {
            input
        }
    }

    private fun parseDateToCalendar(input: String): Calendar? {
        return try {
            val clean = input.trim().replace(':', '-')
            val sdf = if (clean.contains(" ")) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            }
            val date = sdf.parse(clean) ?: return null
            Calendar.getInstance().apply { time = date }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertDecimalToDMS(decimal: Double): String {
        val absVal = Math.abs(decimal)
        val degrees = absVal.toInt()
        val remainderMinutes = (absVal - degrees) * 60.0
        val minutes = remainderMinutes.toInt()
        val remainderSeconds = (remainderMinutes - minutes) * 60.0
        val secondsInt = (remainderSeconds * 1000.0).toInt().coerceAtLeast(0)
        return "$degrees/1,$minutes/1,$secondsInt/1000"
    }
}
