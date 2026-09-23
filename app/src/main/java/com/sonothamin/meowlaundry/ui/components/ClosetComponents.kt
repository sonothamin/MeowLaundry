package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckroomOutlined
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.ui.theme.Spacing

@Composable
fun statusColor(status: ClothingStatus): Color = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.tertiaryContainer
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.primaryContainer
    ClothingStatus.LOST -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun statusLabel(status: ClothingStatus): String = when (status) {
    ClothingStatus.IN_CLOSET -> "In closet"
    ClothingStatus.AT_LAUNDRY -> "At laundry"
    ClothingStatus.LOST -> "Lost"
}

@Composable
fun StatusChip(status: ClothingStatus, modifier: Modifier = Modifier) {
    SuggestionChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(statusLabel(status)) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            disabledContainerColor = statusColor(status),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun ClothingCard(
    item: ClothingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
) {
    Card(
        onClick = { onSelectToggle?.invoke() ?: onClick() },
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Default.CheckroomOutlined,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
