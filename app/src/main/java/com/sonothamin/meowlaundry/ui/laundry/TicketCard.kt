package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.DueDates
import com.sonothamin.meowlaundry.data.DueState
import com.sonothamin.meowlaundry.data.TicketStatus
import com.sonothamin.meowlaundry.ui.components.DueChip
import com.sonothamin.meowlaundry.ui.components.serviceIcon
import com.sonothamin.meowlaundry.ui.components.serviceLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * One laundry ticket as a card. Top to bottom: a tonal service badge + overline/title + status
 * pill; an overlapping stack of the garments' photos with a "3 of 5 back" count; a progress bar
 * while things are still out; and a footer with when it was sent and the due-date chip.
 * Selecting a ticket swaps the badge for a check and tints the whole card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TicketCard(
    overview: TicketOverview,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ticket = overview.ticket
    val scheme = MaterialTheme.colorScheme
    val due = remember(ticket) { DueDates.info(ticket) }
    val overdue = due?.state == DueState.OVERDUE
    val closed = ticket.status == TicketStatus.CLOSED

    val container by animateColorAsState(
        targetValue = when {
            selected -> scheme.primaryContainer
            closed -> scheme.surfaceContainerLow
            else -> scheme.surfaceContainerHigh
        },
        label = "ticket-card-container",
    )
    val (badgeBg, badgeFg) = when {
        selected -> scheme.primary to scheme.onPrimary
        overdue -> scheme.errorContainer to scheme.onErrorContainer
        closed -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        ticket.status == TicketStatus.RECEIVED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }

    val shape = MaterialTheme.shapes.extraLarge
    Card(
        // clip before clickable so the ripple follows the rounded corners
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(badgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else serviceIcon(ticket.serviceType),
                        contentDescription = if (selected) "Selected" else null,
                        tint = badgeFg,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull("Ticket #${ticket.id}", ticket.providerName?.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = serviceLabel(ticket.serviceType),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(ticket.status)
            }

            // Garments: photo stack + how many are back
            if (overview.total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThumbStack(
                        paths = overview.thumbs,
                        extra = overview.total - overview.thumbs.size,
                        ringColor = container,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = backLabel(overview, closed), style = MaterialTheme.typography.labelLarge)
                        if (overview.lost > 0) {
                            Text(
                                text = "${overview.lost} lost",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.error,
                            )
                        }
                    }
                }
                if (!closed) {
                    LinearProgressIndicator(
                        progress = { overview.accountedFor.toFloat() / overview.total },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (overdue) scheme.error else scheme.primary,
                        trackColor = scheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        gapSize = 4.dp,
                        drawStopIndicator = {},
                    )
                }
            }

            // Footer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = sentLabel(ticket.sentAt, ticket.receivedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.xs).weight(1f),
                )
                due?.let { DueChip(it) }
            }
        }
    }
}

@Composable
private fun StatusPill(status: TicketStatus) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, label) = when (status) {
        TicketStatus.SENT -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, "Out")
        TicketStatus.PARTIALLY_RECEIVED -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "Partly back")
        TicketStatus.RECEIVED -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "All back")
        TicketStatus.CLOSED -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, "Closed")
    }
    Surface(shape = CircleShape, color = bg, contentColor = fg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
        )
    }
}

/** Overlapping round garment photos, with a "+N" bubble when there are more than shown. */
@Composable
private fun ThumbStack(paths: List<String?>, extra: Int, ringColor: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy((-12).dp), verticalAlignment = Alignment.CenterVertically) {
        paths.forEach { path ->
            Box(
                modifier = Modifier.size(44.dp).border(3.dp, ringColor, CircleShape).padding(3.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (path != null) {
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(38.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.Checkroom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier.size(44.dp).border(3.dp, ringColor, CircleShape).padding(3.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+$extra",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

private fun backLabel(o: TicketOverview, closed: Boolean): String = when {
    closed && o.lost == 0 -> "All ${o.total} back"
    closed -> "${o.returned} back"
    else -> "${o.returned} of ${o.total} back"
}

/** "Sent today · 21:36", plus "· back 3 days ago" once it has been received. */
private fun sentLabel(sentAt: Long, receivedAt: Long?): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sentAt))
    val sent = "Sent ${relativeDay(sentAt)} · $time"
    return if (receivedAt != null) "$sent · back ${relativeDay(receivedAt)}" else sent
}

private fun relativeDay(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(day, LocalDate.now(zone))) {
        0L -> "today"
        1L -> "yesterday"
        in 2L..6L -> "$days days ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
    }
}
