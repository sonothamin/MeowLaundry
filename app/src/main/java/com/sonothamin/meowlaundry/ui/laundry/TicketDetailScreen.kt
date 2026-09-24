package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.data.TicketStatus
import com.sonothamin.meowlaundry.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    viewModel: TicketDetailViewModel,
    onBack: () -> Unit,
    onClosed: () -> Unit = onBack,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TicketDetailEvent.Message -> snackbarHostState.showSnackbar(event.text)
                TicketDetailEvent.Closed -> onClosed()
                is TicketDetailEvent.LaunchIntent -> runCatching {
                    context.startActivity(android.content.Intent.createChooser(event.intent, event.chooserTitle))
                }.onFailure {
                    snackbarHostState.showSnackbar("Couldn't open MeowSpool - is it installed?")
                }
            }
        }
    }

    val ticket = state.ticket

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ticket != null) "Ticket #${ticket.id}" else "Ticket") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::previewTicket, enabled = !state.isPrinting) {
                        Icon(Icons.Default.Preview, contentDescription = "Preview label")
                    }
                    IconButton(onClick = viewModel::printTicket, enabled = !state.isPrinting) {
                        if (state.isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
                        } else {
                            Icon(Icons.Default.Print, contentDescription = "Print ticket")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (ticket == null) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("${ticket.serviceType} · ${ticket.providerName ?: "no provider set"}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Status: ${ticket.status.name.lowercase().replace('_', ' ')}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.garments, key = { it.id }) { garment ->
                    val ticketItem = state.ticketItems.find { it.clothingItemId == garment.id }
                    GarmentDecisionRow(
                        garment = garment,
                        ticketItem = ticketItem,
                        pendingReturned = garment.id in state.pendingReturned,
                        pendingLost = garment.id in state.pendingLost,
                        onToggleReturned = { viewModel.togglePendingReturned(garment.id) },
                        onToggleLost = { viewModel.togglePendingLost(garment.id) },
                    )
                }
            }

            if (ticket.status == TicketStatus.CLOSED) {
                OutlinedButton(
                    onClick = viewModel::reopenTicket,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                ) {
                    Text("Reopen ticket")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = viewModel::confirmDecisions,
                        enabled = state.pendingReturned.isNotEmpty() || state.pendingLost.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save")
                    }
                    if (ticket.status == TicketStatus.RECEIVED) {
                        OutlinedButton(onClick = viewModel::closeTicket, modifier = Modifier.weight(1f)) {
                            Text("Close ticket")
                        }
                    }
                }
            }
        }
    }

    state.previewBitmap?.let { bitmap ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text("Label preview") },
            text = {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Rendered ticket label preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPreview(); viewModel.printTicket() }) { Text("Print") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPreview) { Text("Close") }
            },
        )
    }
}

@Composable
private fun GarmentDecisionRow(
    garment: ClothingItem,
    ticketItem: LaundryTicketItem?,
    pendingReturned: Boolean,
    pendingLost: Boolean,
    onToggleReturned: () -> Unit,
    onToggleLost: () -> Unit,
) {
    val decided = ticketItem?.returned == true || ticketItem?.lost == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(garment.title, style = MaterialTheme.typography.titleSmall)
            if (decided) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (ticketItem?.returned == true) "Returned" else "Lost") },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = pendingReturned, onClick = onToggleReturned, label = { Text("Returned") })
                    FilterChip(selected = pendingLost, onClick = onToggleLost, label = { Text("Lost") })
                }
            }
        }
    }
}
