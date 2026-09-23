package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.TicketStatus
import com.sonothamin.meowlaundry.ui.components.EmptyState
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaundryScreen(
    viewModel: LaundryViewModel,
    onSendNew: () -> Unit,
    onOpenTicket: (Long) -> Unit,
) {
    val tickets by viewModel.activeTickets.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Laundry") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onSendNew) {
                Icon(Icons.Default.Add, contentDescription = "Send clothes to laundry")
            }
        },
    ) { padding ->
        if (tickets.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LocalLaundryService,
                title = "Nothing out right now",
                subtitle = "Tap + to send clothes to the wash or press.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    TicketRow(ticket = ticket, onClick = { onOpenTicket(ticket.id) })
                }
            }
        }
    }
}

@Composable
private fun TicketRow(ticket: LaundryTicket, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("Ticket #${ticket.id} · ${ticket.serviceType}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sent ${dateFormat.format(Date(ticket.sentAt))} · ${statusText(ticket.status)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusText(status: TicketStatus): String = when (status) {
    TicketStatus.SENT -> "Out"
    TicketStatus.PARTIALLY_RECEIVED -> "Partially back"
    TicketStatus.RECEIVED -> "All back"
    TicketStatus.CLOSED -> "Closed"
}
