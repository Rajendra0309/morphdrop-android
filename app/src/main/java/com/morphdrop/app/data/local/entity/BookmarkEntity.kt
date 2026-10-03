package com.morphdrop.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["fileUri", "pageNumber"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileUri: String,
    val pageNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)
