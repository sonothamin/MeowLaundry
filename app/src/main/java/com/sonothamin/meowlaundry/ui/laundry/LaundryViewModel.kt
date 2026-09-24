package com.sonothamin.meowlaundry.ui.laundry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.export.ExportUtils
import com.sonothamin.meowlaundry.print.LabelRenderer
import com.sonothamin.meowlaundry.print.PrintDispatcher
import com.sonothamin.meowlaundry.print.PrintOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot events the screen must act on (launching a share/print intent, showing a message). */
sealed class LaundryEvent {
    data class LaunchIntent(val intent: android.content.Intent, val chooserTitle: String) : LaundryEvent()
    data class Message(val text: String) : LaundryEvent()
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

    fun selectAll() {
        _selectedIds.value = tickets.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteTicketsByIds(ids)
            _selectedIds.value = emptySet()
            _events.emit(LaundryEvent.Message("Deleted ${ids.size} ticket(s)"))
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllTickets()
            _selectedIds.value = emptySet()
            _events.emit(LaundryEvent.Message("History cleared"))
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
            bitmaps += LabelRenderer.renderTicket(ticket, garments)
        }
        return bitmaps
    }
}
