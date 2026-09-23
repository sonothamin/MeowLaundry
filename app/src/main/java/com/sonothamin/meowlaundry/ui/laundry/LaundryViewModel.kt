package com.sonothamin.meowlaundry.ui.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.LaundryTicket
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LaundryViewModel(repository: ClosetRepository) : ViewModel() {
    val activeTickets: StateFlow<List<LaundryTicket>> = repository.observeActiveTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
