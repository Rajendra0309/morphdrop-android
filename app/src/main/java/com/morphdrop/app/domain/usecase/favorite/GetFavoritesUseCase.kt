package com.morphdrop.app.domain.usecase.favorite

import com.morphdrop.app.data.local.entity.FavoriteEntity
import com.morphdrop.app.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    operator fun invoke(): Flow<List<FavoriteEntity>> {
        return repository.getFavorites()
    }
}
