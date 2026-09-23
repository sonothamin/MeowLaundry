package com.sonothamin.meowlaundry.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.LaundryTicket
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(repository: ClosetRepository) : ViewModel() {
    val tickets: StateFlow<List<LaundryTicket>> = repository.observeAllTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
