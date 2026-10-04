package com.sonothamin.meowlaundry.ui.articleview

import com.sonothamin.meowlaundry.ui.adaptive.BackNavigationIcon
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.ui.components.InfoBlock
import com.sonothamin.meowlaundry.ui.components.InfoCell
import com.sonothamin.meowlaundry.ui.components.archiveReasonLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing
import androidx.compose.foundation.pager.PagerState
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingItemPhoto
import com.sonothamin.meowlaundry.data.Suggestions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The building blocks of ArticleViewScreen: each takes only what it shows, so the screen itself
 * just wires state to them.
 */

/** App bar: back, quick laundry action, edit, and an overflow menu (archive/restore, delete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleAppBar(
    item: ClothingItem?,
    activeTicketId: Long?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSendToLaundry: (Long) -> Unit,
    onOpenTicket: (Long) -> Unit,
    onRestore: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {},
        navigationIcon = {
            BackNavigationIcon(onBack)
        },
        actions = {
            if (item != null) {
                // Quick laundry action: send it off, or jump to the ticket it's already out on.
                // A multi-unit article can be both: some units home to send, some still out.
                if (item.status == ClothingStatus.IN_CLOSET) {
                    IconButton(onClick = { onSendToLaundry(item.id) }) {
                        Icon(Icons.Default.LocalLaundryService, contentDescription = "Send to laundry")
                    }
                }
                if (item.status != ClothingStatus.ARCHIVED && item.atLaundryQuantity > 0 && activeTicketId != null) {
                    IconButton(onClick = { onOpenTicket(activeTicketId) }) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = "View laundry ticket")
                    }
                }
                IconButton(onClick = { onEdit(item.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit article")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (item.status == ClothingStatus.ARCHIVED) {
                            DropdownMenuItem(
                                text = { Text("Restore to closet") },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                onClick = { menuOpen = false; onRestore() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                                onClick = { menuOpen = false; onArchive() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

/**
 * Gallery-style article paging: the whole page follows the finger, then either flies off
 * (and the neighbor slides in from the other side) or springs back. The photo carousel
 * consumes horizontal drags inside its own bounds first, so swiping photos never pages the
 * article - this gesture only sees drags that start elsewhere on the screen.
 */
@Composable
internal fun Modifier.pageSwipe(navState: ArticleNavState, onSwipe: (Long, Boolean) -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val latestNavState = rememberUpdatedState(navState)
    val latestOnSwipe = rememberUpdatedState(onSwipe)
    val dragOffset = remember { Animatable(0f) }
    // Short swipe: the page moves 1.6x the finger, and ~64dp of page travel (~40dp of finger) commits.
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    var pageWidthPx by remember { mutableStateOf(1f) }

    return this
        .onSizeChanged { pageWidthPx = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val nav = latestNavState.value
                    val gained = dragAmount * 1.6f
                    val target = dragOffset.value + gained
                    // Rubber-band when there's nothing further in that direction.
                    val hasNeighbor = if (target > 0) nav.previousId != null else nav.nextId != null
                    val applied = if (hasNeighbor) gained else gained * 0.2f
                    scope.launch { dragOffset.snapTo(dragOffset.value + applied) }
                },
                onDragEnd = {
                    val nav = latestNavState.value
                    val offset = dragOffset.value
                    val goPrevious = offset > swipeThresholdPx && nav.previousId != null
                    val goNext = offset < -swipeThresholdPx && nav.nextId != null
                    scope.launch {
                        if (goPrevious || goNext) {
                            dragOffset.animateTo(if (goPrevious) pageWidthPx else -pageWidthPx, tween(160))
                            val id = if (goPrevious) nav.previousId!! else nav.nextId!!
                            latestOnSwipe.value(id, goNext)
                        } else {
                            dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
                onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
            )
        }
        .graphicsLayer {
            translationX = dragOffset.value
            val progress = (kotlin.math.abs(dragOffset.value) / pageWidthPx).coerceIn(0f, 1f)
            alpha = 1f - 0.5f * progress
            val scale = 1f - 0.06f * progress
            scaleX = scale
            scaleY = scale
        }
}

/** Hero carousel: square crop with the Material extra-large rounded corners. */
@Composable
internal fun PhotoCarousel(
    pagePaths: List<String?>,
    pagerState: PagerState,
    title: String,
    /** Tapped a photo: open it full screen, starting at this page. */
    onOpen: (Int) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val path = pagePaths[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (path != null) {
                                Modifier.clickable(onClickLabel = "View photo full screen") { onOpen(page) }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (path != null) {
                        AsyncImage(
                            model = path,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (pagePaths.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.sm)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(pagePaths.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (active) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (active) 1f else 0.6f)),
                        )
                    }
                }
            }
        }
    }
}

