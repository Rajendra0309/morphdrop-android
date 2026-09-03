package com.morphdrop.app.ui.screens.conversion.components

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.tooling.preview.Preview
import com.morphdrop.app.ui.theme.MorphDropTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphdrop.app.domain.model.FileMetadata
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.model.MetadataEditParams
import com.morphdrop.app.ui.components.FormatBadge
import java.util.Locale

@Composable
fun MetadataInspectorView(
    metadata: FileMetadata?,
    isLoading: Boolean,
    editAuthor: String,
    editTitle: String,
    editSubject: String,
    editSoftware: String,
    editCopyright: String,
    editDateCreated: String,
    editLatitude: String,
    editLongitude: String,
    editCameraMake: String,
    editCameraModel: String,
    gpsError: String?,
    onAuthorChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onSoftwareChange: (String) -> Unit,
    onCopyrightChange: (String) -> Unit,
    onDateCreatedChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onCameraMakeChange: (String) -> Unit,
    onCameraModelChange: (String) -> Unit,
    onScrubClicked: () -> Unit,
    onApplyEditsClicked: (MetadataEditParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isEditModeOpen by remember { mutableStateOf(false) }
    var showScrubConfirmDialog by remember { mutableStateOf(false) }
    var showPreviewDiffDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Disclaimer Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Original File Protected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Original files are never modified. Changes are saved as new files in Downloads/MorphDrop/.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        if (metadata != null && !metadata.isEditable && !metadata.isScrubbable) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Audio and Video files support Metadata Inspection. Scrubbing & Editing are supported for Images and PDFs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        if (isLoading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Reading & Inspecting Metadata...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        if (metadata == null) return

        // File General Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${metadata.fileSizeFormatted} • ${metadata.mimeType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (metadata.fileType != null) {
                        FormatBadge(fileType = metadata.fileType)
                    } else {
                        FormatBadge(
                            text = metadata.fileExtension,
                            backgroundColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (!metadata.fileHash.isNull_or_empty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "SHA-256 Hash Preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = metadata.fileHash ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(metadata.fileHash ?: ""))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Hash",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row (Scrub & Edit Toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showScrubConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = metadata.isScrubbable,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scrub All",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { isEditModeOpen = !isEditModeOpen },
                shape = RoundedCornerShape(12.dp),
                enabled = metadata.isEditable,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isEditModeOpen) Icons.Outlined.Info else Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditModeOpen) "View Details" else "Edit Metadata",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Edit Metadata Form
        AnimatedVisibility(
            visible = isEditModeOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Edit & Spoof Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = editAuthor,
                        onValueChange = onAuthorChange,
                        label = { Text("Author / Artist") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Title / Description") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = onSubjectChange,
                        label = { Text("Subject / User Comment") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editSoftware,
                        onValueChange = onSoftwareChange,
                        label = { Text("Software / Creator") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editCopyright,
                        onValueChange = onCopyrightChange,
                        label = { Text("Copyright Info") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDateCreated,
                        onValueChange = onDateCreatedChange,
                        label = { Text("Date Created (YYYY-MM-DD HH:MM:SS)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editLatitude,
                            onValueChange = onLatitudeChange,
                            label = { Text("GPS Lat (-90 to 90)") },
                            singleLine = true,
                            isError = gpsError != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = editLongitude,
                            onValueChange = onLongitudeChange,
                            label = { Text("GPS Lng (-180 to 180)") },
                            singleLine = true,
                            isError = gpsError != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (gpsError != null) {
                        Text(
                            text = gpsError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editCameraMake,
                            onValueChange = onCameraMakeChange,
                            label = { Text("Camera Make") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = editCameraModel,
                            onValueChange = onCameraModelChange,
                            label = { Text("Camera Model") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showPreviewDiffDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            enabled = gpsError == null
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Preview & Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Inspection Categories
        if (!metadata.hasMetadata) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FindInPage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Hidden Metadata Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This file does not contain camera EXIF, GPS tracking tags, or document properties.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        // Camera Info Category
        val cameraItems = listOfNotNull(
            metadata.cameraMake?.let { "Make" to it },
            metadata.cameraModel?.let { "Model" to it },
            metadata.software?.let { "Software" to it },
            metadata.lensModel?.let { "Lens" to it },
            metadata.focalLength?.let { "Focal Length" to it },
            metadata.fNumber?.let { "Aperture" to it },
            metadata.isoSpeed?.let { "ISO Speed" to it },
            metadata.exposureTime?.let { "Exposure Time" to it },
            metadata.flash?.let { "Flash" to it }
        )
        if (cameraItems.isNotEmpty()) {
            MetadataCategoryCard(
                title = "Camera & Hardware",
                icon = Icons.Outlined.CameraAlt,
                items = cameraItems
            )
        }

        // Video & Audio Category
        val mediaItems = listOfNotNull(
            metadata.duration?.let { "Duration" to it },
            metadata.bitrate?.let { "Bitrate" to it }
        )
        if (mediaItems.isNotEmpty()) {
            MetadataCategoryCard(
                title = "Media Details",
                icon = Icons.Outlined.VideoLibrary,
                items = mediaItems
            )
        }

        // Location Category
        if (metadata.latitude != null && metadata.longitude != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "GPS Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val mapUri = Uri.parse("geo:${metadata.latitude},${metadata.longitude}?q=${metadata.latitude},${metadata.longitude}")
                                val intent = Intent(Intent.ACTION_VIEW, mapUri)
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Open Map", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (!metadata.locationAddress.isNull_or_empty()) {
                        MetadataItemRow(
                            label = "Address",
                            value = metadata.locationAddress!!
                        )
                    }
                    MetadataItemRow(
                        label = "Coordinates",
                        value = "${String.format(Locale.US, "%.6f", metadata.latitude)}, ${String.format(Locale.US, "%.6f", metadata.longitude)}"
                    )
                    if (metadata.gpsAltitude != null) {
                        MetadataItemRow(
                            label = "Altitude",
                            value = "${String.format(Locale.US, "%.1f", metadata.gpsAltitude)} m"
                        )
                    }
                }
            }
        }

        // Dates Category
        val dateItems = listOfNotNull(
            metadata.dateCreated?.let { "Created" to it },
            metadata.dateOriginal?.let { "Original" to it },
            metadata.dateDigitized?.let { "Digitized" to it },
            metadata.dateModified?.let { "Modified" to it }
        )
        if (dateItems.isNotEmpty()) {
            MetadataCategoryCard(
                title = "Dates & Timeline",
                icon = Icons.Outlined.CalendarToday,
                items = dateItems
            )
        }

        // Document Info Category
        val docItems = listOfNotNull(
            metadata.author?.let { "Author / Artist" to it },
            metadata.title?.let { "Title / Description" to it },
            metadata.subject?.let { "Subject" to it },
            metadata.creator?.let { "Creator" to it },
            metadata.producer?.let { "Producer" to it },
            metadata.keywords?.let { "Keywords" to it },
            metadata.copyright?.let { "Copyright" to it }
        )
        if (docItems.isNotEmpty()) {
            MetadataCategoryCard(
                title = "Document Info",
                icon = Icons.Outlined.Description,
                items = docItems
            )
        }

        // Technical Category
        val techItems = listOfNotNull(
            metadata.width?.let { w -> metadata.height?.let { h -> "Dimensions" to "${w} x ${h} px" } },
            metadata.dpi?.let { "Resolution" to it },
            metadata.colorSpace?.let { "Color Space" to it },
            metadata.pageCount?.let { "Page Count" to "$it pages" }
        )
        if (techItems.isNotEmpty()) {
            MetadataCategoryCard(
                title = "Technical Details",
                icon = Icons.Outlined.Build,
                items = techItems
            )
        }
    }

    // Confirmation Dialog for Scrub
    if (showScrubConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showScrubConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Remove All Metadata?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will create a new file with all metadata removed. Your original file will not be modified. This action cannot be undone for the new file."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showScrubConfirmDialog = false
                        onScrubClicked()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = "Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScrubConfirmDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // Diff Preview Dialog before Applying Edits
    if (showPreviewDiffDialog && metadata != null) {
        val newLat = editLatitude.toDoubleOrNull()
        val newLng = editLongitude.toDoubleOrNull()

        AlertDialog(
            onDismissRequest = { showPreviewDiffDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Preview Changes",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Review updated metadata before generating the new file:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()

                    if (editAuthor.isNotBlank()) {
                        DiffRow(label = "Author", oldVal = metadata.author ?: "Not set", newVal = editAuthor)
                    }
                    if (editTitle.isNotBlank()) {
                        DiffRow(label = "Title", oldVal = metadata.title ?: "Not set", newVal = editTitle)
                    }
                    if (editSubject.isNotBlank()) {
                        DiffRow(label = "Subject", oldVal = metadata.subject ?: "Not set", newVal = editSubject)
                    }
                    if (editSoftware.isNotBlank()) {
                        DiffRow(label = "Software", oldVal = metadata.software ?: "Not set", newVal = editSoftware)
                    }
                    if (editCopyright.isNotBlank()) {
                        DiffRow(label = "Copyright", oldVal = metadata.copyright ?: "Not set", newVal = editCopyright)
                    }
                    if (editDateCreated.isNotBlank()) {
                        DiffRow(label = "Date Created", oldVal = metadata.dateCreated ?: "Not set", newVal = editDateCreated)
                    }
                    if (newLat != null && newLng != null) {
                        val oldLoc = if (metadata.latitude != null && metadata.longitude != null) {
                            "${String.format(Locale.US, "%.4f", metadata.latitude)}, ${String.format(Locale.US, "%.4f", metadata.longitude)}"
                        } else "Not set"
                        DiffRow(label = "GPS Coordinates", oldVal = oldLoc, newVal = "${String.format(Locale.US, "%.4f", newLat)}, ${String.format(Locale.US, "%.4f", newLng)}")
                    }
                    if (editCameraMake.isNotBlank()) {
                        DiffRow(label = "Camera Make", oldVal = metadata.cameraMake ?: "Not set", newVal = editCameraMake)
                    }
                    if (editCameraModel.isNotBlank()) {
                        DiffRow(label = "Camera Model", oldVal = metadata.cameraModel ?: "Not set", newVal = editCameraModel)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPreviewDiffDialog = false
                        isEditModeOpen = false
                        onApplyEditsClicked(
                            MetadataEditParams(
                                author = editAuthor.takeIf { it.isNotBlank() },
                                title = editTitle.takeIf { it.isNotBlank() },
                                subject = editSubject.takeIf { it.isNotBlank() },
                                software = editSoftware.takeIf { it.isNotBlank() },
                                copyright = editCopyright.takeIf { it.isNotBlank() },
                                dateCreated = editDateCreated.takeIf { it.isNotBlank() },
                                latitude = newLat,
                                longitude = newLng,
                                cameraMake = editCameraMake.takeIf { it.isNotBlank() },
                                cameraModel = editCameraModel.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                ) {
                    Text(text = "Apply & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreviewDiffDialog = false }) {
                    Text(text = "Back")
                }
            }
        )
    }
}

@Composable
private fun MetadataCategoryCard(
    title: String,
    icon: ImageVector,
    items: List<Pair<String, String>>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            items.forEachIndexed { index, (label, value) ->
                MetadataItemRow(label = label, value = value)
                if (index < items.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MetadataItemRow(label: String, value: String) {
    val clipboardManager = LocalClipboardManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                clipboardManager.setText(AnnotatedString(value))
            }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun DiffRow(label: String, oldVal: String, newVal: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = oldVal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " -> ",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = newVal,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun MetadataInspectorViewLightPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(16.dp)) {
            MetadataInspectorView(
                metadata = FileMetadata(
                    fileName = "IMG_20240512_143000.jpg",
                    fileSize = 3450000L,
                    fileSizeFormatted = "3.4 MB",
                    mimeType = "image/jpeg",
                    fileExtension = "jpg",
                    fileType = FileType.JPG,
                    dateModified = "2024-05-12 14:32:10",
                    fileHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    cameraMake = "Google",
                    cameraModel = "Pixel 7 Pro",
                    software = "Google Camera 9.1",
                    lensModel = "Pixel 7 Pro Rear Main Camera",
                    focalLength = "6.81 mm",
                    fNumber = "f/1.85",
                    isoSpeed = "100",
                    exposureTime = "1/250s",
                    flash = "No Flash",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    gpsAltitude = 15.0,
                    locationAddress = "San Francisco, CA, USA",
                    dateCreated = "2024-05-12 14:30:00",
                    dateOriginal = "2024-05-12 14:30:00",
                    dateDigitized = "2024-05-12 14:30:00",
                    author = "MorphDrop User",
                    title = "Golden Gate Capture",
                    subject = "Landscape Photography",
                    creator = "Pixel 7 Pro",
                    copyright = "2024 MorphDrop",
                    width = 4080,
                    height = 3072,
                    dpi = "300 dpi",
                    colorSpace = "sRGB",
                    hasMetadata = true,
                    isScrubbable = true,
                    isEditable = true
                ),
                isLoading = false,
                editAuthor = "MorphDrop User",
                editTitle = "Golden Gate Capture",
                editSubject = "Landscape Photography",
                editSoftware = "Google Camera 9.1",
                editCopyright = "2024 MorphDrop",
                editDateCreated = "2024-05-12 14:30:00",
                editLatitude = "37.7749",
                editLongitude = "-122.4194",
                editCameraMake = "Google",
                editCameraModel = "Pixel 7 Pro",
                gpsError = null,
                onAuthorChange = {},
                onTitleChange = {},
                onSubjectChange = {},
                onSoftwareChange = {},
                onCopyrightChange = {},
                onDateCreatedChange = {},
                onLatitudeChange = {},
                onLongitudeChange = {},
                onCameraMakeChange = {},
                onCameraModelChange = {},
                onScrubClicked = {},
                onApplyEditsClicked = {}
            )
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun MetadataInspectorViewDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            MetadataInspectorView(
                metadata = FileMetadata(
                    fileName = "IMG_20240512_143000.jpg",
                    fileSize = 3450000L,
                    fileSizeFormatted = "3.4 MB",
                    mimeType = "image/jpeg",
                    fileExtension = "jpg",
                    fileType = FileType.JPG,
                    dateModified = "2024-05-12 14:32:10",
                    fileHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    cameraMake = "Google",
                    cameraModel = "Pixel 7 Pro",
                    software = "Google Camera 9.1",
                    lensModel = "Pixel 7 Pro Rear Main Camera",
                    focalLength = "6.81 mm",
                    fNumber = "f/1.85",
                    isoSpeed = "100",
                    exposureTime = "1/250s",
                    flash = "No Flash",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    gpsAltitude = 15.0,
                    locationAddress = "San Francisco, CA, USA",
                    dateCreated = "2024-05-12 14:30:00",
                    dateOriginal = "2024-05-12 14:30:00",
                    dateDigitized = "2024-05-12 14:30:00",
                    author = "MorphDrop User",
                    title = "Golden Gate Capture",
                    subject = "Landscape Photography",
                    creator = "Pixel 7 Pro",
                    copyright = "2024 MorphDrop",
                    width = 4080,
                    height = 3072,
                    dpi = "300 dpi",
                    colorSpace = "sRGB",
                    hasMetadata = true,
                    isScrubbable = true,
                    isEditable = true
                ),
                isLoading = false,
                editAuthor = "MorphDrop User",
                editTitle = "Golden Gate Capture",
                editSubject = "Landscape Photography",
                editSoftware = "Google Camera 9.1",
                editCopyright = "2024 MorphDrop",
                editDateCreated = "2024-05-12 14:30:00",
                editLatitude = "37.7749",
                editLongitude = "-122.4194",
                editCameraMake = "Google",
                editCameraModel = "Pixel 7 Pro",
                gpsError = null,
                onAuthorChange = {},
                onTitleChange = {},
                onSubjectChange = {},
                onSoftwareChange = {},
                onCopyrightChange = {},
                onDateCreatedChange = {},
                onLatitudeChange = {},
                onLongitudeChange = {},
                onCameraMakeChange = {},
                onCameraModelChange = {},
                onScrubClicked = {},
                onApplyEditsClicked = {}
            )
        }
    }
}
