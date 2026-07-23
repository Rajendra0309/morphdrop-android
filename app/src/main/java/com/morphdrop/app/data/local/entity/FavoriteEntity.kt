package com.morphdrop.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversionTypeId: String,
    val timestamp: Long = System.currentTimeMillis()
)
