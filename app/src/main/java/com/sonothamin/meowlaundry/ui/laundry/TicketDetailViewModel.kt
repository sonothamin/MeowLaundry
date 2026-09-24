package com.sonothamin.meowlaundry.ui.laundry

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.data.TicketStatus
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

/** Where one garment stands on a ticket. PENDING = still out at the laundry. */
enum class ItemDecision { PENDING, RETURNED, LOST }

data class TicketDetailUiState(
    val ticket: LaundryTicket? = null,
    val garments: List<ClothingItem> = emptyList(),
    val ticketItems: List<LaundryTicketItem> = emptyList(),
    /** Unsaved changes only: garment id -> the decision the user picked, which differs from what's saved. */
    val edits: Map<Long, ItemDecision> = emptyMap(),
    val isPrinting: Boolean = false,
    val previewBitmap: Bitmap? = null,
) {
    fun savedDecision(garmentId: Long): ItemDecision {
        val item = ticketItems.find { it.clothingItemId == garmentId } ?: return ItemDecision.PENDING
        return when {
            item.returned -> ItemDecision.RETURNED
            item.lost -> ItemDecision.LOST
            else -> ItemDecision.PENDING
        }
    }

    /** What the row shows: the unsaved pick if there is one, otherwise what's saved. */
    fun decisionFor(garmentId: Long): ItemDecision = edits[garmentId] ?: savedDecision(garmentId)

    val hasChanges: Boolean get() = edits.isNotEmpty()

    /** True when, after the current picks are saved, nothing is left out at the laundry. */
    val allAccountedFor: Boolean
        get() = garments.isNotEmpty() && garments.all { decisionFor(it.id) != ItemDecision.PENDING }

    val isClosed: Boolean get() = ticket?.status == TicketStatus.CLOSED

    /** Save is offered when something changed, or when everything is back but the ticket is still open. */
    val canSave: Boolean get() = !isClosed && (hasChanges || allAccountedFor)
}

class TicketDetailViewModel(
    private val repository: ClosetRepository,
    private val printDispatcher: PrintDispatcher,
    private val ticketId: Long,
) : ViewModel() {

    private val edits = MutableStateFlow<Map<Long, ItemDecision>>(emptyMap())
    private val isPrinting = MutableStateFlow(false)
    private val previewBitmap = MutableStateFlow<Bitmap?>(null)

    private val _events = MutableSharedFlow<TicketDetailEvent>()
    val events: SharedFlow<TicketDetailEvent> = _events

    val state: StateFlow<TicketDetailUiState> = combine(
        repository.observeTicket(ticketId),
        repository.observeGarmentsForTicket(ticketId),
        repository.observeItemsForTicket(ticketId),
        edits,
    ) { ticket, garments, ticketItems, edits ->
        TicketDetailUiState(ticket, garments, ticketItems, edits)
    }.combine(isPrinting) { s, printing -> s.copy(isPrinting = printing) }
        .combine(previewBitmap) { s, preview -> s.copy(previewBitmap = preview) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketDetailUiState())

    /** Picks a decision for one garment. Picking what's already saved simply clears the unsaved change. */
    fun setDecision(garmentId: Long, decision: ItemDecision) {
        val current = state.value
        if (current.isClosed) return
        edits.value = edits.value.toMutableMap().also {
            if (decision == current.savedDecision(garmentId)) it.remove(garmentId) else it[garmentId] = decision
        }
    }

    private var busy = false

    /**
     * Saves the picks. If that leaves nothing out at the laundry the ticket is closed in the same step,
     * so "save" and "close" are one action. (Closing can be undone: reopen it, and any garment's
     * decision can be changed again.)
     */
    fun save() {
        val current = state.value
        if (busy || !current.canSave) return
        busy = true
        val picked = edits.value
        val closesTicket = current.allAccountedFor
        viewModelScope.launch {
            if (picked.isNotEmpty()) {
                repository.resolveTicket(
                    ticketId = ticketId,
                    returnedItemIds = picked.filterValues { it == ItemDecision.RETURNED }.keys,
                    lostItemIds = picked.filterValues { it == ItemDecision.LOST }.keys,
                    resetItemIds = picked.filterValues { it == ItemDecision.PENDING }.keys,
                )
                edits.value = emptyMap()
            }
            if (closesTicket) {
                repository.closeTicket(ticketId)
                _events.emit(TicketDetailEvent.Closed) // stay busy: the screen is leaving
            } else {
                _events.emit(TicketDetailEvent.Message("Saved"))
                busy = false
            }
        }
    }

    /** Undoes a close: the ticket goes back to being editable and can be closed again later. */
    fun reopenTicket() {
        viewModelScope.launch {
            repository.reopenTicket(ticketId)
            busy = false
            _events.emit(TicketDetailEvent.Message("Ticket reopened"))
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
