package com.morphdrop.app.domain.usecase.history

import com.morphdrop.app.domain.repository.HistoryRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke() {
        repository.clearAllHistory()
    }
}
