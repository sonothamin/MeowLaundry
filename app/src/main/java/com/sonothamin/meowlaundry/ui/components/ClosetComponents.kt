package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.ui.theme.Spacing

@Composable
fun statusColor(status: ClothingStatus): Color = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.tertiaryContainer
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.primaryContainer
    ClothingStatus.LOST -> MaterialTheme.colorScheme.errorContainer
    ClothingStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant
}

// Paired "on" color for each status container above, so the icon always meets
// contrast guidelines against its badge background regardless of theme.
@Composable
fun statusContentColor(status: ClothingStatus): Color = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.onTertiaryContainer
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.onPrimaryContainer
    ClothingStatus.LOST -> MaterialTheme.colorScheme.onErrorContainer
    ClothingStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun statusLabel(status: ClothingStatus): String = when (status) {
    ClothingStatus.IN_CLOSET -> "In closet"
    ClothingStatus.AT_LAUNDRY -> "At laundry"
    ClothingStatus.LOST -> "Lost"
    ClothingStatus.ARCHIVED -> "Archived"
}

fun statusIcon(status: ClothingStatus): ImageVector = when (status) {
    ClothingStatus.IN_CLOSET -> Icons.Filled.Checkroom
    ClothingStatus.AT_LAUNDRY -> Icons.Filled.LocalLaundryService
    ClothingStatus.LOST -> Icons.Filled.ReportProblem
    ClothingStatus.ARCHIVED -> Icons.Filled.Inventory2
}

fun archiveReasonLabel(reason: ArchiveReason): String = when (reason) {
    ArchiveReason.DONATED -> "Donated"
    ArchiveReason.SOLD -> "Sold"
    ArchiveReason.DISCARDED -> "Discarded"
    ArchiveReason.GIVEN_AWAY -> "Given away"
    ArchiveReason.LOST -> "Lost"
    ArchiveReason.OTHER -> "Other"
}

@Composable
fun StatusChip(status: ClothingStatus, modifier: Modifier = Modifier) {
    // An icon-only badge instead of a text chip: it reads at a glance and,
    // by using the theme's onXContainer color, always has enough contrast
    // against its container color (a Material3 disabled chip's dimmed text
    // did not). contentDescription keeps the status readable for screen readers.
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(statusColor(status)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = statusIcon(status),
            contentDescription = statusLabel(status),
            tint = statusContentColor(status),
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClothingCard(
    item: ClothingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = { onSelectToggle?.invoke() ?: onClick() },
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(0.dp)),
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
                        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.xs)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact row layout for the closet's list view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClothingListRow(
    item: ClothingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = { onSelectToggle?.invoke() ?: onClick() },
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(status = item.status)
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Spacing.md),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
