package com.sonothamin.meowlaundry.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
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
class ArchiveViewModel(
    private val repository: ClosetRepository,
    initialReasonFilter: ArchiveReason? = null,
) : ViewModel() {

    private val _reasonFilter = MutableStateFlow(initialReasonFilter)
    val reasonFilter: StateFlow<ArchiveReason?> = _reasonFilter

    private val allArchived: StateFlow<List<ClothingItem>> = repository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // A garment can be "lost" two ways: archived with reason LOST, or just marked lost from the
    // closet without ever being formally archived. The Lost filter here means "lost", full stop,
    // so it also pulls in that second group - otherwise the Closet screen's Lost tile would land
    // you on a page that's empty even though it just told you there are lost garments.
    private val lostNotArchived: StateFlow<List<ClothingItem>> = repository.observeClothingByStatus(ClothingStatus.LOST)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<ClothingItem>> = combine(allArchived, lostNotArchived, _reasonFilter) { archived, lost, reason ->
        when (reason) {
            null -> archived
            ArchiveReason.LOST -> archived.filter { it.archiveReason == reason } + lost
            else -> archived.filter { it.archiveReason == reason }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setReasonFilter(reason: ArchiveReason?) {
        _reasonFilter.value = reason
    }

    fun unarchive(id: Long) {
        viewModelScope.launch { repository.unarchiveItem(id) }
    }
}
