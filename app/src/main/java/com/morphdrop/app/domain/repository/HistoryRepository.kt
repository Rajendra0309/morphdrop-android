package com.morphdrop.app.domain.repository

import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<ConversionHistoryEntity>>
    fun getHistoryById(id: Long): Flow<ConversionHistoryEntity?>
    suspend fun insertHistory(history: ConversionHistoryEntity): Long
    suspend fun deleteHistory(history: ConversionHistoryEntity)
    suspend fun deleteHistoryById(id: Long)
    suspend fun deleteHistoryByIds(ids: List<Long>)
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun clearAllHistory()
}
