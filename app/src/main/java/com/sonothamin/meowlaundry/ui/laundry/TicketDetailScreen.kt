package com.sonothamin.meowlaundry.ui.laundry

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.ui.components.QuantityStepper
import com.sonothamin.meowlaundry.data.Resolution
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.TicketFormat
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
    onEdit: (Long) -> Unit,
    onClosed: () -> Unit = onBack,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Lets the person save the rendered label as a PNG anywhere they like (Downloads, Photos, a
    // cloud folder...) via the system file picker - no storage permission needed for this.
    var pendingExportBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val bitmap = pendingExportBitmap
        pendingExportBitmap = null
        if (uri != null && bitmap != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
    }
    // Same idea, but for the real text-PDF ticket format: a document should export as a
    // document, not a rasterized picture of one.
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> viewModel.writeTicketPdf(out) }
                }
            }
        }
    }
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
                    if (ticket != null) {
                        IconButton(onClick = { onEdit(ticket.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ticket")
                        }
                    }
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
        // Units, not articles: a row of 5 socks is 5 of the ticket's items.
        val total = state.totalUnits
        val returnedCount = state.returnedUnits
        val lostCount = state.lostUnits
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
                    val savedRes = state.savedResolution(garment.id)
                    val quantity = state.quantityFor(garment.id)
                    val floor = state.returnedFloor(garment)
                    val lockedReason = when {
                        closed -> null // the banner already explains it
                        // Lost garments are archived as "Lost" - that one stays undoable from here.
                        garment.status == ClothingStatus.ARCHIVED &&
                            !(savedRes.lost > 0 && garment.archiveReason == ArchiveReason.LOST) ->
                            "Archived, so this can't be changed here"
                        floor >= quantity ->
                            if (quantity == 1) "Out again on another ticket, so this can't be changed here"
                            else "All of these are out again on another ticket, so they can't be changed here"
                        else -> null
                    }
                    val note = if (lockedReason == null && !closed && floor > 0) {
                        "$floor of these are out again on another ticket, so they stay marked as back"
                    } else null
                    GarmentDecisionCard(
                        garment = garment,
                        quantity = quantity,
                        resolution = state.resolutionFor(garment.id),
                        decision = state.decisionFor(garment.id),
                        edited = garment.id in state.edits,
                        locked = closed || lockedReason != null,
                        lockedReason = lockedReason ?: note,
                        returnedFloor = floor,
                        onDecision = { viewModel.setDecision(garment.id, it) },
                        onReturnedUnits = { viewModel.setReturnedUnits(garment.id, it) },
                        onLostUnits = { viewModel.setLostUnits(garment.id, it) },
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
        LabelPreviewDialog(
            bitmap = bitmap,
            format = state.previewFormat,
            onDismiss = viewModel::dismissPreview,
            onPrint = { viewModel.dismissPreview(); viewModel.printTicket() },
            onExportPng = {
                pendingExportBitmap = bitmap
                exportPngLauncher.launch("meowlaundry-ticket-${ticket?.id}.png")
            },
            onExportPdf = { exportPdfLauncher.launch("meowlaundry-ticket-${ticket?.id}.pdf") },
        )
    }
}

/**
 * Shows the rendered ticket the way it'll actually come out. A receipt strip is
 * shown the way it'll print - a small strip/card on its own white surface with a soft shadow -
 * and exports as a PNG. The [TicketFormat.PAGE] format is treated as what it actually is, a
 * document: a plain white page (page-proportioned, not squeezed to a small strip), described as
 * a page rather than a printer label, and exported as a real PDF rather than a screenshot of one.
 */
@Composable
private fun LabelPreviewDialog(
    bitmap: android.graphics.Bitmap,
    format: TicketFormat,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val isDocument = format == TicketFormat.PAGE
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isDocument) Icons.Default.Description else Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        if (isDocument) "Document preview" else "Label preview",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                    )
                    IconButton(onClick = if (isDocument) onExportPdf else onExportPng) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = if (isDocument) "Save as PDF" else "Save as PNG",
                        )
                    }
                }
                Text(
                    if (isDocument) {
                        "This is the first page of the document. Paper size follows whatever printer or " +
                            "\"Save as PDF\" you pick next."
                    } else {
                        "This is exactly what will come out of the printer."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = Spacing.md),
                )

                // A document gets a full-width page (an A4/Letter proportion, not a narrow strip)
                // with a hairline border, like a sheet of paper sitting on the dialog's tonal
                // background; a receipt/card keeps the narrow printer-strip presentation.
                Surface(
                    shape = if (isDocument) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
                    color = Color.White,
                    shadowElevation = 4.dp,
                    border = if (isDocument) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    modifier = if (isDocument) Modifier.fillMaxWidth() else Modifier.widthIn(max = 260.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = if (isDocument) "Preview of the printed document" else "Rendered ticket label preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        contentScale = ContentScale.FillWidth,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                    FilledTonalButton(onClick = onPrint, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Print", modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        }
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
    quantity: Int,
    resolution: Resolution,
    decision: ItemDecision,
    edited: Boolean,
    locked: Boolean,
    lockedReason: String?,
    returnedFloor: Int,
    onDecision: (ItemDecision) -> Unit,
    onReturnedUnits: (Int) -> Unit,
    onLostUnits: (Int) -> Unit,
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
                            if (quantity > 1) "\u00d7$quantity" else null,
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

            if (quantity <= 1) {
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
            } else {
                // Several identical units: count how many came back and how many were lost.
                val stillOut = (quantity - resolution.returned - resolution.lost).coerceAtLeast(0)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(decisionIcon(ItemDecision.RETURNED), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Back", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.xs).weight(1f))
                        QuantityStepper(
                            value = resolution.returned,
                            onChange = onReturnedUnits,
                            min = returnedFloor,
                            max = quantity - resolution.lost,
                            enabled = !locked,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(decisionIcon(ItemDecision.LOST), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Lost", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.xs).weight(1f))
                        QuantityStepper(
                            value = resolution.lost,
                            onChange = onLostUnits,
                            min = 0,
                            max = quantity - resolution.returned,
                            enabled = !locked,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(decisionIcon(ItemDecision.PENDING), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (stillOut == 0) "Nothing left out" else "$stillOut still out",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs).weight(1f),
                        )
                        if (!locked && stillOut > 0) {
                            TextButton(onClick = { onDecision(ItemDecision.RETURNED) }) { Text("All back") }
                        }
                    }
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
