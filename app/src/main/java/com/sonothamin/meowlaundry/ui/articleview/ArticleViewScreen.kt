package com.sonothamin.meowlaundry.ui.articleview

import com.sonothamin.meowlaundry.ui.adaptive.contentMaxWidth
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.ui.components.archiveReasonLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing

/**
 * Garment detail screen. Top to bottom: app bar (back, quick laundry action, edit, overflow),
 * a square, Material rounded-corner hero photo, the title, an at-a-glance info block (status, last washed,
 * last pressed, added, trips, value), a quick laundry button, notes, expandable details, and an
 * Activity feed with the garment's care history. The sections themselves live in ArticleSections.kt;
 * this function only wires state to them.
 */
@Composable
fun ArticleViewScreen(
    viewModel: ArticleViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSendToLaundry: (Long) -> Unit,
    onOpenTicket: (Long) -> Unit,
    /** Swiped to a neighboring garment ([Boolean] = true when moving forward/next); the caller re-points navigation without growing the back stack. */
    onSwipeToItem: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navState by viewModel.navState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var viewerStartPage by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val item = state.item
    val care = remember(state.events) { summarizeCare(state.events) }
    val activity = remember(item, state.events) { item?.let { buildActivity(it, state.events) }.orEmpty() }

    Scaffold(
        topBar = {
            ArticleAppBar(
                item = item,
                activeTicketId = care.activeTicketId,
                onBack = onBack,
                onEdit = onEdit,
                onSendToLaundry = onSendToLaundry,
                onOpenTicket = onOpenTicket,
                onRestore = { viewModel.unarchive() },
                onArchive = { showArchiveDialog = true },
                onDelete = { showDeleteConfirm = true },
            )
        },
    ) { padding ->
        if (item == null) return@Scaffold

        // Photo carousel: one page per photo (or the legacy single image / a placeholder).
        val pagePaths = remember(state.photos, item.imagePath) {
            if (state.photos.isNotEmpty()) state.photos.map { it.path } else listOf(item.imagePath)
        }
        val pagerState = rememberPagerState(pageCount = { pagePaths.size })

        val canSend = item.status == ClothingStatus.IN_CLOSET
        val ticketToView = care.activeTicketId
            ?.takeIf { item.status != ClothingStatus.ARCHIVED && item.atLaundryQuantity > 0 }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .contentMaxWidth()
                .pageSwipe(navState, onSwipeToItem),
        ) {
            item { PhotoCarousel(pagePaths, pagerState, item.title, onOpen = { viewerStartPage = it }) }
            if (state.photos.size > 1) item { ThumbnailStrip(state.photos, pagerState) }
            item { ArticleTitle(item) }
            item { ArticleInfoBlock(item, care) }
            if (canSend || ticketToView != null) {
                item {
                    LaundryActions(
                        canSend = canSend,
                        ticketToView = ticketToView,
                        onSend = { onSendToLaundry(item.id) },
                        onOpenTicket = onOpenTicket,
                    )
                }
            }
            item { NotesRow(item.notes, onClick = { onEdit(item.id) }) }
            item {
                DetailsCard(
                    item = item,
                    photoCount = state.photos.size,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
            }
            item { SectionTitle("Activity") }
            items(activity, key = { "${it.kind}-${it.ticketId}-${it.at}" }) { entry ->
                ActivityRow(entry = entry, onOpenTicket = onOpenTicket)
            }
            item { Spacer(modifier = Modifier.height(Spacing.lg)) }
        }

        viewerStartPage?.let { start ->
            val photoPaths = pagePaths.filterNotNull()
            if (start in photoPaths.indices) {
                PhotoViewer(
                    paths = photoPaths,
                    startPage = start,
                    title = item.title,
                    onDismiss = { lastPage ->
                        viewerStartPage = null
                        // Leave the carousel on the photo the viewer ended on.
                        scope.launch { pagerState.scrollToPage(lastPage) }
                    },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteArticleDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
        )
    }

    if (showArchiveDialog && item != null) {
        ArchiveDialog(
            onDismiss = { showArchiveDialog = false },
            onConfirm = { reason, notes ->
                showArchiveDialog = false
                viewModel.archive(reason, notes)
            },
        )
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry, onOpenTicket: (Long) -> Unit) {
    val ticketId = entry.ticketId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ticketId != null) Modifier.clickable { onOpenTicket(ticketId) } else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val isProblem = entry.kind == ActivityKind.LOST
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isProblem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (entry.kind) {
                    ActivityKind.ADDED -> Icons.Default.Checkroom
                    ActivityKind.WASH, ActivityKind.WASH_AND_PRESS -> Icons.Default.LocalLaundryService
                    ActivityKind.PRESS -> Icons.Default.AutoAwesome
                    ActivityKind.DRY_CLEAN -> Icons.Default.DryCleaning
                    ActivityKind.OUT_FOR_CARE -> Icons.Default.Schedule
                    ActivityKind.LOST -> Icons.Default.ReportProblem
                    ActivityKind.ARCHIVED -> Icons.Default.Inventory2
                },
                contentDescription = null,
                tint = if (isProblem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            entry.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDialog(
    onDismiss: () -> Unit,
    onConfirm: (ArchiveReason, String?) -> Unit,
) {
    var reason by remember { mutableStateOf(ArchiveReason.DONATED) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive this article?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "It leaves your active closet and moves to the Archive tab. You can restore it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = archiveReasonLabel(reason),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reason") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ArchiveReason.values().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(archiveReasonLabel(r)) },
                                onClick = { reason = r; expanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason, notes.trim().ifBlank { null }) }) { Text("Archive") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
