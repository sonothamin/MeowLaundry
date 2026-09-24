package com.sonothamin.meowlaundry.ui.articleview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.ui.components.archiveReasonLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Garment detail screen, reached by tapping it in the closet. Laid out like a photo app's
 * details view (full-bleed photo up top with overlaid back/action buttons, a date/time
 * header, a caption-style notes row, then an expandable "Details" card) rather than a form,
 * since editing and deleting both happen elsewhere - the pencil action is the deliberate,
 * single way in to Edit Article.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleViewScreen(
    viewModel: ArticleViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val item = state.item

    Scaffold { padding ->
        if (item == null) return@Scaffold

        val heroPath = selectedPhotoPath ?: item.imagePath
        val dateFormat = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Full-bleed photo header, with back/action buttons floating on top of it ---
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (heroPath != null) {
                            AsyncImage(
                                model = heroPath,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = null,
                                modifier = Modifier.padding(Spacing.xxl),
                                tint = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        HeroIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (item.status == ClothingStatus.ARCHIVED) {
                                HeroIconButton(Icons.Default.Restore, "Restore to closet", viewModel::unarchive)
                            } else {
                                HeroIconButton(Icons.Default.Inventory2, "Archive article") { showArchiveDialog = true }
                            }
                            HeroIconButton(Icons.Default.Edit, "Edit article") { onEdit(item.id) }
                            HeroIconButton(Icons.Default.Delete, "Delete article") { showDeleteConfirm = true }
                        }
                    }
                }
            }

            // A thumbnail strip under the header lets you flip through the rest of the gallery,
            // the way a photo app lets you swipe between shots of the same subject.
            if (state.photos.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(state.photos, key = { it.id }) { photo ->
                            val isSelected = (selectedPhotoPath ?: item.imagePath) == photo.path
                            AsyncImage(
                                model = photo.path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(if (isSelected) 2.dp else 0.dp)
                                    .clickable { selectedPhotoPath = photo.path },
                            )
                        }
                    }
                }
            }

            // --- The "sheet" below the photo: date/time, caption, then an expandable Details card ---
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${dateFormat.format(Date(item.createdAt))} · ${timeFormat.format(Date(item.createdAt)).lowercase(Locale.getDefault())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .clickable { onEdit(item.id) }
                        .padding(vertical = Spacing.sm),
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

            item {
                Text(
                    "Details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }

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
                            Icon(Icons.Default.Checkroom, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                                Text(
                                    item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    statusLabel(item.status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor(item.status),
                                )
                            }
                            Icon(
                                if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (detailsExpanded) "Collapse" else "Expand",
                            )
                        }

                        if (detailsExpanded) {
                            HorizontalDetailDivider()
                            item.price?.let {
                                DetailRow("Replacement price", "$${"%.0f".format(it)}")
                                HorizontalDetailDivider()
                            }
                            DetailRow("Photos", if (state.photos.isEmpty()) "None" else "${state.photos.size}")
                            HorizontalDetailDivider()
                            DetailRow("Added", "${dateFormat.format(Date(item.createdAt))}, ${timeFormat.format(Date(item.createdAt)).lowercase(Locale.getDefault())}")
                            if (item.updatedAt != item.createdAt) {
                                HorizontalDetailDivider()
                                DetailRow("Last updated", "${dateFormat.format(Date(item.updatedAt))}, ${timeFormat.format(Date(item.updatedAt)).lowercase(Locale.getDefault())}")
                            }
                            if (item.status == ClothingStatus.ARCHIVED) {
                                HorizontalDetailDivider()
                                DetailRow("Archived as", item.archiveReason?.let { archiveReasonLabel(it) } ?: "—")
                                item.archivedAt?.let {
                                    HorizontalDetailDivider()
                                    DetailRow("Archived on", dateFormat.format(Date(it)))
                                }
                                item.archiveNotes?.takeIf { it.isNotBlank() }?.let {
                                    HorizontalDetailDivider()
                                    DetailRow("Archive notes", it)
                                }
                            }
                        }
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).padding(top = Spacing.sm),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Laundry history", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(state.history, key = { it.id }) { ticketItem ->
                    Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                        HistoryRow(ticketItem)
                    }
                }
                item { Spacer(modifier = Modifier.height(Spacing.md)) }
            } else {
                item { Spacer(modifier = Modifier.height(Spacing.md)) }
            }
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

/** A circular, semi-transparent icon button meant to float directly on top of a photo. */
@Composable
private fun HeroIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
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

@Composable
private fun HorizontalDetailDivider() {
    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
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

@Composable
private fun HistoryRow(ticketItem: LaundryTicketItem) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Default.LocalLaundryService, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                when {
                    ticketItem.lost -> "Lost at the laundry"
                    ticketItem.returned -> "Returned"
                    else -> "Currently at the laundry"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
