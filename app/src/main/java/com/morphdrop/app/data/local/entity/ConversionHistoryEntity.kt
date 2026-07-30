package com.morphdrop.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversionType: String,
    val inputFileName: String,
    val outputFileNames: String, // This will now store the file names part
    val outputUris: String = "", // Added to store actual URIs (comma separated)
    val displayName: String = "", // Added to store the user-defined name
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0L,
    val success: Boolean = true
)
