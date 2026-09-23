package com.sonothamin.meowlaundry.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shows garments that have been permanently taken out of rotation (donated, sold,
 * discarded, lost, etc.) - kept for the record but out of the way of the everyday closet.
 */
class ArchiveViewModel(private val repository: ClosetRepository) : ViewModel() {

    private val _reasonFilter = MutableStateFlow<ArchiveReason?>(null)
    val reasonFilter: StateFlow<ArchiveReason?> = _reasonFilter

    private val allArchived: StateFlow<List<ClothingItem>> = repository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<ClothingItem>> = combine(allArchived, _reasonFilter) { all, reason ->
        if (reason == null) all else all.filter { it.archiveReason == reason }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setReasonFilter(reason: ArchiveReason?) {
        _reasonFilter.value = reason
    }

    fun unarchive(id: Long) {
        viewModelScope.launch { repository.unarchiveItem(id) }
    }
}
