package com.morphdrop.app.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import com.morphdrop.app.ui.widget.WidgetUpdateHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val historyList: StateFlow<List<ConversionHistoryEntity>> = combine(
        historyRepository.getAllHistory(),
        _searchQuery
    ) { history, query ->
        history.filter { item ->
            if (query.isBlank()) true else {
                item.inputFileName.contains(query, ignoreCase = true) ||
                item.outputFileNames.contains(query, ignoreCase = true) ||
                item.conversionType.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(historyEntity: ConversionHistoryEntity) {
        viewModelScope.launch {
            historyRepository.deleteHistory(historyEntity)
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }
}
