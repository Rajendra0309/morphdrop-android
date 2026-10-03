package com.morphdrop.app.domain.usecase.history

import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.repository.HistoryRepository
import javax.inject.Inject

class SaveHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(history: ConversionHistoryEntity): Long {
        // Non-breaking normalization: backfill the stable conversionTypeId for
        // rows that carry only the legacy display name (exact id/name match).
        val normalized = if (history.conversionTypeId.isNullOrBlank()) {
            ConversionType.defaultList
                .firstOrNull { it.id == history.conversionType || it.name == history.conversionType }
                ?.let { history.copy(conversionTypeId = it.id) }
                ?: history
        } else history
        return repository.insertHistory(normalized)
    }
}
