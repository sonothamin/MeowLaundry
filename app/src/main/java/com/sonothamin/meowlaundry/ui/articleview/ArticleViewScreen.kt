package com.sonothamin.meowlaundry.ui.articleview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.ui.theme.Spacing

/**
 * Read-only landing screen for a garment, reached by tapping it in the closet. Editing and
 * deleting both happen from here so a stray tap on a card never drops the person straight
 * into an editable form (that's what caused the "everything is instantly editable" feel
 * before) - the pencil action is the deliberate, single way in to Edit Article.
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

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val item = state.item

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (item != null) {
                        FilledTonalIconButton(onClick = { onEdit(item.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit article")
                        }
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        FilledIconButton(
                            onClick = { showDeleteConfirm = true },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete article")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(0.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.imagePath != null) {
                            AsyncImage(
                                model = item.imagePath,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = null,
                                modifier = Modifier.padding(Spacing.xxl),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(item.type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(statusLabel(item.status)) },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledLabelColor = statusColor(item.status),
                            ),
                        )
                        item.price?.let { price ->
                            AssistChip(onClick = {}, enabled = false, label = { Text("$${"%.0f".format(price)}") })
                        }
                    }
                }
            }

            if (!item.notes.isNullOrBlank()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            item.notes,
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Laundry history", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(state.history, key = { it.id }) { ticketItem -> HistoryRow(ticketItem) }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this article?") },
            text = { Text("This removes it and its photo permanently. This can't be undone.") },
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
}

@Composable
private fun statusColor(status: ClothingStatus) = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.primary
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.tertiary
    ClothingStatus.LOST -> MaterialTheme.colorScheme.error
}
