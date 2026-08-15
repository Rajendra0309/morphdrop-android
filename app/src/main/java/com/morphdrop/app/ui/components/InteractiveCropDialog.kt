package com.morphdrop.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.morphdrop.app.ui.utils.rememberHapticHelper
import com.morphdrop.app.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class HandleType {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    LEFT_CENTER, RIGHT_CENTER,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    INSIDE, NONE
}

private data class CropRatio(
    val label: String,
    val value: Float?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveCropDialog(
    imageUri: Uri,
    initialCropRect: android.graphics.Rect? = null,
    initialRotation: Int = 0,
    onDismiss: () -> Unit,
    onCropApplied: (left: Int, top: Int, right: Int, bottom: Int, rotation: Int) -> Unit
) {
    val context = LocalContext.current
    val hapticHelper = rememberHapticHelper()
    
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var imageBounds by remember { mutableStateOf(Rect.Zero) }
    var isInitialized by remember { mutableStateOf(false) }
    var currentRotation by remember { mutableIntStateOf(initialRotation) }
    var isDragging by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf(HandleType.NONE) }
    
    var selectedRatio by remember { mutableStateOf<Float?>(null) }

    val ratios = listOf(
        CropRatio("Free", null),
        CropRatio("1:1", 1f),
        CropRatio("4:3", 4f / 3f),
        CropRatio("16:9", 16f / 9f),
        CropRatio("9:16", 9f / 16f)
    )

    LaunchedEffect(imageUri, currentRotation) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Get EXIF Orientation
                var exifOrientation = ExifInterface.ORIENTATION_NORMAL
                FileHelper.readFileFromUri(context, imageUri).use {
                    val exif = ExifInterface(it)
                    exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                FileHelper.readFileFromUri(context, imageUri).use { 
                    BitmapFactory.decodeStream(it, null, options) 
                }
                
                val isSwappedByExif = (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270)
                val isSwappedByManual = (currentRotation % 180 != 0)
                val isSwappedTotal = isSwappedByExif xor isSwappedByManual

                originalSize = if (isSwappedTotal) {
                    IntSize(options.outHeight, options.outWidth)
                } else {
                    IntSize(options.outWidth, options.outHeight)
                }

                val scaleFactor = calculateInSampleSize(options, 2048, 2048)
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scaleFactor }
                
                var decodedBitmap: Bitmap? = null
                FileHelper.readFileFromUri(context, imageUri).use { 
                    decodedBitmap = BitmapFactory.decodeStream(it, null, decodeOptions) 
                }
                
                if (decodedBitmap != null) {
                    var finalBitmap = decodedBitmap!!
                    
                    // Apply Exif Rotation
                    val exifMatrix = Matrix()
                    when (exifOrientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> exifMatrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> exifMatrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> exifMatrix.postRotate(270f)
                    }
                    if (!exifMatrix.isIdentity) {
                        val rotated = Bitmap.createBitmap(finalBitmap, 0, 0, finalBitmap.width, finalBitmap.height, exifMatrix, true)
                        if (rotated != finalBitmap) {
                            finalBitmap.recycle()
                            finalBitmap = rotated
                        }
                    }
                    
                    // Apply Manual Rotation
                    if (currentRotation % 360 != 0) {
                        val manualMatrix = Matrix().apply { postRotate(currentRotation.toFloat()) }
                        val manuallyRotated = Bitmap.createBitmap(finalBitmap, 0, 0, finalBitmap.width, finalBitmap.height, manualMatrix, true)
                        if (manuallyRotated != finalBitmap) {
                            finalBitmap.recycle()
                            finalBitmap = manuallyRotated
                        }
                    }
                    
                    sourceBitmap = finalBitmap
                    // Force re-initialization of bounds when rotation changes
                    isInitialized = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Crop", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentRotation = (currentRotation + 90) % 360 }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate Image")
                        }
                        TextButton(
                            onClick = {
                                if (sourceBitmap != null) {
                                    val scaleX = originalSize.width.toFloat() / imageBounds.width
                                    val scaleY = originalSize.height.toFloat() / imageBounds.height
                                    
                                    val left = ((cropRect.left - imageBounds.left) * scaleX).toInt()
                                        .coerceIn(0, originalSize.width)
                                    val top = ((cropRect.top - imageBounds.top) * scaleY).toInt()
                                        .coerceIn(0, originalSize.height)
                                    val right = ((cropRect.right - imageBounds.left) * scaleX).toInt()
                                        .coerceIn(0, originalSize.width)
                                    val bottom = ((cropRect.bottom - imageBounds.top) * scaleY).toInt()
                                        .coerceIn(0, originalSize.height)
                                    
                                    onCropApplied(left, top, right, bottom, currentRotation)
                                }
                            }
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ratios.forEach { ratio ->
                            RatioItem(
                                ratio = ratio,
                                isSelected = selectedRatio == ratio.value,
                                onClick = {
                                    selectedRatio = ratio.value
                                    selectedRatio?.let { r ->
                                        cropRect = applyRatioToRect(cropRect, r, imageBounds)
                                    }
                                    hapticHelper.click()
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
            ) {
                if (sourceBitmap != null) {
                    val imageBitmap = sourceBitmap!!.asImageBitmap()
                    val primaryColor = MaterialTheme.colorScheme.primary
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .pointerInput(selectedRatio) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        activeHandle = getHandleAt(offset, cropRect, 32.dp.toPx())
                                        if (activeHandle != HandleType.NONE) hapticHelper.selection()
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        activeHandle = HandleType.NONE
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        activeHandle = HandleType.NONE
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        cropRect = updateCropRect(
                                            cropRect, dragAmount, activeHandle, imageBounds, selectedRatio
                                        )
                                    }
                                )
                            }
                    ) {
                        val scale = minOf(size.width / imageBitmap.width, size.height / imageBitmap.height)
                        val dw = imageBitmap.width * scale
                        val dh = imageBitmap.height * scale
                        val offsetX = (size.width - dw) / 2
                        val offsetY = (size.height - dh) / 2
                        
                        val currentBounds = Rect(offsetX, offsetY, offsetX + dw, offsetY + dh)
                        imageBounds = currentBounds

                        if (!isInitialized && originalSize.width > 0 && originalSize.height > 0) {
                            if (initialCropRect != null) {
                                val scaleX = dw / originalSize.width.toFloat()
                                val scaleY = dh / originalSize.height.toFloat()
                                cropRect = Rect(
                                    left = offsetX + initialCropRect.left * scaleX,
                                    top = offsetY + initialCropRect.top * scaleY,
                                    right = offsetX + initialCropRect.right * scaleX,
                                    bottom = offsetY + initialCropRect.bottom * scaleY
                                )
                            } else {
                                cropRect = currentBounds.inflate(-dw * 0.1f)
                            }
                            isInitialized = true
                        }

                        // Draw Image
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(dw.toInt(), dh.toInt())
                        )

                        // Draw Dimming
                        val overlayPath = Path().apply {
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRect(cropRect)
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(overlayPath, color = Color.Black.copy(alpha = 0.6f))

                        // Draw 3x3 Grid
                        if (isDragging) {
                            val gridStroke = 1.dp.toPx()
                            val gridColor = Color.White.copy(alpha = 0.5f)
                            for (i in 1..2) {
                                val x = cropRect.left + (cropRect.width * i / 3f)
                                drawLine(gridColor, Offset(x, cropRect.top), Offset(x, cropRect.bottom), gridStroke)
                                val y = cropRect.top + (cropRect.height * i / 3f)
                                drawLine(gridColor, Offset(cropRect.left, y), Offset(cropRect.right, y), gridStroke)
                            }
                        }

                        // Draw Border
                        drawRect(
                            color = Color.White,
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Draw 8 Handles
                        val handleRadius = 5.dp.toPx()
                        val handlePoints = listOf(
                            cropRect.topLeft to HandleType.TOP_LEFT,
                            Offset(cropRect.center.x, cropRect.top) to HandleType.TOP_CENTER,
                            cropRect.topRight to HandleType.TOP_RIGHT,
                            Offset(cropRect.left, cropRect.center.y) to HandleType.LEFT_CENTER,
                            Offset(cropRect.right, cropRect.center.y) to HandleType.RIGHT_CENTER,
                            cropRect.bottomLeft to HandleType.BOTTOM_LEFT,
                            Offset(cropRect.center.x, cropRect.bottom) to HandleType.BOTTOM_CENTER,
                            cropRect.bottomRight to HandleType.BOTTOM_RIGHT
                        )

                        handlePoints.forEach { (point, type) ->
                            val color = if (activeHandle == type) primaryColor else Color.White
                            drawCircle(Color.Black.copy(alpha = 0.2f), radius = handleRadius + 2f, center = point)
                            drawCircle(color, radius = handleRadius, center = point)
                        }
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun RatioItem(
    ratio: CropRatio,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val ratioValue = ratio.value
                if (ratioValue == null) {
                    Icon(
                        Icons.Default.CropFree,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val baseSize = 24f
                    val (w, h) = if (ratioValue >= 1f) {
                        baseSize to (baseSize / ratioValue)
                    } else {
                        (baseSize * ratioValue) to baseSize
                    }

                    Box(
                        modifier = Modifier
                            .size(w.dp, h.dp)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = ratio.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getHandleAt(offset: Offset, rect: Rect, threshold: Float): HandleType {
    val x = offset.x
    val y = offset.y
    
    // Corners
    if (abs(x - rect.left) < threshold && abs(y - rect.top) < threshold) return HandleType.TOP_LEFT
    if (abs(x - rect.right) < threshold && abs(y - rect.top) < threshold) return HandleType.TOP_RIGHT
    if (abs(x - rect.left) < threshold && abs(y - rect.bottom) < threshold) return HandleType.BOTTOM_LEFT
    if (abs(x - rect.right) < threshold && abs(y - rect.bottom) < threshold) return HandleType.BOTTOM_RIGHT
    
    // Edges
    if (abs(x - rect.center.x) < threshold && abs(y - rect.top) < threshold) return HandleType.TOP_CENTER
    if (abs(x - rect.center.x) < threshold && abs(y - rect.bottom) < threshold) return HandleType.BOTTOM_CENTER
    if (abs(x - rect.left) < threshold && abs(y - rect.center.y) < threshold) return HandleType.LEFT_CENTER
    if (abs(x - rect.right) < threshold && abs(y - rect.center.y) < threshold) return HandleType.RIGHT_CENTER
    
    if (rect.contains(offset)) return HandleType.INSIDE
    return HandleType.NONE
}

private fun updateCropRect(
    rect: Rect,
    dragAmount: Offset,
    handle: HandleType,
    bounds: Rect,
    ratio: Float?
): Rect {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom
    val minSize = 120f

    when (handle) {
        HandleType.INSIDE -> {
            left = (left + dragAmount.x).coerceIn(bounds.left, bounds.right - rect.width)
            top = (top + dragAmount.y).coerceIn(bounds.top, bounds.bottom - rect.height)
            right = left + rect.width
            bottom = top + rect.height
        }
        HandleType.TOP_LEFT -> {
            left += dragAmount.x
            top += dragAmount.y
            if (ratio != null) {
                if ((right - left) / (bottom - top) > ratio) left = right - (bottom - top) * ratio 
                else top = bottom - (right - left) / ratio
            }
        }
        HandleType.TOP_RIGHT -> {
            right += dragAmount.x
            top += dragAmount.y
            if (ratio != null) {
                if ((right - left) / (bottom - top) > ratio) right = left + (bottom - top) * ratio
                else top = bottom - (right - left) / ratio
            }
        }
        HandleType.BOTTOM_LEFT -> {
            left += dragAmount.x
            bottom += dragAmount.y
            if (ratio != null) {
                if ((right - left) / (bottom - top) > ratio) left = right - (bottom - top) * ratio
                else bottom = top + (right - left) / ratio
            }
        }
        HandleType.BOTTOM_RIGHT -> {
            right += dragAmount.x
            bottom += dragAmount.y
            if (ratio != null) {
                if ((right - left) / (bottom - top) > ratio) right = left + (bottom - top) * ratio
                else bottom = top + (right - left) / ratio
            }
        }
        HandleType.TOP_CENTER -> {
            top += dragAmount.y
            if (ratio != null) {
                val newH = bottom - top
                val newW = newH * ratio
                left = rect.center.x - newW / 2
                right = rect.center.x + newW / 2
            }
        }
        HandleType.BOTTOM_CENTER -> {
            bottom += dragAmount.y
            if (ratio != null) {
                val newH = bottom - top
                val newW = newH * ratio
                left = rect.center.x - newW / 2
                right = rect.center.x + newW / 2
            }
        }
        HandleType.LEFT_CENTER -> {
            left += dragAmount.x
            if (ratio != null) {
                val newW = right - left
                val newH = newW / ratio
                top = rect.center.y - newH / 2
                bottom = rect.center.y + newH / 2
            }
        }
        HandleType.RIGHT_CENTER -> {
            right += dragAmount.x
            if (ratio != null) {
                val newW = right - left
                val newH = newW / ratio
                top = rect.center.y - newH / 2
                bottom = rect.center.y + newH / 2
            }
        }
        HandleType.NONE -> {}
    }

    // Constraints
    if (right - left < minSize) {
        if (handle == HandleType.TOP_LEFT || handle == HandleType.BOTTOM_LEFT || handle == HandleType.LEFT_CENTER) left = right - minSize else right = left + minSize
    }
    if (bottom - top < minSize) {
        if (handle == HandleType.TOP_LEFT || handle == HandleType.TOP_RIGHT || handle == HandleType.TOP_CENTER) top = bottom - minSize else bottom = top + minSize
    }

    // Boundary check
    left = left.coerceAtLeast(bounds.left)
    top = top.coerceAtLeast(bounds.top)
    right = right.coerceAtMost(bounds.right)
    bottom = bottom.coerceAtMost(bounds.bottom)

    return Rect(left, top, right, bottom)
}

private fun applyRatioToRect(rect: Rect, ratio: Float, bounds: Rect): Rect {
    var w = rect.width
    var h = rect.width / ratio
    if (h > bounds.height) {
        h = bounds.height
        w = h * ratio
    }
    if (w > bounds.width) {
        w = bounds.width
        h = w / ratio
    }
    val left = (rect.center.x - w / 2).coerceIn(bounds.left, bounds.right - w)
    val top = (rect.center.y - h / 2).coerceIn(bounds.top, bounds.bottom - h)
    return Rect(left, top, left + w, top + h)
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}