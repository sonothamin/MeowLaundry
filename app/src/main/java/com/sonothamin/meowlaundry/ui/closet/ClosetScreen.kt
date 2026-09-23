package com.sonothamin.meowlaundry.ui.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.ui.components.ClothingCard
import com.sonothamin.meowlaundry.ui.components.EmptyState
import com.sonothamin.meowlaundry.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosetScreen(
    viewModel: ClosetViewModel,
    onAddItem: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("My closet") },
                colors = TopAppBarDefaults.largeTopAppBarColors(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = "Add garment")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SummaryRow(
                inCloset = summary.inClosetCount,
                atLaundry = summary.atLaundryCount,
                lost = summary.lostCount,
                lostValue = summary.lostValue,
            )

            FilterRow(selected = filter, onSelect = viewModel::setFilter)

            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Checkroom,
                    title = "Nothing here yet",
                    subtitle = "Tap + to add the first garment to your closet.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        ClothingCard(item = item, onClick = { onOpenItem(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(inCloset: Int, atLaundry: Int, lost: Int, lostValue: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SummaryCard(label = "In closet", value = inCloset.toString(), modifier = Modifier.weight(1f))
        SummaryCard(label = "At laundry", value = atLaundry.toString(), modifier = Modifier.weight(1f))
        SummaryCard(
            label = "Lost",
            value = if (lostValue > 0) "$lost · \$${"%.0f".format(lostValue)}" else lost.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterRow(selected: ClothingStatus?, onSelect: (ClothingStatus?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
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
    }
}
