package com.sonothamin.meowlaundry.ui.articleview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArticleViewUiState(
    val item: ClothingItem? = null,
    val history: List<LaundryTicketItem> = emptyList(),
    val deleted: Boolean = false,
)

class ArticleViewModel(
    private val repository: ClosetRepository,
    private val itemId: Long,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    val state: StateFlow<ArticleViewUiState> = combine(
        repository.observeClothingById(itemId),
        repository.observeHistoryForItem(itemId),
        deleted,
    ) { item, history, isDeleted ->
        ArticleViewUiState(item, history, isDeleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleViewUiState())

    fun delete() {
        val item = state.value.item ?: return
        viewModelScope.launch {
            repository.deleteClothing(item)
            deleted.value = true
        }
    }
}
