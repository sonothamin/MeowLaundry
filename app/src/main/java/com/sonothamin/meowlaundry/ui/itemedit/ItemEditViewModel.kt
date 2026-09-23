package com.sonothamin.meowlaundry.ui.itemedit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingItemPhoto
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.ClothingType
import com.sonothamin.meowlaundry.data.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemEditUiState(
    val id: Long? = null,
    val title: String = "",
    val type: ClothingType = ClothingType.TOP,
    /** Staged photo for a brand-new article, before it has been saved once and gained an id. */
    val stagedImagePath: String? = null,
    /** Full gallery, only populated once the article exists in the database. */
    val photos: List<ClothingItemPhoto> = emptyList(),
    val priceText: String = "",
    val notes: String = "",
    val status: ClothingStatus = ClothingStatus.IN_CLOSET,
    val isLoading: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val isValid: Boolean get() = title.isNotBlank()

    /** The photo shown as the "main" one: the gallery's primary once one exists, else the staged pick. */
    val primaryImagePath: String?
        get() = photos.firstOrNull { it.isPrimary }?.path ?: stagedImagePath
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
                    _state.value = ItemEditUiState(
                        id = item.id,
                        title = item.title,
                        type = item.type,
                        priceText = item.price?.let { formatPrice(it) } ?: "",
                        notes = item.notes.orEmpty(),
                        status = item.status,
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
            viewModelScope.launch {
                repository.observePhotosForItem(itemId).collect { photos ->
                    _state.update { it.copy(photos = photos, isLoading = false) }
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

    /** Adds a photo picked from the gallery/camera. Multiple photos are allowed; the first one added becomes primary. */
    fun onPhotoPicked(uri: Uri) {
        addPhotoPath(photoStore.importPhoto(uri))
    }

    fun onPhotoCaptured(path: String) {
        addPhotoPath(path)
    }

    private fun addPhotoPath(path: String) {
        val id = _state.value.id
        if (id != null) {
            viewModelScope.launch { repository.addPhoto(id, path) }
        } else {
            // The article doesn't exist yet. Stage this as the single pre-save photo; it becomes
            // the first (primary) gallery entry once the article is saved for the first time.
            val previousStaged = _state.value.stagedImagePath
            if (previousStaged != null) photoStore.delete(previousStaged)
            _state.value = _state.value.copy(stagedImagePath = path)
        }
    }

    /** Removes a photo that already belongs to a saved article's gallery. */
    fun removeGalleryPhoto(photo: ClothingItemPhoto) {
        viewModelScope.launch { repository.removePhoto(photo) }
    }

    /** Makes an existing gallery photo the primary/cover photo. */
    fun setPrimaryPhoto(photo: ClothingItemPhoto) {
        val id = _state.value.id ?: return
        viewModelScope.launch { repository.setPrimaryPhoto(id, photo.id) }
    }

    /** Removes the staged pre-save photo of a brand-new, not-yet-saved article. */
    fun removeStagedPhoto() {
        photoStore.delete(_state.value.stagedImagePath)
        _state.value = _state.value.copy(stagedImagePath = null)
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
            // First save of a brand-new article: register its staged photo in the gallery table too.
            if (current.id == null && current.stagedImagePath != null) {
                repository.addPhoto(newId, current.stagedImagePath)
            }
            _state.value = current.copy(id = newId, saved = true)
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
            _state.value = current.copy(deleted = true)
        }
    }
}
