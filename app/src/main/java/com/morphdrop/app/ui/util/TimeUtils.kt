package com.morphdrop.app.ui.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun formatRelativeTime(timestamp: Long): String {
        return try {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            when {
                diff < 60_000L -> "Just now"
                diff < 3600_000L -> "${diff / 60_000L}m ago"
                diff < 86400_000L -> "${diff / 3600_000L}h ago"
                else -> DateUtils.getRelativeTimeSpanString(
                    timestamp,
                    now,
                    DateUtils.DAY_IN_MILLIS
                ).toString()
            }
        } catch (e: Exception) {
            "Recently"
        }
    }

    fun formatFullDateTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown Date"
        }
    }

    fun formatOutputDisplayName(outputNames: String?): String {
        if (outputNames.isNullOrBlank() || outputNames == "-") return "None"
        
        // Split by comma if multiple files
        val items = outputNames.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (items.isEmpty()) return "None"

        if (items.size > 1) {
            val firstItem = items.first()
            if (firstItem.contains("/")) {
                val segments = firstItem.split("/").filter { it.isNotBlank() }
                if (segments.size > 1) {
                    val parent = segments[segments.size - 2]
                    return "$parent (Folder)"
                }
            }
            return "${items.size} Converted Files"
        }
        
        // Single file
        val outputName = items.first()
        if (outputName.contains("/")) {
            val name = outputName.substringAfterLast("/")
            if (name.isNotBlank()) {
                return name
            }
        }
        
        return outputName
    }
}
