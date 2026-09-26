package com.sonothamin.meowlaundry.ui.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.data.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One garment on the ticket, together with whether it's still pending or already decided. */
data class TicketGarment(
    val item: ClothingItem,
    val returned: Boolean,
    val lost: Boolean,
) {
    val removable: Boolean get() = !returned && !lost
}

data class EditTicketUiState(
    val loaded: Boolean = false,
    val serviceType: ServiceType = ServiceType.WASH,
    val providerName: String = "",
    val dueAt: Long? = null,
    val notes: String = "",
    val garments: List<TicketGarment> = emptyList(),
    val availableToAdd: List<ClothingItem> = emptyList(),
    val saved: Boolean = false,
) {
    // A ticket always has a service type, so there's nothing to actually invalidate here -
    // this exists so the screen has a single place to extend validation later.
    val isValid: Boolean get() = loaded
}

class EditTicketViewModel(
    private val repository: ClosetRepository,
    private val ticketId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditTicketUiState())
    val state: StateFlow<EditTicketUiState> = _state.asStateFlow()

    /** Providers used on past tickets, most recently used first. */
    private val _providerHistory = MutableStateFlow<List<String>>(emptyList())

    /** Past providers matching what's typed so far, most recently used first, current text excluded. */
    val providerSuggestions: StateFlow<List<String>> = combine(_providerHistory, _state) { history, state ->
        val typed = state.providerName.trim()
        history.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _providerHistory.value = repository.getRecentProviderNames()
        }
        viewModelScope.launch {
            val ticket = repository.getTicket(ticketId) ?: return@launch
            _state.update {
                it.copy(
                    loaded = true,
                    serviceType = ticket.serviceType,
                    providerName = ticket.providerName.orEmpty(),
                    dueAt = ticket.expectedReturnAt,
                    notes = ticket.notes.orEmpty(),
                )
            }
        }
        // Garments currently on the ticket (with their pending/returned/lost state) and the
        // closet items free to be added to it - both update live as the user adds/removes.
        viewModelScope.launch {
            combine(
                repository.observeItemsForTicket(ticketId),
                repository.observeGarmentsForTicket(ticketId),
                repository.observeClothingByStatus(ClothingStatus.IN_CLOSET),
            ) { ticketItems, garments, inCloset ->
                val itemsById = ticketItems.associateBy(LaundryTicketItem::clothingItemId)
                val ticketGarments = garments.map { garment ->
                    val ticketItem = itemsById[garment.id]
                    TicketGarment(item = garment, returned = ticketItem?.returned ?: false, lost = ticketItem?.lost ?: false)
                }
                ticketGarments to inCloset
            }.collect { (ticketGarments, inCloset) ->
                _state.update { it.copy(garments = ticketGarments, availableToAdd = inCloset) }
            }
        }
    }

    fun onServiceTypeChange(value: ServiceType) = _state.update { it.copy(serviceType = value) }
    fun onProviderNameChange(value: String) = _state.update { it.copy(providerName = value) }
    fun onDueAtChange(value: Long?) = _state.update { it.copy(dueAt = value) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    /** Adds one closet garment to the ticket directly - no separate picker, tap it and it's on. */
    fun addGarment(clothingItemId: Long) {
        viewModelScope.launch {
            repository.addGarmentsToTicket(ticketId, listOf(clothingItemId))
        }
    }

    fun removeGarment(clothingItemId: Long) {
        viewModelScope.launch {
            repository.removeGarmentFromTicket(ticketId, clothingItemId)
        }
    }

    fun save() {
        val current = _state.value
        if (!current.isValid) return
        viewModelScope.launch {
            val ticket = repository.getTicket(ticketId) ?: return@launch
            repository.updateTicket(
                ticket.copy(
                    serviceType = current.serviceType,
                    providerName = current.providerName.trim().ifBlank { null },
                    expectedReturnAt = current.dueAt,
                    notes = current.notes.trim().ifBlank { null },
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
