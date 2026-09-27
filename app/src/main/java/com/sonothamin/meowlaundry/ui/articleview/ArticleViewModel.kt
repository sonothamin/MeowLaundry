package com.sonothamin.meowlaundry.ui.articleview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingItemPhoto
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.ItemCareEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArticleViewUiState(
    val item: ClothingItem? = null,
    /** Every laundry trip of this garment, newest first. */
    val events: List<ItemCareEvent> = emptyList(),
    val photos: List<ClothingItemPhoto> = emptyList(),
    val deleted: Boolean = false,
)

/** Neighboring garment ids for swipe navigation; null on either side means "no more that way". */
data class ArticleNavState(val previousId: Long? = null, val nextId: Long? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleViewModel(
    private val repository: ClosetRepository,
    private val itemId: Long,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    val state: StateFlow<ArticleViewUiState> = combine(
        repository.observeClothingById(itemId),
        repository.observeCareEventsForItem(itemId),
        repository.observePhotosForItem(itemId),
        deleted,
    ) { item, events, photos, isDeleted ->
        ArticleViewUiState(item, events, photos, isDeleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleViewUiState())

    /**
     * The list to page through while swiping. Archived garments page through the Archive's own
     * order (most recently archived first); everything else pages through the closet's default
     * "Newest first" order, the same one Closet shows before the person picks a different sort.
     */
    private val orderedIds: StateFlow<List<Long>> = state
        .map { it.item?.status == ClothingStatus.ARCHIVED }
        .distinctUntilChanged()
        .flatMapLatest { isArchived ->
            if (isArchived) {
                repository.observeArchived().map { list -> list.map { it.id } }
            } else {
                repository.observeAllClothing().map { list ->
                    list.filter { it.status != ClothingStatus.ARCHIVED }
                        .sortedByDescending { it.createdAt }
                        .map { it.id }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val navState: StateFlow<ArticleNavState> = orderedIds.map { ids ->
        val index = ids.indexOf(itemId)
        if (index == -1) {
            ArticleNavState()
        } else {
            ArticleNavState(
                previousId = ids.getOrNull(index - 1),
                nextId = ids.getOrNull(index + 1),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleNavState())

    fun delete() {
        val item = state.value.item ?: return
        viewModelScope.launch {
            repository.deleteClothing(item)
            deleted.value = true
        }
    }

    fun archive(reason: ArchiveReason, notes: String?) {
        viewModelScope.launch { repository.archiveItem(itemId, reason, notes) }
    }

    fun unarchive() {
        viewModelScope.launch { repository.unarchiveItem(itemId) }
    }
}
