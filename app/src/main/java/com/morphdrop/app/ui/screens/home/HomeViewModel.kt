package com.morphdrop.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.usecase.favorite.GetFavoritesUseCase
import com.morphdrop.app.domain.usecase.favorite.ToggleFavoriteUseCase
import com.morphdrop.app.domain.usecase.history.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val conversionTypes: StateFlow<List<ConversionType>> = getFavoritesUseCase()
        .map { favorites ->
            val favoriteIds = favorites.map { it.conversionTypeId }.toSet()
            ConversionType.defaultList.map { it.copy(isFavorite = it.id in favoriteIds) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConversionType.defaultList
        )

    val favoriteConversionTypes: StateFlow<List<ConversionType>> = conversionTypes
        .map { types ->
            types.filter { it.isFavorite }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentConversions: StateFlow<List<ConversionHistoryEntity>> = getHistoryUseCase()
        .map { history ->
            history.take(5) // Limit to 5 most recent
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredConversionTypes: StateFlow<List<ConversionType>> = combine(
        conversionTypes,
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
        viewModelScope.launch {
            toggleFavoriteUseCase(id)
        }
    }
}
