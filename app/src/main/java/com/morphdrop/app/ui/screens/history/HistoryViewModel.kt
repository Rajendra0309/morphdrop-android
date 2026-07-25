package com.morphdrop.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val historyList: StateFlow<List<ConversionHistoryEntity>> = historyRepository.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteItem(historyEntity: ConversionHistoryEntity) {
        viewModelScope.launch {
            historyRepository.deleteHistory(historyEntity)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }
}