/** Thumbnail strip, kept in sync with the carousel; tapping one jumps to that photo. */
@Composable
internal fun ThumbnailStrip(photos: List<ClothingItemPhoto>, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
            val isSelected = pagerState.currentPage == index
            AsyncImage(
                model = photo.path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

@Composable
internal fun ArticleTitle(item: ClothingItem) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(categoryLabel(item), item.brand).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The at-a-glance block directly under the title. */
@Composable
internal fun ArticleInfoBlock(item: ClothingItem, care: CareSummary) {
    Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
        InfoBlock(
            rows = listOf(
                listOf(
                    InfoCell(
                        "Status",
                        statusLabel(item.status),
                        statusIcon(item.status),
                        // For several identical units, say where they are: "5 home · 2 out".
                        sub = if (item.quantity > 1 && item.status != ClothingStatus.ARCHIVED) {
                            listOfNotNull(
                                "${item.inClosetQuantity} home",
                                if (item.atLaundryQuantity > 0) "${item.atLaundryQuantity} out" else null,
                                if (item.lostQuantity > 0) "${item.lostQuantity} lost" else null,
                            ).joinToString(" \u00b7 ")
                        } else null,
                        valueColor = statusColor(item.status),
                    ),
                    dateCell("Last washed", Icons.Default.LocalLaundryService, care.lastWashedAt),
                    dateCell("Last pressed", Icons.Default.AutoAwesome, care.lastPressedAt),
                ),
                listOf(
                    dateCell("Added", Icons.Default.CalendarToday, item.createdAt, neverLabel = "—"),
                    InfoCell(
                        "Laundry trips",
                        care.timesSent.toString(),
                        Icons.Default.Repeat,
                        sub = if (care.timesSent == 1) "time" else "times",
                    ),
                    InfoCell(
                        "Value",
                        item.price?.let { Currencies.format(it, item.currency) } ?: "—",
                        Icons.Default.Sell,
                        sub = if (item.price != null) "to replace" else null,
                    ),
                ),
            ),
        )
    }
}

/** Quick laundry actions (mirror the app bar): send it off, or open the ticket it's out on. */
@Composable
internal fun LaundryActions(
    canSend: Boolean,
    ticketToView: Long?,
    onSend: () -> Unit,
    onOpenTicket: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (canSend) {
            FilledTonalButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.LocalLaundryService, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Send to laundry", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
        if (ticketToView != null) {
            FilledTonalButton(onClick = { onOpenTicket(ticketToView) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("View laundry ticket", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

@Composable
internal fun NotesRow(notes: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            notes?.takeIf { it.isNotBlank() } ?: "Add a note…",
            style = MaterialTheme.typography.bodyMedium,
            color = if (notes.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/** Expandable card with the article's less-glanceable facts. */
@Composable
internal fun DetailsCard(item: ClothingItem, photoCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    fun stamp(at: Long) = "${dateFormat.format(Date(at))}, ${timeFormat.format(Date(at)).lowercase(Locale.getDefault())}"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                listOf(
                    "Category" to categoryLabel(item),
                    "Garment type" to item.garmentType,
                    "Brand" to item.brand,
                    "Color" to item.color,
                    "Photos" to if (photoCount == 0) "None" else "$photoCount",
                    "Added" to stamp(item.createdAt),
                    "Last updated" to item.updatedAt.takeIf { it != item.createdAt }?.let(::stamp),
                ).filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                    DetailRow(label, value!!)
                }
                if (item.status == ClothingStatus.ARCHIVED) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                    DetailRow("Archived as", item.archiveReason?.let { archiveReasonLabel(it) } ?: "—")
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.lg, bottom = Spacing.sm),
    )
}

@Composable
internal fun DeleteArticleDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this article?") },
        text = { Text("This removes it and its photos permanently. This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Top", "Bottom"... from the broad category enum. */
private fun categoryLabel(item: ClothingItem): String =
    Suggestions.displayCategory(item.type)

/** "3 days ago" under the date, or [neverLabel] when there is no date yet. */
private fun dateCell(label: String, icon: ImageVector, at: Long?, neverLabel: String = "Never"): InfoCell {
    if (at == null) return InfoCell(label, neverLabel, icon)
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
    val relative = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    return InfoCell(label, date, icon, sub = relative)
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

private fun statusLabel(status: ClothingStatus): String = when (status) {
    ClothingStatus.IN_CLOSET -> "In closet"
    ClothingStatus.AT_LAUNDRY -> "At laundry"
    ClothingStatus.ARCHIVED -> "Archived"
}

@Composable
private fun statusColor(status: ClothingStatus) = when (status) {
    ClothingStatus.IN_CLOSET -> MaterialTheme.colorScheme.primary
    ClothingStatus.AT_LAUNDRY -> MaterialTheme.colorScheme.tertiary
    ClothingStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Icon that mirrors the status shown in the info grid. */
private fun statusIcon(status: ClothingStatus): ImageVector = when (status) {
    ClothingStatus.IN_CLOSET -> Icons.Default.Checkroom
    ClothingStatus.AT_LAUNDRY -> Icons.Default.LocalLaundryService
    ClothingStatus.ARCHIVED -> Icons.Default.Inventory2
}
