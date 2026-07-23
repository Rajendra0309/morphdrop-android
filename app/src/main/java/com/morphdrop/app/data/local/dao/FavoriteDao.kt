package com.morphdrop.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.morphdrop.app.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE conversionTypeId = :conversionTypeId")
    suspend fun deleteFavoriteByTypeId(conversionTypeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE conversionTypeId = :conversionTypeId)")
    fun isFavorite(conversionTypeId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE conversionTypeId = :conversionTypeId)")
    suspend fun isFavoriteDirect(conversionTypeId: String): Boolean
}
