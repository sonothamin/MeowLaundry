package com.sonothamin.meowlaundry.ui.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SendToLaundryViewModel(
    private val repository: ClosetRepository,
    preselectedIds: Set<Long> = emptySet(),
) : ViewModel() {

    val availableItems: StateFlow<List<ClothingItem>> = repository.observeClothingByStatus(ClothingStatus.IN_CLOSET)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow(preselectedIds)
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _serviceType = MutableStateFlow(ServiceType.WASH_AND_PRESS)
    val serviceType: StateFlow<ServiceType> = _serviceType.asStateFlow()

    private val _providerName = MutableStateFlow("")
    val providerName: StateFlow<String> = _providerName.asStateFlow()

    /** Providers used on past tickets, most recently used first. */
    private val _providerHistory = MutableStateFlow<List<String>>(emptyList())

    /** Past providers matching what's typed so far, most recently used first, current text excluded. */
    val providerSuggestions: StateFlow<List<String>> = combine(_providerHistory, _providerName) { history, typed ->
        history.filter { it.contains(typed.trim(), ignoreCase = true) && !it.equals(typed.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _providerHistory.value = repository.getRecentProviderNames()
        }
    }

    /** Start of the day the garments are expected back, or null for no due date. */
    private val _dueAt = MutableStateFlow<Long?>(null)
    val dueAt: StateFlow<Long?> = _dueAt.asStateFlow()

    fun setDueAt(at: Long?) {
        _dueAt.value = at
    }

    private val _createdTicketId = MutableStateFlow<Long?>(null)
    val createdTicketId: StateFlow<Long?> = _createdTicketId.asStateFlow()

    fun toggleSelection(id: Long) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun setServiceType(type: ServiceType) {
        _serviceType.value = type
    }

    fun setProviderName(name: String) {
        _providerName.value = name
    }

    private var sending = false

    fun confirmSend() {
        val ids = _selected.value.toList()
        if (ids.isEmpty() || sending) return // ignore double taps: one tap = one ticket
        sending = true
        viewModelScope.launch {
            val ticketId = repository.sendToLaundry(
                LaundryTicket(
                    serviceType = _serviceType.value,
                    providerName = _providerName.value.trim().ifBlank { null },
                    expectedReturnAt = _dueAt.value,
                ),
                ids,
            )
            _createdTicketId.value = ticketId
        }
    }
}
