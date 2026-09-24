package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.TicketStatus
import com.sonothamin.meowlaundry.ui.components.EmptyState
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every laundry ticket - active and past - in one place, with long-press multiselect for
 * printing, sharing, exporting or deleting a batch. Replaces what used to be two separate
 * tabs (a simple "active tickets" Laundry screen and a full-featured History screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaundryScreen(
    viewModel: LaundryViewModel,
    onSendNew: () -> Unit,
    onOpenTicket: (Long) -> Unit,
    flashMessage: String? = null,
    /** When set, the flash snackbar offers "Undo", which reopens this ticket. */
    flashUndoTicketId: Long? = null,
    onFlashShown: () -> Unit = {},
) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val closedCount = remember(tickets) { tickets.count { it.status == TicketStatus.CLOSED } }
    val closedSelectedCount = remember(tickets, selectedIds) {
        tickets.count { it.id in selectedIds && it.status == TicketStatus.CLOSED }
    }

    // Message handed back by another screen (e.g. "Ticket #4 closed"). Consume it first so it
    // shows once, then show it in its own coroutine so clearing the key doesn't cancel it.
    LaunchedEffect(flashMessage) {
        if (flashMessage != null) {
            onFlashShown()
            val undoId = flashUndoTicketId
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = flashMessage,
                    actionLabel = if (undoId != null) "Undo" else null,
                    withDismissAction = undoId == null,
                    duration = if (undoId != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed && undoId != null) viewModel.reopenTicket(undoId)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LaundryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is LaundryEvent.LaunchIntent -> runCatching {
                    context.startActivity(android.content.Intent.createChooser(event.intent, event.chooserTitle))
                }.onFailure {
                    snackbarHostState.showSnackbar("Couldn't open MeowSpool - is it installed?")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(targetState = selectionMode, label = "laundry-topbar") { inSelection ->
                if (inSelection) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                            }
                            IconButton(onClick = viewModel::printSelected) {
                                Icon(Icons.Default.Print, contentDescription = "Print")
                            }
                            IconButton(onClick = viewModel::shareSelected) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = { viewModel.exportSelected(context) }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }, enabled = closedSelectedCount > 0) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete closed tickets")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text("Laundry") },
                        actions = {
                            if (closedCount > 0) {
                                IconButton(onClick = { showClearAllConfirm = true }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear closed history")
                                }
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onSendNew) {
                    Icon(Icons.Default.Add, contentDescription = "Send clothes to laundry")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (tickets.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LocalLaundryService,
                title = "Nothing here yet",
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
                    TicketRow(
                        ticket = ticket,
                        selected = ticket.id in selectedIds,
                        onClick = {
                            if (selectionMode) viewModel.toggleSelection(ticket.id) else onOpenTicket(ticket.id)
                        },
                        onLongClick = { viewModel.startSelection(ticket.id) },
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $closedSelectedCount closed ticket(s)?") },
            text = {
                val openSelected = selectedIds.size - closedSelectedCount
                Text(
                    "This removes them from your history and can't be undone." +
                        if (openSelected > 0) " $openSelected open ticket(s) in your selection will be kept." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteSelected() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear $closedCount closed ticket(s)?") },
            text = { Text("Closed tickets are removed from your history for good. Tickets that are still out at the laundry are not touched.") },
            confirmButton = {
                TextButton(onClick = { showClearAllConfirm = false; viewModel.clearClosedHistory() }) { Text("Clear history") }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TicketRow(
    ticket: LaundryTicket,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ticket #${ticket.id} · ${ticket.serviceType}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sent ${dateFormat.format(Date(ticket.sentAt))}" +
                        (ticket.receivedAt?.let { " · received ${dateFormat.format(Date(it))}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusLabel(ticket.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun statusLabel(status: TicketStatus) = when (status) {
    TicketStatus.SENT -> "Out"
    TicketStatus.PARTIALLY_RECEIVED -> "Partially back"
    TicketStatus.RECEIVED -> "All back"
    TicketStatus.CLOSED -> "Closed"
}
