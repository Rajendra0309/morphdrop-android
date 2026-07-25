package com.morphdrop.app.domain.usecase.favorite

import com.morphdrop.app.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    operator fun invoke(conversionTypeId: String): Flow<Boolean> {
        return repository.isFavorite(conversionTypeId)
    }
}
