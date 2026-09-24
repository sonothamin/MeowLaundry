package com.sonothamin.meowlaundry.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val stats: StatsData,
    /** False until the first ticket is ever created, when there's nothing to show yet. */
    val hasAnyTrips: Boolean,
)

class StatsViewModel(repository: ClosetRepository) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.LAST_6_MONTHS)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    fun setPeriod(period: StatsPeriod) {
        _period.value = period
    }

    /** Null while the first calculation runs. */
    val state: StateFlow<StatsUiState?> = combine(
        repository.observeAllTickets(),
        repository.observeAllTicketItems(),
        repository.observeAllClothing(),
        _period,
    ) { tickets, ticketItems, clothing, period ->
        StatsUiState(
            stats = computeStats(tickets, ticketItems, clothing, period),
            hasAnyTrips = tickets.isNotEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
