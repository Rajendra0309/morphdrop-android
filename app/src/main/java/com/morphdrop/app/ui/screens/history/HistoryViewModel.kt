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

/** History list filter chips. */
enum class HistoryFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    IMAGES("Images"),
    EXCEL("Excel")
}

private val IMAGE_TOOL_IDS = setOf("image_converter", "compress_images", "pdf_to_images", "images_to_pdf")
private val EXCEL_TOOL_IDS = setOf("excel_to_pdf")

/** True when the history row belongs to the given filter. Old rows without a
 * conversionTypeId fall back to matching the display name. */
private fun ConversionHistoryEntity.matchesFilter(filter: HistoryFilter): Boolean {
    if (filter == HistoryFilter.ALL) return true
    val id = conversionTypeId
    val name = conversionType
    return when (filter) {
        HistoryFilter.PDF -> (id?.contains("pdf") == true)
            || name.contains("pdf", ignoreCase = true)
        HistoryFilter.IMAGES -> (id in IMAGE_TOOL_IDS)
            || name.contains("image", ignoreCase = true)
        HistoryFilter.EXCEL -> (id in EXCEL_TOOL_IDS)
            || name.contains("excel", ignoreCase = true) || name.contains("csv", ignoreCase = true)
        HistoryFilter.ALL -> true
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter = _filter.asStateFlow()

    /** Ids selected for bulk delete. Non-empty = selection mode. */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    val historyList: StateFlow<List<ConversionHistoryEntity>> = combine(
        historyRepository.getAllHistory(),
        _searchQuery,
        _filter
    ) { history, query, filter ->
        history
            .filter { item ->
                item.matchesFilter(filter) && (
                    query.isBlank() ||
                        item.inputFileName.contains(query, ignoreCase = true) ||
                        item.outputFileNames.contains(query, ignoreCase = true) ||
                        item.conversionType.contains(query, ignoreCase = true)
                    )
            }
            // Pinned favorites always sort first, then newest first.
            .sortedWith(compareByDescending<ConversionHistoryEntity> { it.isPinned }
                .thenByDescending { it.timestamp })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        clearSelection()
    }

    fun onFilterChange(filter: HistoryFilter) {
        _filter.value = filter
        clearSelection()
    }

    fun togglePin(item: ConversionHistoryEntity) {
        viewModelScope.launch {
            historyRepository.setPinned(item.id, !item.isPinned)
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
    }

    fun selectAll(ids: List<Long>) {
        _selectedIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Deletes all selected rows. Returns the deleted rows so the caller can
     * offer Undo. Selection is cleared.
     */
    suspend fun deleteSelected(items: List<ConversionHistoryEntity>): List<ConversionHistoryEntity> {
        val toDelete = items.filter { it.id in _selectedIds.value }
        if (toDelete.isNotEmpty()) {
            historyRepository.deleteHistoryByIds(toDelete.map { it.id })
            WidgetUpdateHelper.updateAllWidgets(context)
        }
        _selectedIds.value = emptySet()
        return toDelete
    }

    /** Re-inserts previously deleted rows (Snackbar Undo). */
    fun restoreItems(items: List<ConversionHistoryEntity>) {
        viewModelScope.launch {
            items.forEach { historyRepository.insertHistory(it) }
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    fun deleteItem(historyEntity: ConversionHistoryEntity) {
        viewModelScope.launch {
            historyRepository.deleteHistory(historyEntity)
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    /**
     * Re-inserts a previously deleted row (Snackbar Undo). Insert uses REPLACE
     * on conflict, so the row is restored with its original id.
     */
    fun restoreItem(historyEntity: ConversionHistoryEntity) {
        viewModelScope.launch {
            historyRepository.insertHistory(historyEntity)
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
