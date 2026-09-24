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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One photo in the gallery, whether or not the article has been saved yet.
 * [id] is the DB row id once persisted, or null for a photo still only staged locally
 * (a brand-new article that hasn't been saved for the first time yet).
 */
data class PhotoEntry(
    val id: Long?,
    val path: String,
    val isPrimary: Boolean,
)

data class ItemEditUiState(
    val id: Long? = null,
    val title: String = "",
    val type: ClothingType = ClothingType.TOP,
    /** Every photo, in order. Works the same whether the article exists in the DB yet or not. */
    val photos: List<PhotoEntry> = emptyList(),
    val priceText: String = "",
    val notes: String = "",
    val status: ClothingStatus = ClothingStatus.IN_CLOSET,
    val isLoading: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val isValid: Boolean get() = title.isNotBlank()
    val primaryImagePath: String? get() = photos.firstOrNull { it.isPrimary }?.path
}

class ItemEditViewModel(
    private val repository: ClosetRepository,
    private val photoStore: PhotoStore,
    itemId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditUiState(id = itemId, isLoading = itemId != null))
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = repository.getClothingById(itemId)
                if (item != null) {
                    _state.update {
                        it.copy(
                            id = item.id,
                            title = item.title,
                            type = item.type,
                            priceText = item.price?.let { p -> formatPrice(p) } ?: "",
                            notes = item.notes.orEmpty(),
                            status = item.status,
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            }
            viewModelScope.launch {
                repository.observePhotosForItem(itemId).collect { photos ->
                    _state.update {
                        it.copy(
                            photos = photos.map { p -> PhotoEntry(id = p.id, path = p.path, isPrimary = p.isPrimary) },
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value) }
    }

    fun onTypeChange(value: ClothingType) {
        _state.update { it.copy(type = value) }
    }

    fun onPriceChange(value: String) {
        _state.update { it.copy(priceText = value) }
    }

    fun onNotesChange(value: String) {
        _state.update { it.copy(notes = value) }
    }

    /** Adds one or more photos picked from the gallery. Any number is allowed; the first ever added is primary. */
    fun onPhotosPicked(uris: List<Uri>) {
        uris.forEach { uri -> addPhotoPath(photoStore.importPhoto(uri)) }
    }

    fun onPhotoCaptured(path: String) {
        addPhotoPath(path)
    }

    private fun addPhotoPath(path: String) {
        val itemId = _state.value.id
        if (itemId != null) {
            // Already saved: persist straight to the gallery table; the Flow collected in
            // init refreshes state.photos automatically once the write lands.
            viewModelScope.launch { repository.addPhoto(itemId, path) }
        } else {
            // Not saved yet: stage it locally. All staged photos are registered in the real
            // gallery table (in the same primary order) the first time the article is saved.
            _state.update { current ->
                val makePrimary = current.photos.isEmpty()
                current.copy(photos = current.photos + PhotoEntry(id = null, path = path, isPrimary = makePrimary))
            }
        }
    }

    /** Removes any photo, staged or already persisted. */
    fun removePhoto(photo: PhotoEntry) {
        if (photo.id != null) {
            viewModelScope.launch { repository.removePhotoById(photo.id) }
        } else {
            photoStore.delete(photo.path)
            _state.update { current ->
                val remaining = current.photos.filterNot { it === photo || (it.id == null && it.path == photo.path) }
                val fixed = if (photo.isPrimary && remaining.isNotEmpty() && remaining.none { it.isPrimary }) {
                    remaining.mapIndexed { index, entry -> if (index == 0) entry.copy(isPrimary = true) else entry }
                } else remaining
                current.copy(photos = fixed)
            }
        }
    }

    /** Makes a photo primary/the cover photo, whether staged or already persisted. */
    fun setPrimaryPhoto(photo: PhotoEntry) {
        val itemId = _state.value.id
        if (photo.id != null && itemId != null) {
            viewModelScope.launch { repository.setPrimaryPhoto(itemId, photo.id) }
        } else {
            _state.update { current ->
                current.copy(photos = current.photos.map { it.copy(isPrimary = it.path == photo.path) })
            }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.isValid) return
        viewModelScope.launch {
            val newId = repository.saveClothing(
                ClothingItem(
                    id = current.id ?: 0,
                    title = current.title.trim(),
                    type = current.type,
                    imagePath = current.primaryImagePath,
                    price = current.priceText.toDoubleOrNull(),
                    status = current.status,
                    notes = current.notes.trim().ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            // First save of a brand-new article: register every staged photo in the gallery
            // table too, in the same order (the first one added stays primary via addPhoto).
            if (current.id == null) {
                current.photos.forEach { repository.addPhoto(newId, it.path) }
            }
            _state.update { it.copy(id = newId, saved = true) }
        }
    }

    private fun formatPrice(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /** Deletes the existing article, its gallery, and its photo files (no-op for one still being created). */
    fun delete() {
        val current = _state.value
        val id = current.id ?: return
        viewModelScope.launch {
            repository.deleteClothing(
                ClothingItem(
                    id = id,
                    title = current.title,
                    type = current.type,
                    imagePath = current.primaryImagePath,
                    price = current.priceText.toDoubleOrNull(),
                    status = current.status,
                    notes = current.notes.trim().ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            _state.update { it.copy(deleted = true) }
        }
    }
}
