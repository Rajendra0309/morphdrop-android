package com.morphdrop.app.domain.usecase.favorite

import com.morphdrop.app.domain.repository.FavoriteRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    suspend operator fun invoke(conversionTypeId: String) {
        repository.toggleFavorite(conversionTypeId)
    }
}
