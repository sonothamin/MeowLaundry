package com.sonothamin.meowlaundry.ui.closet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Woman
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClosetViewMode
import com.sonothamin.meowlaundry.data.ClosetSortOption
import com.sonothamin.meowlaundry.data.ClothingStatus
import androidx.compose.material.icons.filled.Hotel
import com.sonothamin.meowlaundry.data.Suggestions
import com.sonothamin.meowlaundry.data.export.ExportUtils
import com.sonothamin.meowlaundry.data.CurrencyAmount
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.ui.components.ClothingCard
import com.sonothamin.meowlaundry.ui.components.ClothingGridSkeleton
import com.sonothamin.meowlaundry.ui.components.ClothingListSkeleton
import com.sonothamin.meowlaundry.ui.components.EndOfList
import com.sonothamin.meowlaundry.ui.components.LoadMoreFooter
import com.sonothamin.meowlaundry.ui.components.LoadMoreOnNearEnd
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
    onOpenAtLaundry: () -> Unit,
    onOpenLostArchive: () -> Unit,
    /** Opens the nav drawer. Null hides the hamburger icon (e.g. when embedded without a drawer). */
    onMenuClick: (() -> Unit)? = null,
) {
    val paged by viewModel.paged.collectAsStateWithLifecycle()
    val items = paged.items
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val typeCounts by viewModel.typeCounts.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val showWinterWear by viewModel.showWinterWear.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    // Whichever of the two is on screen reports when the user nears its end; the other has no
    // visible items and stays quiet.
    gridState.LoadMoreOnNearEnd(enabled = paged.hasMore, onLoadMore = viewModel::loadMore)
    listState.LoadMoreOnNearEnd(enabled = paged.hasMore, onLoadMore = viewModel::loadMore)

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
                            // Winter wear visibility lives here (was buried in Settings): highlighted
                            // while snowflake garments are shown, plain when they're hidden.
                            IconToggleButton(
                                checked = showWinterWear,
                                onCheckedChange = viewModel::setShowWinterWear,
                            ) {
                                Icon(
                                    Icons.Default.AcUnit,
                                    contentDescription = if (showWinterWear) "Hide winter wear" else "Show winter wear",
                                )
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
                // The three stat tiles (in closet/at laundry/lost) are shortcuts to jump into a
                // filtered view - redundant screen real estate once the person is already
                // narrowing things down by search. Swap them for filter + sort shortcuts instead.
                if (!searchActive) {
                    ClosetSummaryBlock(
                        inCloset = summary.inClosetCount,
                        atLaundry = summary.atLaundryCount,
                        lost = summary.lostCount,
                        lostValues = summary.lostValues,
                        inClosetSelected = filter == ClothingStatus.IN_CLOSET,
                        // Tapping the tile again clears it - the old "All" status chip is gone.
                        onInClosetClick = {
                            viewModel.setFilter(if (filter == ClothingStatus.IN_CLOSET) null else ClothingStatus.IN_CLOSET)
                        },
                        onAtLaundryClick = onOpenAtLaundry,
                        onLostClick = onOpenLostArchive,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
                TypeFilterRow(
                    counts = typeCounts,
                    selected = typeFilter,
                    onToggle = viewModel::toggleType,
                    onClear = viewModel::clearTypes,
                )
                if (searchActive) {
                    SortRow(selected = sortOption, onSelect = viewModel::setSortOption)
                }
            }

            // Extra bottom room so the FAB never covers the end-of-list marker.
            val listPadding = PaddingValues(
                start = Spacing.md, top = Spacing.md, end = Spacing.md, bottom = 96.dp,
            )
            when {
                paged.isLoading -> {
                    if (viewMode == ClosetViewMode.GRID) ClothingGridSkeleton() else ClothingListSkeleton()
                }
                paged.total == 0 -> EmptyState(
                    icon = Icons.Default.Checkroom,
                    title = if (searchQuery.isNotBlank()) "No matches" else "Nothing here yet",
                    subtitle = if (searchQuery.isNotBlank()) "Try a different search term."
                    else "Tap + to add the first garment to your closet.",
                )
                viewMode == ClosetViewMode.GRID -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = listPadding,
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
                    item(key = "list-footer", span = { GridItemSpan(maxLineSpan) }) {
                        if (paged.hasMore) LoadMoreFooter() else EndOfList()
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = listPadding,
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
                    item(key = "list-footer") {
                        if (paged.hasMore) LoadMoreFooter() else EndOfList()
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
    inClosetSelected: Boolean,
    onInClosetClick: () -> Unit,
    onAtLaundryClick: () -> Unit,
    onLostClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lostTotal = lostValues.filter { it.total > 0 }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatTile(
            icon = Icons.Default.Checkroom,
            value = inCloset.toString(),
            label = "In closet",
            active = inCloset > 0,
            selected = inClosetSelected,
            onClick = onInClosetClick,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Default.LocalLaundryService,
            value = atLaundry.toString(),
            label = "At laundry",
            active = atLaundry > 0,
            onClick = onAtLaundryClick,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = Icons.Default.ReportProblem,
            value = lost.toString(),
            label = if (lostTotal.isEmpty()) "Lost" else lostTotal.joinToString(" + ") { Currencies.format(it.total, it.currency) },
            active = lost > 0,
            onClick = onLostClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    /** All three tiles share one look; the only thing that changes is whether there's anything to show. */
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The tile's own status filter is applied: shown in the stronger primary role. */
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        selected -> scheme.primary
        active -> scheme.primaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        selected -> scheme.onPrimary
        active -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
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
private fun SortRow(selected: ClosetSortOption, onSelect: (ClosetSortOption) -> Unit) {
    val options = ClosetSortOption.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(sortLabel(option)) },
                leadingIcon = if (selected == option) {
                    { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

private fun sortLabel(option: ClosetSortOption): String = when (option) {
    ClosetSortOption.NEWEST -> "Newest"
    ClosetSortOption.OLDEST -> "Oldest"
    ClosetSortOption.NAME_ASC -> "Name A\u2013Z"
    ClosetSortOption.NAME_DESC -> "Name Z\u2013A"
    ClosetSortOption.PRICE_HIGH -> "Price: high\u2013low"
    ClosetSortOption.PRICE_LOW -> "Price: low\u2013high"
}

/**
 * Category filter. Collapsed it is one scrolling row of chips with a trailing expand button;
 * expanded the same chips wrap into a grid so every category is visible at once. Multi-select:
 * nothing selected means "All". Size changes and the chevron use spring motion (Expressive).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterRow(
    counts: Map<String, Int>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    // Only categories that actually hold garments (plus any still selected) earn a chip. Built-in
    // categories keep their usual order; anything the person typed themselves is appended, sorted.
    val present = counts.keys + selected
    val types = Suggestions.builtInCategories.filter { it in present } +
        present.filterNot { it in Suggestions.builtInCategories }.sorted()
    if (types.size < 2 && selected.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "type-filter-chevron",
    )

    @Composable
    fun Chips() {
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClear,
            label = { Text("All") },
            leadingIcon = if (selected.isEmpty()) {
                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
        )
        types.forEach { type ->
            val isSelected = type in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(type) },
                label = { Text("$type \u00B7 ${counts[type] ?: 0}") },
                leadingIcon = {
                    Icon(
                        if (isSelected) Icons.Default.Done else typeIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        if (expanded) {
            FlowRow(
                modifier = Modifier.weight(1f).padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) { Chips() }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) { Chips() }
        }
        FilledTonalIconToggleButton(
            checked = expanded,
            onCheckedChange = { expanded = it },
        ) {
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Show fewer categories" else "Show all categories",
                modifier = Modifier.rotate(chevron),
            )
        }
    }
}

/** Icon for a category chip. Built-in categories get a specific icon; anything typed by hand
 * (matched by exact display name, e.g. "Kitchen linen") falls back to a generic one. */
private fun typeIcon(category: String) = when (category) {
    "Top" -> Icons.Default.Checkroom
    "Bottom" -> Icons.Default.Straighten
    "Dress" -> Icons.Default.Woman
    "Outerwear" -> Icons.Default.Layers
    "Underwear" -> Icons.Default.DryCleaning
    "Sleepwear" -> Icons.Default.Bedtime
    "Accessory" -> Icons.Default.Watch
    "Footwear" -> Icons.Default.Hiking
    "Bedding" -> Icons.Default.Hotel
    else -> Icons.Default.Category
}
