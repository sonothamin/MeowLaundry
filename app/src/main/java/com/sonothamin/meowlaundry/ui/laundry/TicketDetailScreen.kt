package com.sonothamin.meowlaundry.ui.laundry

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.DueDates
import com.sonothamin.meowlaundry.data.DueState
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.TicketStatus
import com.sonothamin.meowlaundry.ui.components.DueChip
import com.sonothamin.meowlaundry.ui.components.DueDatePickerDialog
import com.sonothamin.meowlaundry.ui.components.InfoBlock
import com.sonothamin.meowlaundry.ui.components.InfoCell
import com.sonothamin.meowlaundry.ui.components.rememberNotificationPermissionRequester
import com.sonothamin.meowlaundry.ui.components.serviceIcon
import com.sonothamin.meowlaundry.ui.components.serviceLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ticket detail: an at-a-glance info block, progress, and one card per garment with a
 * Pending / Returned / Lost selector. Choices stay editable after saving (pick Pending to undo), and
 * Save closes the ticket by itself once every garment is accounted for.
 */
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
    var showDuePicker by remember { mutableStateOf(false) }
    val askNotificationPermission = rememberNotificationPermissionRequester()

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

        val closed = state.isClosed
        val total = state.garments.size
        val returnedCount = state.garments.count { state.decisionFor(it.id) == ItemDecision.RETURNED }
        val lostCount = state.garments.count { state.decisionFor(it.id) == ItemDecision.LOST }
        val accounted = returnedCount + lostCount

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item { TicketInfo(ticket = ticket, itemCount = total) }

                if (DueDates.isOpen(ticket)) {
                    item { DueCard(ticket = ticket, onClick = { showDuePicker = true }) }
                }

                item {
                    ProgressCard(
                        accounted = accounted,
                        total = total,
                        returned = returnedCount,
                        lost = lostCount,
                    )
                }

                if (closed) {
                    item { ClosedBanner() }
                }

                if (!ticket.notes.isNullOrBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ticket.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Garments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "$total",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(state.garments, key = { it.id }) { garment ->
                    val saved = state.savedDecision(garment.id)
                    val lockedReason = when {
                        closed -> null // the banner already explains it
                        saved != ItemDecision.PENDING && garment.status == ClothingStatus.AT_LAUNDRY ->
                            "Out again on another ticket, so this can't be changed here"
                        // Lost garments are archived as "Lost" - that one stays undoable from here.
                        garment.status == ClothingStatus.ARCHIVED &&
                            !(saved == ItemDecision.LOST && garment.archiveReason == ArchiveReason.LOST) ->
                            "Archived, so this can't be changed here"
                        else -> null
                    }
                    GarmentDecisionCard(
                        garment = garment,
                        decision = state.decisionFor(garment.id),
                        edited = garment.id in state.edits,
                        locked = closed || lockedReason != null,
                        lockedReason = lockedReason,
                        onDecision = { viewModel.setDecision(garment.id, it) },
                    )
                }
            }

            // One action for both saving and closing.
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                if (closed) {
                    FilledTonalButton(onClick = viewModel::reopenTicket, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Reopen ticket", modifier = Modifier.padding(start = Spacing.sm))
                    }
                } else {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (state.allAccountedFor) Icons.Default.DoneAll else Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (state.allAccountedFor) "Save & close ticket" else "Save",
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }

    if (showDuePicker && ticket != null) {
        DueDatePickerDialog(
            initial = ticket.expectedReturnAt,
            onDismiss = { showDuePicker = false },
            onConfirm = {
                showDuePicker = false
                viewModel.setDueDate(it)
                askNotificationPermission()
            },
            onClear = if (ticket.expectedReturnAt != null) {
                { showDuePicker = false; viewModel.setDueDate(null) }
            } else null,
        )
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
private fun TicketInfo(ticket: LaundryTicket, itemCount: Int) {
    InfoBlock(
        rows = listOf(
            listOf(
                InfoCell("Service", serviceLabel(ticket.serviceType), serviceIcon(ticket.serviceType)),
                InfoCell("Provider", ticket.providerName?.takeIf { it.isNotBlank() } ?: "Not set", Icons.Default.Storefront),
                InfoCell("Status", ticketStatusLabel(ticket.status), ticketStatusIcon(ticket.status), valueColor = ticketStatusColor(ticket.status)),
            ),
            listOf(
                dateInfoCell("Sent", Icons.Default.CalendarToday, ticket.sentAt),
                InfoCell("Garments", itemCount.toString(), Icons.Default.Checkroom, sub = if (itemCount == 1) "item" else "items"),
                if (ticket.receivedAt != null) {
                    dateInfoCell("Received", Icons.Default.EventAvailable, ticket.receivedAt)
                } else {
                    InfoCell("Received", "Not yet", Icons.Default.EventAvailable)
                },
            ),
        ),
    )
}

private fun dateInfoCell(label: String, icon: ImageVector, at: Long): InfoCell {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
    val relative = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    return InfoCell(label, date, icon, sub = relative)
}

/** Tappable "Due back" card: shows the date and how close it is, and opens the date picker. */
@Composable
private fun DueCard(ticket: LaundryTicket, onClick: () -> Unit) {
    val info = DueDates.info(ticket)
    val overdue = info?.state == DueState.OVERDUE
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                if (overdue) Icons.Default.Warning else Icons.Default.Event,
                contentDescription = null,
                tint = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Due back",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ticket.expectedReturnAt?.let { DueDates.format(it) } ?: "No date set",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (info != null) {
                DueChip(info)
            } else {
                Text("Set date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProgressCard(accounted: Int, total: Int, returned: Int, lost: Int) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Back from the laundry",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                )
                Text("$accounted of $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else accounted.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            if (returned > 0 || lost > 0) {
                Text(
                    listOfNotNull(
                        if (returned > 0) "$returned returned" else null,
                        if (lost > 0) "$lost lost" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClosedBanner() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "This ticket is closed. Reopen it to change anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun GarmentDecisionCard(
    garment: ClothingItem,
    decision: ItemDecision,
    edited: Boolean,
    locked: Boolean,
    lockedReason: String?,
    onDecision: (ItemDecision) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (garment.imagePath != null) {
                        AsyncImage(
                            model = garment.imagePath,
                            contentDescription = garment.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Default.Checkroom, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(garment.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            garment.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            garment.brand?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (edited) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Unsaved change",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            val options = ItemDecision.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    val (container, content) = decisionColors(option)
                    SegmentedButton(
                        selected = decision == option,
                        onClick = { onDecision(option) },
                        enabled = !locked,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = container,
                            activeContentColor = content,
                        ),
                        icon = {
                            Icon(
                                decisionIcon(option),
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        },
                        label = { Text(decisionLabel(option), maxLines = 1) },
                    )
                }
            }

            if (lockedReason != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(lockedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun decisionLabel(d: ItemDecision) = when (d) {
    ItemDecision.PENDING -> "Out"
    ItemDecision.RETURNED -> "Returned"
    ItemDecision.LOST -> "Lost"
}

private fun decisionIcon(d: ItemDecision): ImageVector = when (d) {
    ItemDecision.PENDING -> Icons.Default.Schedule
    ItemDecision.RETURNED -> Icons.Default.CheckCircle
    ItemDecision.LOST -> Icons.Default.ReportProblem
}

@Composable
private fun decisionColors(d: ItemDecision): Pair<Color, Color> = when (d) {
    ItemDecision.PENDING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    ItemDecision.RETURNED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    ItemDecision.LOST -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

private fun ticketStatusLabel(status: TicketStatus) = when (status) {
    TicketStatus.SENT -> "Out"
    TicketStatus.PARTIALLY_RECEIVED -> "Partly back"
    TicketStatus.RECEIVED -> "All back"
    TicketStatus.CLOSED -> "Closed"
}

private fun ticketStatusIcon(status: TicketStatus): ImageVector = when (status) {
    TicketStatus.SENT -> Icons.Default.Schedule
    TicketStatus.PARTIALLY_RECEIVED -> Icons.Default.Sync
    TicketStatus.RECEIVED -> Icons.Default.CheckCircle
    TicketStatus.CLOSED -> Icons.Default.Lock
}

@Composable
private fun ticketStatusColor(status: TicketStatus): Color = when (status) {
    TicketStatus.SENT, TicketStatus.PARTIALLY_RECEIVED -> MaterialTheme.colorScheme.tertiary
    TicketStatus.RECEIVED -> MaterialTheme.colorScheme.primary
    TicketStatus.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
}
