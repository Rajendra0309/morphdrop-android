package com.morphdrop.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    // conversionTypeId is the natural primary key: duplicates are impossible
    // at the schema level, so a favorite can never be stored twice.
    @PrimaryKey
    val conversionTypeId: String,
    val timestamp: Long = System.currentTimeMillis()
)
