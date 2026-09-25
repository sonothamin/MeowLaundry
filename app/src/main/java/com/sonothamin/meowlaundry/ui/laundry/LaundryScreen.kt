package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.TicketStatus
import com.sonothamin.meowlaundry.ui.components.EmptyState
import com.sonothamin.meowlaundry.data.DueDates
import com.sonothamin.meowlaundry.data.DueState
import com.sonothamin.meowlaundry.ui.theme.Spacing

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
    /** Opens the nav drawer. Null hides the hamburger icon. */
    onMenuClick: (() -> Unit)? = null,
) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val filteredOverviews by viewModel.filteredOverviews.collectAsStateWithLifecycle()
    val ticketFilter by viewModel.ticketFilter.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The extended FAB collapses to an icon once the list scrolls, out of the way of the cards.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val overdueCount = remember(tickets) { tickets.count { DueDates.info(it)?.state == DueState.OVERDUE } }
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    LargeTopAppBar(
                        title = { Text("Laundry") },
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            onMenuClick?.let {
                                IconButton(onClick = it) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                                }
                            }
                        },
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
                ExtendedFloatingActionButton(
                    onClick = onSendNew,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New ticket") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!selectionMode) {
                TicketFilterRow(selected = ticketFilter, onSelect = viewModel::setTicketFilter)
            }

            if (tickets.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.LocalLaundryService,
                    title = "Nothing here yet",
                    subtitle = "Tap + to send clothes to the wash or press.",
                )
            } else if (filteredOverviews.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.LocalLaundryService,
                    title = if (ticketFilter == TicketFilter.CLOSED) "No history yet" else "Nothing active",
                    subtitle = if (ticketFilter == TicketFilter.CLOSED) {
                        "Tickets show up here once they're closed."
                    } else {
                        "Nothing is out at the laundry right now."
                    },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // extra bottom room so the FAB never covers the last card
                    contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (overdueCount > 0 && !selectionMode && ticketFilter != TicketFilter.CLOSED) {
                        item(key = "overdue-banner") { OverdueBanner(overdueCount) }
                    }
                    items(filteredOverviews, key = { it.ticket.id }) { overview ->
                        TicketCard(
                            overview = overview,
                            selected = overview.ticket.id in selectedIds,
                            onClick = {
                                if (selectionMode) viewModel.toggleSelection(overview.ticket.id) else onOpenTicket(overview.ticket.id)
                            },
                            onLongClick = { viewModel.startSelection(overview.ticket.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
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

@Composable
private fun TicketFilterRow(selected: TicketFilter, onSelect: (TicketFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selected == TicketFilter.ACTIVE,
            onClick = { onSelect(TicketFilter.ACTIVE) },
            label = { Text("Active") },
        )
        FilterChip(
            selected = selected == TicketFilter.CLOSED,
            onClick = { onSelect(TicketFilter.CLOSED) },
            label = { Text("Closed") },
        )
        FilterChip(
            selected = selected == TicketFilter.ALL,
            onClick = { onSelect(TicketFilter.ALL) },
            label = { Text("All") },
        )
    }
}

@Composable
private fun OverdueBanner(count: Int) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(
                    if (count == 1) "1 ticket is overdue" else "$count tickets are overdue",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Check with your laundry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

