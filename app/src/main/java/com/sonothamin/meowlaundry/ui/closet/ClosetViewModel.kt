package com.sonothamin.meowlaundry.ui.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClosetViewModel(private val repository: ClosetRepository) : ViewModel() {

    private val _filter = MutableStateFlow<ClothingStatus?>(null)
    val filter: StateFlow<ClothingStatus?> = _filter

    val items: StateFlow<List<ClothingItem>> = _filter
        .flatMapLatest { status ->
            if (status == null) repository.observeAllClothing() else repository.observeClothingByStatus(status)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<ClosetRepository.ClosetSummary> = repository.observeSummary()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ClosetRepository.ClosetSummary(0, 0, 0, 0.0),
        )

    fun setFilter(status: ClothingStatus?) {
        _filter.value = status
    }

    fun deleteItem(item: ClothingItem) {
        viewModelScope.launch { repository.deleteClothing(item) }
    }
}
