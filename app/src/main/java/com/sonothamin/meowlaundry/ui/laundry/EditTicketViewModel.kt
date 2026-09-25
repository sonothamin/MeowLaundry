package com.sonothamin.meowlaundry.ui.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditTicketUiState(
    val loaded: Boolean = false,
    val serviceType: ServiceType = ServiceType.WASH,
    val providerName: String = "",
    val dueAt: Long? = null,
    val notes: String = "",
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

    init {
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
    }

    fun onServiceTypeChange(value: ServiceType) = _state.update { it.copy(serviceType = value) }
    fun onProviderNameChange(value: String) = _state.update { it.copy(providerName = value) }
    fun onDueAtChange(value: Long?) = _state.update { it.copy(dueAt = value) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

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
