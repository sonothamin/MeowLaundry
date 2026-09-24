package com.sonothamin.meowlaundry.ui.laundry

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.print.LabelRenderer
import com.sonothamin.meowlaundry.print.PrintDispatcher
import com.sonothamin.meowlaundry.print.PrintOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot events the screen must act on (launching a share/print intent, showing a message). */
sealed class TicketDetailEvent {
    data class LaunchIntent(val intent: android.content.Intent, val chooserTitle: String) : TicketDetailEvent()
    data class Message(val text: String) : TicketDetailEvent()

    /** The ticket was closed; the screen has nothing left to do and should leave. */
    data object Closed : TicketDetailEvent()
}

data class TicketDetailUiState(
    val ticket: LaundryTicket? = null,
    val garments: List<ClothingItem> = emptyList(),
    val ticketItems: List<LaundryTicketItem> = emptyList(),
    /** Pending decisions for garments not yet marked returned/lost, keyed by clothing item id. */
    val pendingReturned: Set<Long> = emptySet(),
    val pendingLost: Set<Long> = emptySet(),
    val isPrinting: Boolean = false,
    val previewBitmap: Bitmap? = null,
)

class TicketDetailViewModel(
    private val repository: ClosetRepository,
    private val printDispatcher: PrintDispatcher,
    private val ticketId: Long,
) : ViewModel() {

    private val pendingReturned = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingLost = MutableStateFlow<Set<Long>>(emptySet())
    private val isPrinting = MutableStateFlow(false)
    private val previewBitmap = MutableStateFlow<Bitmap?>(null)

    private val _events = MutableSharedFlow<TicketDetailEvent>()
    val events: SharedFlow<TicketDetailEvent> = _events

    val state: StateFlow<TicketDetailUiState> = combine(
        repository.observeTicket(ticketId),
        repository.observeGarmentsForTicket(ticketId),
        repository.observeItemsForTicket(ticketId),
        pendingReturned,
        pendingLost,
    ) { ticket, garments, ticketItems, returned, lost ->
        TicketDetailUiState(ticket, garments, ticketItems, returned, lost)
    }.combine(isPrinting) { s, printing -> s.copy(isPrinting = printing) }
        .combine(previewBitmap) { s, preview -> s.copy(previewBitmap = preview) }
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

    private var closing = false

    fun closeTicket() {
        if (closing) return // ignore double taps while the close is in flight
        closing = true
        viewModelScope.launch {
            repository.closeTicket(ticketId)
            _events.emit(TicketDetailEvent.Closed)
        }
    }

    /** Renders the label and shows it in a preview dialog, without sending it anywhere. */
    fun previewTicket() {
        viewModelScope.launch {
            val bitmap = renderCurrentLabel() ?: return@launch
            previewBitmap.value = bitmap
        }
    }

    fun dismissPreview() {
        previewBitmap.value = null
    }

    fun printTicket() {
        viewModelScope.launch {
            isPrinting.value = true
            val bitmap = renderCurrentLabel()
            if (bitmap == null) {
                isPrinting.value = false
                return@launch
            }
            when (val outcome = printDispatcher.dispatch(bitmap)) {
                is PrintOutcome.Printed -> _events.emit(TicketDetailEvent.Message(outcome.message))
                is PrintOutcome.Failed -> _events.emit(TicketDetailEvent.Message(outcome.message))
                is PrintOutcome.ShareReady -> _events.emit(
                    TicketDetailEvent.LaunchIntent(outcome.intent, "Print via MeowSpool"),
                )
            }
            isPrinting.value = false
        }
    }

    private suspend fun renderCurrentLabel(): Bitmap? {
        val ticket = repository.getTicket(ticketId)
        val garments = state.value.garments
        if (ticket == null || garments.isEmpty()) {
            _events.emit(TicketDetailEvent.Message("Nothing to print yet"))
            return null
        }
        return LabelRenderer.renderTicket(ticket, garments)
    }
}
