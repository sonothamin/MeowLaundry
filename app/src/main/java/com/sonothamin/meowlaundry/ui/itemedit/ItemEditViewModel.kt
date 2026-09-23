package com.sonothamin.meowlaundry.ui.itemedit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.ClothingType
import com.sonothamin.meowlaundry.data.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ItemEditUiState(
    val id: Long? = null,
    val title: String = "",
    val type: ClothingType = ClothingType.TOP,
    val imagePath: String? = null,
    val priceText: String = "",
    val notes: String = "",
    val status: ClothingStatus = ClothingStatus.IN_CLOSET,
    val isLoading: Boolean = false,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val isValid: Boolean get() = title.isNotBlank()
}

class ItemEditViewModel(
    private val repository: ClosetRepository,
    private val photoStore: PhotoStore,
    itemId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditUiState(isLoading = itemId != null))
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = repository.getClothingById(itemId)
                if (item != null) {
                    _state.value = ItemEditUiState(
                        id = item.id,
                        title = item.title,
                        type = item.type,
                        imagePath = item.imagePath,
                        priceText = item.price?.let { formatPrice(it) } ?: "",
                        notes = item.notes.orEmpty(),
                        status = item.status,
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.value = _state.value.copy(title = value)
    }

    fun onTypeChange(value: ClothingType) {
        _state.value = _state.value.copy(type = value)
    }

    fun onPriceChange(value: String) {
        _state.value = _state.value.copy(priceText = value)
    }

    fun onNotesChange(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun onPhotoPicked(uri: Uri) {
        val previous = _state.value.imagePath
        val newPath = photoStore.importPhoto(uri)
        photoStore.delete(previous)
        _state.value = _state.value.copy(imagePath = newPath)
    }

    fun onPhotoCaptured(path: String) {
        val previous = _state.value.imagePath
        if (previous != null && previous != path) photoStore.delete(previous)
        _state.value = _state.value.copy(imagePath = path)
    }

    fun removePhoto() {
        photoStore.delete(_state.value.imagePath)
        _state.value = _state.value.copy(imagePath = null)
    }

    fun save() {
        val current = _state.value
        if (!current.isValid) return
        viewModelScope.launch {
            repository.saveClothing(
                ClothingItem(
                    id = current.id ?: 0,
                    title = current.title.trim(),
                    type = current.type,
                    imagePath = current.imagePath,
                    price = current.priceText.toDoubleOrNull(),
                    status = current.status,
                    notes = current.notes.trim().ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            _state.value = current.copy(saved = true)
        }
    }

    private fun formatPrice(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
