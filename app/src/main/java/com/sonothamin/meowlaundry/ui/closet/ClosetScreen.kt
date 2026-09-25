package com.sonothamin.meowlaundry.ui.closet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClosetViewMode
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.export.ExportUtils
import com.sonothamin.meowlaundry.data.CurrencyAmount
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.ui.components.ClothingCard
import com.sonothamin.meowlaundry.ui.components.ClothingListRow
import com.sonothamin.meowlaundry.ui.components.EmptyState
import com.sonothamin.meowlaundry.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosetScreen(
    viewModel: ClosetViewModel,
    onAddItem: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onSendSelectedToLaundry: (List<Long>) -> Unit,
    /** Opens the nav drawer. Null hides the hamburger icon (e.g. when embedded without a drawer). */
    onMenuClick: (() -> Unit)? = null,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedContent(targetState = selectionMode, label = "closet-topbar") { inSelection ->
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
                            IconButton(onClick = { onSendSelectedToLaundry(selectedIds.toList()) }) {
                                Icon(Icons.Default.LocalLaundryService, contentDescription = "Send to laundry")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val selected = viewModel.getSelectedItems()
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            ExportUtils.shareTextIntent(ExportUtils.clothingSummaryText(selected)),
                                            "Share garments",
                                        )
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val selected = viewModel.getSelectedItems()
                                    val intent = ExportUtils.shareFileIntent(
                                        context,
                                        "closet-export.csv",
                                        ExportUtils.clothingCsv(selected),
                                        "text/csv",
                                    )
                                    context.startActivity(android.content.Intent.createChooser(intent, "Export garments"))
                                }
                            }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                } else if (searchActive) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                placeholder = { Text("Search garments") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.setSearchActive(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                    )
                } else {
                    LargeTopAppBar(
                        title = { Text("My closet") },
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            onMenuClick?.let {
                                IconButton(onClick = it) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.setSearchActive(true) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = viewModel::toggleViewMode) {
                                Icon(
                                    if (viewMode == ClosetViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle grid/list view",
                                )
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add garment") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!selectionMode) {
                ClosetSummaryBlock(
                    inCloset = summary.inClosetCount,
                    atLaundry = summary.atLaundryCount,
                    lost = summary.lostCount,
                    lostValues = summary.lostValues,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
                FilterRow(selected = filter, onSelect = viewModel::setFilter)
            }

            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Checkroom,
                    title = if (searchQuery.isNotBlank()) "No matches" else "Nothing here yet",
                    subtitle = if (searchQuery.isNotBlank()) "Try a different search term."
                    else "Tap + to add the first garment to your closet.",
                )
            } else if (viewMode == ClosetViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        ClothingCard(
                            item = item,
                            onClick = { if (selectionMode) viewModel.toggleSelection(item.id) else onOpenItem(item.id) },
                            selected = item.id in selectedIds,
                            onSelectToggle = if (selectionMode) ({ viewModel.toggleSelection(item.id) }) else null,
                            onLongClick = { viewModel.startSelection(item.id) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        ClothingListRow(
                            item = item,
                            onClick = { if (selectionMode) viewModel.toggleSelection(item.id) else onOpenItem(item.id) },
                            selected = item.id in selectedIds,
                            onSelectToggle = if (selectionMode) ({ viewModel.toggleSelection(item.id) }) else null,
                            onLongClick = { viewModel.startSelection(item.id) },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} garment(s)?") },
            text = { Text("This removes them and their photos permanently. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ClosetSummaryBlock(
    inCloset: Int,
    atLaundry: Int,
    lost: Int,
    lostValues: List<CurrencyAmount>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val lostTotal = lostValues.filter { it.total > 0 }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatTile(
            icon = Icons.Default.Checkroom,
            value = inCloset.toString(),
            label = "In closet",
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Default.LocalLaundryService,
            value = atLaundry.toString(),
            label = "At laundry",
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Default.ReportProblem,
            value = lost.toString(),
            label = if (lostTotal.isEmpty()) "Lost" else lostTotal.joinToString(" + ") { Currencies.format(it.total, it.currency) },
            container = if (lost > 0) scheme.errorContainer else scheme.surfaceContainerHigh,
            content = if (lost > 0) scheme.onErrorContainer else scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterRow(selected: ClothingStatus?, onSelect: (ClothingStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        FilterChip(
            selected = selected == ClothingStatus.IN_CLOSET,
            onClick = { onSelect(ClothingStatus.IN_CLOSET) },
            label = { Text("In closet") },
        )
        FilterChip(
            selected = selected == ClothingStatus.AT_LAUNDRY,
            onClick = { onSelect(ClothingStatus.AT_LAUNDRY) },
            label = { Text("At laundry") },
        )
        FilterChip(
            selected = selected == ClothingStatus.LOST,
            onClick = { onSelect(ClothingStatus.LOST) },
            label = { Text("Lost") },
        )
        FilterChip(
            selected = selected == ClothingStatus.ARCHIVED,
            onClick = { onSelect(ClothingStatus.ARCHIVED) },
            label = { Text("Archived") },
        )
    }
}
