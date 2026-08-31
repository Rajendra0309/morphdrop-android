package com.morphdrop.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.morphdrop.app.data.local.dao.BookmarkDao
import com.morphdrop.app.data.local.dao.FavoriteDao
import com.morphdrop.app.data.local.dao.HistoryDao
import com.morphdrop.app.data.local.entity.BookmarkEntity
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.data.local.entity.FavoriteEntity

import com.morphdrop.app.data.local.dao.PdfAnnotationDao
import com.morphdrop.app.data.local.entity.PdfAnnotationEntity

@Database(
    entities = [ConversionHistoryEntity::class, FavoriteEntity::class, BookmarkEntity::class, PdfAnnotationEntity::class],
    version = 4,
    exportSchema = false
)
abstract class MorphDropDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
}
