package com.sonothamin.meowlaundry.ui.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.AppPreferences
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClosetViewMode
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ClosetViewModel(
    private val repository: ClosetRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _filter = MutableStateFlow<ClothingStatus?>(null)
    val filter: StateFlow<ClothingStatus?> = _filter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    val selectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val viewMode: StateFlow<ClosetViewMode> = appPreferences.closetViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosetViewMode.GRID)

    private val allItemsForFilter: StateFlow<List<ClothingItem>> = _filter
        .flatMapLatest { status ->
            when (status) {
                // "All" means all active garments - archived ones have their own filter chip
                // and live in the Archive screen so they don't clutter the everyday view.
                null -> repository.observeAllClothing().map { list -> list.filter { it.status != ClothingStatus.ARCHIVED } }
                else -> repository.observeClothingByStatus(status)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<ClothingItem>> = combine(allItemsForFilter, _searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) || it.type.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<ClosetRepository.ClosetSummary> = repository.observeSummary()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ClosetRepository.ClosetSummary(0, 0, 0, 0.0),
        )

    fun setFilter(status: ClothingStatus?) {
        _filter.value = status
    }

    fun setViewMode(mode: ClosetViewMode) {
        viewModelScope.launch { appPreferences.setClosetViewMode(mode) }
    }

    fun toggleViewMode() {
        setViewMode(if (viewMode.value == ClosetViewMode.GRID) ClosetViewMode.LIST else ClosetViewMode.GRID)
    }

    fun setSearchActive(active: Boolean) {
        _searchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(item: ClothingItem) {
        viewModelScope.launch { repository.deleteClothing(item) }
    }

    // --- Multiselect ------------------------------------------------------

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun selectAll() {
        _selectedIds.value = items.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    suspend fun getSelectedItems(): List<ClothingItem> = repository.getClothingByIds(_selectedIds.value.toList())

    fun deleteSelected(onDone: () -> Unit = {}) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteClothingByIds(ids)
            _selectedIds.value = emptySet()
            onDone()
        }
    }
}
