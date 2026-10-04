package com.sonothamin.meowlaundry.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.Suggestions
import com.sonothamin.meowlaundry.ui.theme.Spacing

@Composable
fun statusColor(status: ClothingStatus): Color = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.tertiaryContainer
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.primaryContainer
    ClothingStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant
}

// Paired "on" color for each status container above, so the icon always meets
// contrast guidelines against its badge background regardless of theme.
@Composable
fun statusContentColor(status: ClothingStatus): Color = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.onTertiaryContainer
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.onPrimaryContainer
    ClothingStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun statusLabel(status: ClothingStatus): String = when (status) {
    ClothingStatus.IN_CLOSET -> "In closet"
    ClothingStatus.AT_LAUNDRY -> "At laundry"
    ClothingStatus.ARCHIVED -> "Archived"
}

fun statusIcon(status: ClothingStatus): ImageVector = when (status) {
    ClothingStatus.IN_CLOSET -> Icons.Filled.Checkroom
    ClothingStatus.AT_LAUNDRY -> Icons.Filled.LocalLaundryService
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

/** Small snowflake badge marking a garment tagged as winter wear. */
@Composable
fun WinterBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AcUnit,
            contentDescription = "Winter wear",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}


/** Units still owned, e.g. "x7" - null for an ordinary single garment, so nothing extra is shown. */
fun quantityLabel(item: ClothingItem): String? =
    if (item.quantity <= 1 && item.atLaundryQuantity <= 1) null else "\u00d7${item.ownedQuantity}"

/** "5 out" when only some of a multi-unit article's units are at the laundry. */
fun partlyOutLabel(item: ClothingItem): String? =
    if (item.ownedQuantity > 1 && item.atLaundryQuantity in 1 until item.ownedQuantity) "${item.atLaundryQuantity} out" else null

/** Type, plus the unit count and how many are out, joined for the small line under a title. */
fun clothingSubtitle(item: ClothingItem): String =
    listOfNotNull(
        Suggestions.displayCategory(item.type),
        quantityLabel(item),
        partlyOutLabel(item),
    ).joinToString(" \u00b7 ")

/**
 * Small badge overlaid on a photo showing how many units the article stands for. Leans into MD3
 * Expressive: a lopsided "flag" shape (rounded on three corners, sharp on one) instead of a plain
 * pill, solid high-contrast tertiary rather than a soft container, and a spring pop whenever the
 * count changes so it doesn't just silently swap digits.
 */
@Composable
fun QuantityBadge(text: String, modifier: Modifier = Modifier) {
    val shape = remember {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(text) {
        pop.snapTo(0.65f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Surface(
        modifier = modifier.scale(pop.value),
        shape = shape,
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shadowElevation = 3.dp,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/**
 * Pill-shaped -/value/+ control for choosing a number of units between [min] and [max]. The two
 * buttons are deliberately mismatched (a quiet tonal "fewer", a bold filled "more") rather than a
 * matched pair of plain icon buttons, and the number animates in and out on a small vertical
 * spring whenever it changes instead of just replacing itself.
 */
@Composable
fun QuantityStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onChange((value - 1).coerceAtLeast(min)) },
                enabled = enabled && value > min,
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Fewer", modifier = Modifier.size(16.dp))
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val goingUp = targetState > initialState
                    val enter = slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    ) { height -> if (goingUp) height else -height } + fadeIn()
                    val exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    ) { height -> if (goingUp) -height else height } + fadeOut()
                    enter.togetherWith(exit)
                },
                label = "quantity-stepper-value",
            ) { animatedValue ->
                Text(
                    animatedValue.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
            FilledIconButton(
                onClick = { onChange((value + 1).coerceAtMost(max)) },
                enabled = enabled && value < max,
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = "More", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClothingCard(
    item: ClothingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** The item currently open in a list-detail pane: tinted like a selection, but without the checkmark. */
    highlighted: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Optional content under the title, e.g. the quantity stepper on the send-to-laundry screen. */
    footer: (@Composable () -> Unit)? = null,
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
            containerColor = if (selected || highlighted) MaterialTheme.colorScheme.secondaryContainer
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
                    ThumbImage(
                        path = item.imagePath,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Checkroom,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.isWinterWear) {
                    WinterBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.xs),
                    )
                }
                quantityLabel(item)?.let {
                    QuantityBadge(text = it, modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xs))
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
                    text = listOfNotNull(
                        Suggestions.displayCategory(item.type),
                        partlyOutLabel(item),
                    ).joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                footer?.invoke()
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
    /** The item currently open in a list-detail pane: tinted like a selection, but without the checkmark. */
    highlighted: Boolean = false,
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
            containerColor = if (selected || highlighted) MaterialTheme.colorScheme.secondaryContainer
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
                    ThumbImage(
                        path = item.imagePath,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
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
                    text = clothingSubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.isWinterWear) WinterBadge()
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
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
