package com.sonothamin.meowlaundry.ui.articleview

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.ui.components.InfoBlock
import com.sonothamin.meowlaundry.ui.components.InfoCell
import com.sonothamin.meowlaundry.ui.components.archiveReasonLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Garment detail screen. Top to bottom: app bar (back, quick laundry action, edit, overflow),
 * a square, Material rounded-corner hero photo, the title, an at-a-glance info block (status, last washed,
 * last pressed, added, trips, value), a quick laundry button, notes, expandable details, and an
 * Activity feed with the garment's care history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleViewScreen(
    viewModel: ArticleViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSendToLaundry: (Long) -> Unit,
    onOpenTicket: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val item = state.item
    val care = remember(state.events) { summarizeCare(state.events) }
    val activity = remember(item, state.events) { item?.let { buildActivity(it, state.events) }.orEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (item != null) {
                        // Quick laundry action: send it off, or jump to the ticket it's already out on.
                        when {
                            item.status == ClothingStatus.IN_CLOSET ->
                                IconButton(onClick = { onSendToLaundry(item.id) }) {
                                    Icon(Icons.Default.LocalLaundryService, contentDescription = "Send to laundry")
                                }
                            item.status == ClothingStatus.AT_LAUNDRY && care.activeTicketId != null ->
                                IconButton(onClick = { onOpenTicket(care.activeTicketId) }) {
                                    Icon(Icons.Default.ConfirmationNumber, contentDescription = "View laundry ticket")
                                }
                        }
                        IconButton(onClick = { onEdit(item.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit article")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (item.status == ClothingStatus.ARCHIVED) {
                                    DropdownMenuItem(
                                        text = { Text("Restore to closet") },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                        onClick = { menuOpen = false; viewModel.unarchive() },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Archive") },
                                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                                        onClick = { menuOpen = false; showArchiveDialog = true },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; showDeleteConfirm = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) return@Scaffold

        val heroPath = selectedPhotoPath ?: item.imagePath
        val dateFormat = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Hero: square crop (no letterboxing) with the Material extra-large rounded corners ---
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (heroPath != null) {
                            AsyncImage(
                                model = heroPath,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            // Thumbnail strip to flip through the rest of the gallery.
            if (state.photos.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(state.photos, key = { it.id }) { photo ->
                            val isSelected = heroPath == photo.path
                            AsyncImage(
                                model = photo.path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .clickable { selectedPhotoPath = photo.path },
                            )
                        }
                    }
                }
            }

            // --- Title ---
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            item.brand,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Info block, directly under the title ---
            item {
                Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                    InfoBlock(
                        rows = listOf(
                            listOf(
                                InfoCell("Status", statusLabel(item.status), statusIcon(item.status), valueColor = statusColor(item.status)),
                                dateCell("Last washed", Icons.Default.LocalLaundryService, care.lastWashedAt),
                                dateCell("Last pressed", Icons.Default.AutoAwesome, care.lastPressedAt),
                            ),
                            listOf(
                                dateCell("Added", Icons.Default.CalendarToday, item.createdAt, neverLabel = "—"),
                                InfoCell(
                                    "Laundry trips",
                                    care.timesSent.toString(),
                                    Icons.Default.Repeat,
                                    sub = if (care.timesSent == 1) "time" else "times",
                                ),
                                InfoCell(
                                    "Value",
                                    item.price?.let { Currencies.format(it, item.currency) } ?: "—",
                                    Icons.Default.Sell,
                                    sub = if (item.price != null) "to replace" else null,
                                ),
                            ),
                        ),
                    )
                }
            }

            // --- Quick laundry action (mirrors the app bar) ---
            if (item.status == ClothingStatus.IN_CLOSET || (item.status == ClothingStatus.AT_LAUNDRY && care.activeTicketId != null)) {
                item {
                    val atLaundry = item.status == ClothingStatus.AT_LAUNDRY
                    FilledTonalButton(
                        onClick = {
                            if (atLaundry) onOpenTicket(care.activeTicketId!!) else onSendToLaundry(item.id)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    ) {
                        Icon(
                            if (atLaundry) Icons.Default.ConfirmationNumber else Icons.Default.LocalLaundryService,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (atLaundry) "View laundry ticket" else "Send to laundry",
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }

            // --- Notes ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .clickable { onEdit(item.id) }
                        .padding(vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        item.notes?.takeIf { it.isNotBlank() } ?: "Add a note…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.notes.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }

            // --- Expandable details ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailsExpanded = !detailsExpanded }
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Icon(
                                if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (detailsExpanded) "Collapse" else "Expand",
                            )
                        }
                        if (detailsExpanded) {
                            listOf(
                                "Category" to item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                "Garment type" to item.garmentType,
                                "Brand" to item.brand,
                                "Color" to item.color,
                                "Photos" to if (state.photos.isEmpty()) "None" else "${state.photos.size}",
                                "Added" to "${dateFormat.format(Date(item.createdAt))}, ${timeFormat.format(Date(item.createdAt)).lowercase(Locale.getDefault())}",
                                "Last updated" to item.updatedAt.takeIf { it != item.createdAt }?.let {
                                    "${dateFormat.format(Date(it))}, ${timeFormat.format(Date(it)).lowercase(Locale.getDefault())}"
                                },
                            ).filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
                                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                                DetailRow(label, value!!)
                            }
                            if (item.status == ClothingStatus.ARCHIVED) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                                DetailRow("Archived as", item.archiveReason?.let { archiveReasonLabel(it) } ?: "—")
                            }
                        }
                    }
                }
            }

            // --- Activity: care history ---
            item {
                Text(
                    "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.lg, bottom = Spacing.sm),
                )
            }
            items(activity, key = { "${it.kind}-${it.ticketId}-${it.at}" }) { entry ->
                ActivityRow(entry = entry, onOpenTicket = onOpenTicket)
            }
            item { Spacer(modifier = Modifier.height(Spacing.lg)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this article?") },
            text = { Text("This removes it and its photos permanently. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showArchiveDialog && item != null) {
        ArchiveDialog(
            onDismiss = { showArchiveDialog = false },
            onConfirm = { reason, notes ->
                showArchiveDialog = false
                viewModel.archive(reason, notes)
            },
        )
    }
}

/** "3 days ago" under the date, or [neverLabel] when there is no date yet. */
private fun dateCell(label: String, icon: ImageVector, at: Long?, neverLabel: String = "Never"): InfoCell {
    if (at == null) return InfoCell(label, neverLabel, icon)
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
    val relative = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    return InfoCell(label, date, icon, sub = relative)
}

@Composable
private fun ActivityRow(entry: ActivityEntry, onOpenTicket: (Long) -> Unit) {
    val ticketId = entry.ticketId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ticketId != null) Modifier.clickable { onOpenTicket(ticketId) } else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val isProblem = entry.kind == ActivityKind.LOST
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isProblem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (entry.kind) {
                    ActivityKind.ADDED -> Icons.Default.Checkroom
                    ActivityKind.WASH, ActivityKind.WASH_AND_PRESS -> Icons.Default.LocalLaundryService
                    ActivityKind.PRESS -> Icons.Default.AutoAwesome
                    ActivityKind.DRY_CLEAN -> Icons.Default.DryCleaning
                    ActivityKind.OUT_FOR_CARE -> Icons.Default.Schedule
                    ActivityKind.LOST -> Icons.Default.ReportProblem
                    ActivityKind.ARCHIVED -> Icons.Default.Inventory2
                },
                contentDescription = null,
                tint = if (isProblem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            entry.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDialog(
    onDismiss: () -> Unit,
    onConfirm: (ArchiveReason, String?) -> Unit,
) {
    var reason by remember { mutableStateOf(ArchiveReason.DONATED) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive this article?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "It leaves your active closet and moves to the Archive tab. You can restore it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = archiveReasonLabel(reason),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reason") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ArchiveReason.values().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(archiveReasonLabel(r)) },
                                onClick = { reason = r; expanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason, notes.trim().ifBlank { null }) }) { Text("Archive") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun statusLabel(status: ClothingStatus): String = when (status) {
    ClothingStatus.IN_CLOSET -> "In closet"
    ClothingStatus.AT_LAUNDRY -> "At laundry"
    ClothingStatus.LOST -> "Lost"
    ClothingStatus.ARCHIVED -> "Archived"
}

@Composable
private fun statusColor(status: ClothingStatus) = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.primary
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.tertiary
    ClothingStatus.LOST -> MaterialTheme.colorScheme.error
    ClothingStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Icon that mirrors the status shown in the info grid. */
private fun statusIcon(status: ClothingStatus): ImageVector = when (status) {
    ClothingStatus.IN_CLOSET -> Icons.Default.Checkroom
    ClothingStatus.AT_LAUNDRY -> Icons.Default.LocalLaundryService
    ClothingStatus.LOST -> Icons.Default.ReportProblem
    ClothingStatus.ARCHIVED -> Icons.Default.Inventory2
}
