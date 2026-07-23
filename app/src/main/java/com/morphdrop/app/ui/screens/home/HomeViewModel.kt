package com.morphdrop.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.domain.model.ConversionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _conversionTypes = MutableStateFlow(ConversionType.defaultList)
    val conversionTypes: StateFlow<List<ConversionType>> = _conversionTypes.asStateFlow()

    val favoriteConversionTypes: StateFlow<List<ConversionType>> = _conversionTypes
        .combine(MutableStateFlow(Unit)) { types, _ ->
            types.filter { it.isFavorite }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredConversionTypes: StateFlow<List<ConversionType>> = combine(
        _conversionTypes,
        _searchQuery
    ) { types, query ->
        if (query.isBlank()) {
            types
        } else {
            types.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.inputType.displayName.contains(query, ignoreCase = true) ||
                        it.outputType.displayName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConversionType.defaultList
    )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onToggleFavorite(id: String) {
        _conversionTypes.value = _conversionTypes.value.map { type ->
            if (type.id == id) {
                type.copy(isFavorite = !type.isFavorite)
            } else {
                type
            }
        }
    }
}
