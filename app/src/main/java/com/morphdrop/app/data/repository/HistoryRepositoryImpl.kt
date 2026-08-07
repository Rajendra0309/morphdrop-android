package com.morphdrop.app.data.repository

import com.morphdrop.app.data.local.dao.HistoryDao
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {
    override fun getAllHistory(): Flow<List<ConversionHistoryEntity>> {
        return historyDao.getAllHistory()
    }

    override fun getHistoryById(id: Long): Flow<ConversionHistoryEntity?> {
        return historyDao.getHistoryById(id)
    }

    override suspend fun insertHistory(history: ConversionHistoryEntity): Long {
        return historyDao.insertHistory(history)
    }

    override suspend fun deleteHistory(history: ConversionHistoryEntity) {
        historyDao.deleteHistory(history)
    }

    override suspend fun deleteHistoryById(id: Long) {
        historyDao.deleteHistoryById(id)
    }

    override suspend fun clearAllHistory() {
        historyDao.clearAllHistory()
    }
}
