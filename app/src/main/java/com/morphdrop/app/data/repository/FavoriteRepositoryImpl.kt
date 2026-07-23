package com.morphdrop.app.data.repository

import com.morphdrop.app.data.local.dao.FavoriteDao
import com.morphdrop.app.data.local.entity.FavoriteEntity
import com.morphdrop.app.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao
) : FavoriteRepository {
    override fun getFavorites(): Flow<List<FavoriteEntity>> {
        return favoriteDao.getFavorites()
    }

    override suspend fun toggleFavorite(conversionTypeId: String) {
        val isFav = favoriteDao.isFavoriteDirect(conversionTypeId)
        if (isFav) {
            favoriteDao.deleteFavoriteByTypeId(conversionTypeId)
        } else {
            favoriteDao.insertFavorite(FavoriteEntity(conversionTypeId = conversionTypeId))
        }
    }

    override fun isFavorite(conversionTypeId: String): Flow<Boolean> {
        return favoriteDao.isFavorite(conversionTypeId)
    }
}
