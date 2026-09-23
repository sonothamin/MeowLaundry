package com.sonothamin.meowlaundry.ui.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.data.PrintPreferences
import com.sonothamin.meowlaundry.print.LabelRenderer
import com.sonothamin.meowlaundry.print.MeowSpoolClient
import com.sonothamin.meowlaundry.print.MeowSpoolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TicketDetailUiState(
    val ticket: LaundryTicket? = null,
    val garments: List<ClothingItem> = emptyList(),
    val ticketItems: List<LaundryTicketItem> = emptyList(),
    /** Pending decisions for garments not yet marked returned/lost, keyed by clothing item id. */
    val pendingReturned: Set<Long> = emptySet(),
    val pendingLost: Set<Long> = emptySet(),
    val printMessage: String? = null,
    val isPrinting: Boolean = false,
)

class TicketDetailViewModel(
    private val repository: ClosetRepository,
    private val printPreferences: PrintPreferences,
    private val ticketId: Long,
) : ViewModel() {

    private val pendingReturned = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingLost = MutableStateFlow<Set<Long>>(emptySet())
    private val printMessage = MutableStateFlow<String?>(null)
    private val isPrinting = MutableStateFlow(false)

    val state: StateFlow<TicketDetailUiState> = combine(
        repository.observeTicket(ticketId),
        repository.observeGarmentsForTicket(ticketId),
        repository.observeItemsForTicket(ticketId),
        pendingReturned,
        pendingLost,
    ) { ticket, garments, ticketItems, returned, lost ->
        TicketDetailUiState(ticket, garments, ticketItems, returned, lost)
    }.combine(printMessage) { s, msg -> s.copy(printMessage = msg) }
        .combine(isPrinting) { s, printing -> s.copy(isPrinting = printing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketDetailUiState())

    fun togglePendingReturned(clothingItemId: Long) {
        pendingReturned.value = pendingReturned.value.let { if (clothingItemId in it) it - clothingItemId else it + clothingItemId }
        pendingLost.value = pendingLost.value - clothingItemId
    }

    fun togglePendingLost(clothingItemId: Long) {
        pendingLost.value = pendingLost.value.let { if (clothingItemId in it) it - clothingItemId else it + clothingItemId }
        pendingReturned.value = pendingReturned.value - clothingItemId
    }

    fun confirmDecisions() {
        val returned = pendingReturned.value
        val lost = pendingLost.value
        if (returned.isEmpty() && lost.isEmpty()) return
        viewModelScope.launch {
            repository.resolveTicket(ticketId, returned, lost)
            pendingReturned.value = emptySet()
            pendingLost.value = emptySet()
        }
    }

    fun closeTicket() {
        viewModelScope.launch { repository.closeTicket(ticketId) }
    }

    fun printTicket() {
        viewModelScope.launch {
            isPrinting.value = true
            val ticket = repository.getTicket(ticketId)
            val garments = state.value.garments
            if (ticket == null || garments.isEmpty()) {
                printMessage.value = "Nothing to print yet"
                isPrinting.value = false
                return@launch
            }
            val settings = printPreferences.settings.first()
            if (settings.host.isBlank()) {
                printMessage.value = "Set up your MeowSpool printer in Settings first"
                isPrinting.value = false
                return@launch
            }
            val bitmap = LabelRenderer.renderTicket(ticket, garments)
            val client = MeowSpoolClient(settings)
            printMessage.value = when (val result = client.printBitmap(bitmap)) {
                is MeowSpoolResult.Success -> "Printed (${result.value} rows)"
                is MeowSpoolResult.Failure -> "Print failed: ${result.message}"
            }
            isPrinting.value = false
        }
    }

    fun dismissPrintMessage() {
        printMessage.value = null
    }
}
