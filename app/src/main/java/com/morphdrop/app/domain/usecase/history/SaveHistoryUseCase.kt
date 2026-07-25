package com.morphdrop.app.domain.usecase.history

import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import javax.inject.Inject

class SaveHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(history: ConversionHistoryEntity): Long {
        return repository.insertHistory(history)
    }
}
