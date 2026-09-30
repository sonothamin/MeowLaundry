package com.sonothamin.meowlaundry.ui.laundry

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.data.Resolution
import com.sonothamin.meowlaundry.data.TicketFormat
import com.sonothamin.meowlaundry.data.TicketStatus
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
    /** Launch a print hand-off intent straight at its target app (no chooser). */
    data class LaunchPrint(val intent: android.content.Intent) : TicketDetailEvent()
    data object MeowSpoolMissing : TicketDetailEvent()
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
    /** Unsaved changes only: garment id -> how many units are now back / lost, differing from what's saved. */
    val edits: Map<Long, Resolution> = emptyMap(),
    val isPrinting: Boolean = false,
    val previewBitmap: Bitmap? = null,
    /** The ticket format the preview above was rendered in - decides what the export button does. */
    val previewFormat: TicketFormat = TicketFormat.RECEIPT,
) {
    fun rowFor(garmentId: Long): LaundryTicketItem? = ticketItems.find { it.clothingItemId == garmentId }

    /** Units of this article on the ticket (1 for an ordinary garment). */
    fun quantityFor(garmentId: Long): Int = rowFor(garmentId)?.quantity ?: 1

    fun savedResolution(garmentId: Long): Resolution =
        rowFor(garmentId)?.let { Resolution(it.returnedQuantity, it.lostQuantity) } ?: Resolution(0, 0)

    /** What the row shows: the unsaved pick if there is one, otherwise what's saved. */
    fun resolutionFor(garmentId: Long): Resolution = edits[garmentId] ?: savedResolution(garmentId)

    private fun decisionOf(garmentId: Long, r: Resolution): ItemDecision {
        val q = quantityFor(garmentId)
        return when {
            r.returned + r.lost < q -> ItemDecision.PENDING
            r.returned > 0 -> ItemDecision.RETURNED
            else -> ItemDecision.LOST
        }
    }

    fun savedDecision(garmentId: Long): ItemDecision = decisionOf(garmentId, savedResolution(garmentId))

    /** Coarse per-article state: PENDING while any unit is still out, otherwise RETURNED (any back) or LOST. */
    fun decisionFor(garmentId: Long): ItemDecision = decisionOf(garmentId, resolutionFor(garmentId))

    val hasChanges: Boolean get() = edits.isNotEmpty()

    /** True when, after the current picks are saved, nothing is left out at the laundry. */
    val allAccountedFor: Boolean
        get() = garments.isNotEmpty() && garments.all { decisionFor(it.id) != ItemDecision.PENDING }

    /** Unit totals across the ticket, counting unsaved picks. */
    val totalUnits: Int get() = garments.sumOf { quantityFor(it.id) }
    val returnedUnits: Int get() = garments.sumOf { resolutionFor(it.id).returned }
    val lostUnits: Int get() = garments.sumOf { resolutionFor(it.id).lost }

    /**
     * The fewest units of this article that can be shown as back: units that already came home and
     * have since gone out again on another ticket can't be pulled back from here.
     */
    fun returnedFloor(garment: ClothingItem): Int {
        val saved = savedResolution(garment.id).returned
        return (saved - garment.inClosetQuantity).coerceAtLeast(0)
    }

    val isClosed: Boolean get() = ticket?.status == TicketStatus.CLOSED

    /** Save is offered when something changed, or when everything is back but the ticket is still open. */
    val canSave: Boolean get() = !isClosed && (hasChanges || allAccountedFor)
}

