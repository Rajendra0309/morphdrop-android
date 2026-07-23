package com.morphdrop.app.domain.repository

import com.morphdrop.app.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun getFavorites(): Flow<List<FavoriteEntity>>
    suspend fun toggleFavorite(conversionTypeId: String)
    fun isFavorite(conversionTypeId: String): Flow<Boolean>
}
