package com.sonothamin.meowlaundry.ui.laundry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.export.ExportUtils
import com.sonothamin.meowlaundry.print.PrintDispatcher
import com.sonothamin.meowlaundry.print.PrintOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which tickets the list shows. Defaults to Active: the history is one tap away, not the default view. */
enum class TicketFilter { ACTIVE, CLOSED, ALL }

/** One-shot events the screen must act on (launching a share/print intent, showing a message). */
sealed class LaundryEvent {
    data class LaunchIntent(val intent: android.content.Intent, val chooserTitle: String) : LaundryEvent()
    data class Message(val text: String) : LaundryEvent()
}

/** A ticket plus the numbers and thumbnails its list card shows. */
data class TicketOverview(
    val ticket: LaundryTicket,
    val total: Int,
    val returned: Int,
    val lost: Int,
    /** Photo paths of the first few garments; null entries have no photo (show a placeholder). */
    val thumbs: List<String?>,
) {
    /** Garments that are settled one way or the other. */
    val accountedFor: Int get() = returned + lost
}

/**
 * Every laundry ticket, past and present, with select/print/share/export/delete actions.
 * This used to be split across a simple "active tickets" Laundry tab and a separate
 * History tab; they did the same job, so this one screen now covers both.
 */
class LaundryViewModel(
    private val repository: ClosetRepository,
    private val printDispatcher: PrintDispatcher,
) : ViewModel() {

    val tickets: StateFlow<List<LaundryTicket>> = repository.observeAllTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ticketFilter = MutableStateFlow(TicketFilter.ACTIVE)
    val ticketFilter: StateFlow<TicketFilter> = _ticketFilter

    fun setTicketFilter(filter: TicketFilter) {
        _ticketFilter.value = filter
    }

    /** Unfiltered: used for the overdue banner, the "clear history" action and its counts. */
    val overviews: StateFlow<List<TicketOverview>> = combine(
        repository.observeAllTickets(),
        repository.observeAllTicketItems(),
        repository.observeAllClothing(),
    ) { tickets, items, clothing ->
        val imageById = clothing.associate { it.id to it.imagePath }
        val itemsByTicket = items.groupBy { it.ticketId }
        tickets.map { ticket ->
            val its = itemsByTicket[ticket.id].orEmpty()
            TicketOverview(
                ticket = ticket,
                total = its.size,
                returned = its.count { it.returned },
                lost = its.count { it.lost },
                thumbs = its.take(MAX_THUMBS).map { imageById[it.clothingItemId] },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the list actually renders, narrowed by [ticketFilter]. */
    val filteredOverviews: StateFlow<List<TicketOverview>> = combine(overviews, _ticketFilter) { all, filter ->
        when (filter) {
            TicketFilter.ACTIVE -> all.filter { it.ticket.status != com.sonothamin.meowlaundry.data.TicketStatus.CLOSED }
            TicketFilter.CLOSED -> all.filter { it.ticket.status == com.sonothamin.meowlaundry.data.TicketStatus.CLOSED }
            TicketFilter.ALL -> all
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    val selectionMode: StateFlow<Boolean> = _selectedIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = MutableSharedFlow<LaundryEvent>()
    val events: SharedFlow<LaundryEvent> = _events

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    /** Selects everything currently visible under the active filter, not the whole history. */
    fun selectAll() {
        _selectedIds.value = filteredOverviews.value.map { it.ticket.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            // Only closed tickets can be deleted; open ones still have garments at the laundry.
            val deleted = repository.deleteClosedTicketsByIds(ids)
            _selectedIds.value = emptySet()
            val skipped = ids.size - deleted
            _events.emit(
                LaundryEvent.Message(
                    when {
                        deleted == 0 -> "Only closed tickets can be deleted"
                        skipped > 0 -> "Deleted $deleted closed ticket(s); $skipped open ticket(s) kept"
                        else -> "Deleted $deleted ticket(s)"
                    }
                )
            )
        }
    }

    /** Clears history = removes closed tickets only. Open tickets and their garments stay put. */
    fun clearClosedHistory() {
        viewModelScope.launch {
            val cleared = repository.clearClosedTickets()
            _selectedIds.value = emptySet()
            _events.emit(
                LaundryEvent.Message(
                    if (cleared == 0) "No closed tickets to clear" else "Cleared $cleared closed ticket(s)"
                )
            )
        }
    }

    /** Used by the "Undo" on the "Ticket closed" snackbar. */
    fun reopenTicket(ticketId: Long) {
        viewModelScope.launch {
            repository.reopenTicket(ticketId)
            _events.emit(LaundryEvent.Message("Ticket #$ticketId reopened"))
        }
    }

    fun shareSelected() {
        viewModelScope.launch {
            val selected = repository.getTicketsByIds(_selectedIds.value.toList())
            if (selected.isEmpty()) return@launch
            val intent = ExportUtils.shareTextIntent(
                ExportUtils.ticketsSummaryText(selected)
            )
            _events.emit(LaundryEvent.LaunchIntent(intent, "Share history"))
        }
    }

    fun exportSelected(context: Context) {
        viewModelScope.launch {
            val selected = repository.getTicketsByIds(_selectedIds.value.toList())
            if (selected.isEmpty()) return@launch
            val intent = ExportUtils.shareFileIntent(
                context,
                "history-export.csv",
                ExportUtils.ticketsCsv(selected),
                "text/csv",
            )
            _events.emit(LaundryEvent.LaunchIntent(intent, "Export history"))
        }
    }

    fun printSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return@launch
            val bitmaps = renderSelectedLabels(ids)
            if (bitmaps.isEmpty()) {
                _events.emit(LaundryEvent.Message("Nothing to print"))
                return@launch
            }
            when (val outcome = printDispatcher.dispatchMultiple(bitmaps)) {
                is PrintOutcome.Printed -> _events.emit(LaundryEvent.Message(outcome.message))
                is PrintOutcome.Failed -> _events.emit(LaundryEvent.Message(outcome.message))
                is PrintOutcome.ShareReady -> _events.emit(LaundryEvent.LaunchIntent(outcome.intent, "Print via MeowSpool"))
            }
        }
    }

    private suspend fun renderSelectedLabels(ids: List<Long>): List<android.graphics.Bitmap> {
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        ids.forEach { id ->
            val ticket = repository.getTicket(id) ?: return@forEach
            val garments = repository.observeGarmentsForTicket(id).first()
            bitmaps += printDispatcher.renderTicket(ticket, garments)
        }
        return bitmaps
    }
}

private const val MAX_THUMBS = 4
