package com.morphdrop.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.morphdrop.app.data.local.dao.FavoriteDao
import com.morphdrop.app.data.local.dao.HistoryDao
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.data.local.entity.FavoriteEntity

@Database(
    entities = [ConversionHistoryEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MorphDropDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
}
