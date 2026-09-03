package com.morphdrop.app.domain.model

/**
 * Data class representing update information retrieved from GitHub releases.
 */
data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean = false
)
