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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SendToLaundryViewModel(private val repository: ClosetRepository) : ViewModel() {

    val availableItems: StateFlow<List<ClothingItem>> = repository.observeClothingByStatus(ClothingStatus.IN_CLOSET)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _serviceType = MutableStateFlow(ServiceType.WASH_AND_PRESS)
    val serviceType: StateFlow<ServiceType> = _serviceType.asStateFlow()

    private val _providerName = MutableStateFlow("")
    val providerName: StateFlow<String> = _providerName.asStateFlow()

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

    fun confirmSend() {
        val ids = _selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val ticketId = repository.sendToLaundry(
                LaundryTicket(
                    serviceType = _serviceType.value,
                    providerName = _providerName.value.trim().ifBlank { null },
                ),
                ids,
            )
            _createdTicketId.value = ticketId
        }
    }
}
