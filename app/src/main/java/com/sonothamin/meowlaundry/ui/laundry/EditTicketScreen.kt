package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.ui.components.DueDateChips
import com.sonothamin.meowlaundry.ui.components.SuggestionTextField
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
    val providerSuggestions by viewModel.providerSuggestions.collectAsStateWithLifecycle()

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

                    SuggestionTextField(
                        value = state.providerName,
                        onValueChange = viewModel::onProviderNameChange,
                        label = "Laundry / provider name",
                        placeholder = "Optional",
                        suggestions = providerSuggestions,
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            DueDateChips(dueAt = state.dueAt, onChange = viewModel::onDueAtChange)

            ItemsSection(
                added = state.garments,
                availableToAdd = state.availableToAdd,
                onAdd = viewModel::addGarment,
                onRemove = viewModel::removeGarment,
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
    }
}

/**
 * Which garments are on this ticket, collapsed by default so it doesn't dominate the screen.
 * Two subsections: "Added" (on the ticket - removable unless already returned/lost) and
 * "Can be added" (everything else currently in the closet - one tap adds it, no separate dialog).
 */
@Composable
private fun ItemsSection(
    added: List<TicketGarment>,
    availableToAdd: List<ClothingItem>,
    onAdd: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Checkroom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Items",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                )
                Text(
                    "${added.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs).size(20.dp),
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    GarmentSubsection(title = "Added (${added.size})") {
                        if (added.isEmpty()) {
                            EmptyRow("No garments on this ticket yet.")
                        } else {
                            added.forEach { garment ->
                                AddedGarmentRow(garment = garment, onRemove = { onRemove(garment.item.id) })
                            }
                        }
                    }

                    GarmentSubsection(title = "Can be added (${availableToAdd.size})", topSpacing = Spacing.md) {
                        if (availableToAdd.isEmpty()) {
                            EmptyRow("Nothing else in your closet right now.")
                        } else {
                            availableToAdd.forEach { item ->
                                AvailableGarmentRow(item = item, onAdd = { onAdd(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarmentSubsection(
    title: String,
    topSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = topSpacing)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        content()
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.xs),
    )
}

@Composable
private fun AddedGarmentRow(garment: TicketGarment, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                garment.item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (garment.removable) garmentTypeLabel(garment.item) else if (garment.lost) "Lost" else "Returned",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (garment.removable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${garment.item.title}")
            }
        }
    }
}

@Composable
private fun AvailableGarmentRow(item: ClothingItem, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(garmentTypeLabel(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "Add ${item.title}")
        }
    }
}

private fun garmentTypeLabel(item: ClothingItem): String =
    item.type.name.lowercase().replaceFirstChar { it.uppercase() }
