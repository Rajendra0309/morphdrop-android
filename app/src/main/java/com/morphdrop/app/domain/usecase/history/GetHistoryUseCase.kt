package com.morphdrop.app.domain.usecase.history

import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke(): Flow<List<ConversionHistoryEntity>> {
        return repository.getAllHistory()
    }
}