class TicketDetailViewModel(
    private val repository: ClosetRepository,
    private val printDispatcher: PrintDispatcher,
    private val ticketId: Long,
) : ViewModel() {

    private val edits = MutableStateFlow<Map<Long, Resolution>>(emptyMap())
    private val isPrinting = MutableStateFlow(false)
    private val previewBitmap = MutableStateFlow<Bitmap?>(null)
    private val previewFormat = MutableStateFlow(TicketFormat.RECEIPT)

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
        .combine(previewFormat) { s, format -> s.copy(previewFormat = format) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketDetailUiState())

    private fun put(garmentId: Long, wanted: Resolution) {
        val current = state.value
        if (current.isClosed) return
        val garment = current.garments.find { it.id == garmentId } ?: return
        val q = current.quantityFor(garmentId)
        val returned = wanted.returned.coerceIn(current.returnedFloor(garment), q)
        val lost = wanted.lost.coerceIn(0, q - returned)
        val next = Resolution(returned, lost)
        edits.value = edits.value.toMutableMap().also {
            if (next == current.savedResolution(garmentId)) it.remove(garmentId) else it[garmentId] = next
        }
    }

    /** Picks Out / Returned / Lost for a whole article (all its units on this ticket). */
    fun setDecision(garmentId: Long, decision: ItemDecision) {
        val q = state.value.quantityFor(garmentId)
        put(
            garmentId,
            when (decision) {
                ItemDecision.PENDING -> Resolution(0, 0)
                ItemDecision.RETURNED -> Resolution(q, 0)
                ItemDecision.LOST -> Resolution(0, q)
            },
        )
    }

    /** Sets how many units of a multi-unit article are back; the lost count is kept if it still fits. */
    fun setReturnedUnits(garmentId: Long, returned: Int) {
        val cur = state.value.resolutionFor(garmentId)
        put(garmentId, cur.copy(returned = returned))
    }

    fun setLostUnits(garmentId: Long, lost: Int) {
        val cur = state.value.resolutionFor(garmentId)
        put(garmentId, cur.copy(lost = lost))
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
                repository.resolveTicket(ticketId, picked)
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

    /** Sets, changes (or with null, clears) the day this ticket is expected back. */
    fun setDueDate(at: Long?) {
        viewModelScope.launch { repository.setExpectedReturn(ticketId, at) }
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
            previewFormat.value = printDispatcher.currentFormat()
            previewBitmap.value = bitmap
        }
    }

    /**
     * Writes the current ticket as a real text PDF to [output] (e.g. an opened file-picker
     * stream) - used by the preview dialog's export action when the ticket format is
     * [TicketFormat.PAGE], so exporting a document saves a document, not a screenshot of one.
     */
    /** Writes the ticket as a PDF. False means there was nothing to write (no such ticket, or no garments). */
    suspend fun writeTicketPdf(output: java.io.OutputStream): Boolean {
        val ticket = repository.getTicket(ticketId) ?: return false
        val garments = repository.garmentsForPrint(ticketId)
        if (garments.isEmpty()) return false
        printDispatcher.writeTicketPdf(ticket, garments, output)
        return true
    }

    fun dismissPreview() {
        previewBitmap.value = null
    }

    fun printTicket() {
        viewModelScope.launch {
            isPrinting.value = true
            val ticket = repository.getTicket(ticketId)
            val garments = repository.garmentsForPrint(ticketId)
            if (ticket == null || garments.isEmpty()) {
                _events.emit(TicketDetailEvent.Message("Nothing to print yet"))
                isPrinting.value = false
                return@launch
            }
            when (val outcome = printDispatcher.dispatchTicket(ticket, garments)) {
                is PrintOutcome.Printed -> _events.emit(TicketDetailEvent.Message(outcome.message))
                is PrintOutcome.Failed -> _events.emit(TicketDetailEvent.Message(outcome.message))
                is PrintOutcome.ShareReady -> _events.emit(TicketDetailEvent.LaunchPrint(outcome.intent))
                PrintOutcome.MeowSpoolMissing -> _events.emit(TicketDetailEvent.MeowSpoolMissing)
            }
            isPrinting.value = false
        }
    }

    private suspend fun renderCurrentLabel(): Bitmap? {
        val ticket = repository.getTicket(ticketId)
        val garments = repository.garmentsForPrint(ticketId)
        if (ticket == null || garments.isEmpty()) {
            _events.emit(TicketDetailEvent.Message("Nothing to print yet"))
            return null
        }
        return printDispatcher.renderTicket(ticket, garments)
    }
}
