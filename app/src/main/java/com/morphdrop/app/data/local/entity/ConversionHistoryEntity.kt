package com.morphdrop.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversionType: String,
    val inputFileName: String,
    val outputFileNames: String,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0L,
    val success: Boolean = true
)
