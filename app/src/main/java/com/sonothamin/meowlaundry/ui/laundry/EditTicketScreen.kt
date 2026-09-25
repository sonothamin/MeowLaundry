package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.ui.components.DueDateChips
import com.sonothamin.meowlaundry.ui.components.serviceIcon
import com.sonothamin.meowlaundry.ui.components.serviceLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing

/**
 * Edit an existing ticket's service, provider, due date, notes, and which garments are on it -
 * everything chosen once at "send to laundry" time. Adding/removing a garment here mirrors the
 * status change that "send to laundry" and "resolve ticket" make elsewhere: added garments become
 * AT_LAUNDRY, and removing a still-pending garment puts it back IN_CLOSET.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTicketScreen(
    viewModel: EditTicketViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit ticket") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledIconButton(onClick = viewModel::save, enabled = state.isValid) {
                        Icon(Icons.Default.Check, contentDescription = "Save changes")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Service + provider grouped in one card, same language as the info block on
            // Ticket Detail - they're read together there, so they're edited together here.
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Service", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ServiceType.values().forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = state.serviceType == type,
                                onClick = { viewModel.onServiceTypeChange(type) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ServiceType.values().size),
                                icon = {
                                    Icon(
                                        serviceIcon(type),
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                },
                                label = { Text(serviceLabel(type), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.providerName,
                        onValueChange = viewModel::onProviderNameChange,
                        label = { Text("Laundry / provider name") },
                        placeholder = { Text("Optional") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            DueDateChips(dueAt = state.dueAt, onChange = viewModel::onDueAtChange)

            GarmentsSection(
                garments = state.garments,
                onRemove = viewModel::removeGarment,
                onAddClick = viewModel::openAddPicker,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                placeholder = { Text("Anything worth remembering about this run") },
                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.showAddPicker) {
            AddGarmentDialog(
                available = state.availableToAdd,
                onDismiss = viewModel::dismissAddPicker,
                onConfirm = viewModel::addGarments,
            )
        }
    }
}

@Composable
private fun GarmentsSection(
    garments: List<TicketGarment>,
    onRemove: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Garments", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "${garments.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (garments.isEmpty()) {
                Text(
                    "No garments on this ticket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                garments.forEach { garment ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Checkroom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                            Text(
                                garment.item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!garment.removable) {
                                Text(
                                    if (garment.lost) "Lost" else "Returned",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (garment.removable) {
                            IconButton(onClick = { onRemove(garment.item.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove ${garment.item.title}")
                            }
                        }
                    }
                }
            }

            TextButton(onClick = onAddClick, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Add garment")
            }
        }
    }
}

@Composable
private fun AddGarmentDialog(
    available: List<ClothingItem>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("Add garments", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Only garments currently in your closet can be added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = Spacing.md),
                )

                if (available.isEmpty()) {
                    Text(
                        "Everything in your closet is already out at the laundry.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(available, key = { it.id }) { item ->
                            val isSelected = item.id in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (isSelected) selected - item.id else selected + item.id
                                    }
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + item.id else selected - item.id
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    FilledTonalButton(
                        onClick = { onConfirm(selected) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}") }
                }
            }
        }
    }
}
