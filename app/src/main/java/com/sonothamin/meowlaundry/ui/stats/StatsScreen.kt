package com.sonothamin.meowlaundry.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.data.CurrencyAmount
import com.sonothamin.meowlaundry.ui.components.EmptyState
import com.sonothamin.meowlaundry.ui.components.InfoBlock
import com.sonothamin.meowlaundry.ui.components.InfoCell
import com.sonothamin.meowlaundry.ui.components.serviceIcon
import com.sonothamin.meowlaundry.ui.components.serviceLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.util.Locale

/** Laundry stats: headline numbers, trips per month, most-washed garments and a provider breakdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenItem: (Long) -> Unit,
    /** Opens the nav drawer. Null hides the hamburger icon. */
    onMenuClick: (() -> Unit)? = null,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    onMenuClick?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = ui ?: return@Scaffold
        if (!data.hasAnyTrips) {
            EmptyState(
                icon = Icons.Default.Insights,
                title = "No stats yet",
                subtitle = "Send clothes to the laundry and your numbers will show up here.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val stats = data.stats

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                val periods = StatsPeriod.values()
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    periods.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = period == option,
                            onClick = { viewModel.setPeriod(option) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                            label = { Text(option.label, maxLines = 1) },
                        )
                    }
                }
            }

            item { Headline(stats) }

            if (stats.lostValues.isNotEmpty()) {
                item { LostCard(stats.lostValues) }
            }

            item {
                StatCard(icon = Icons.Default.Insights, title = "Laundry trips", subtitle = "Last 6 months") {
                    TripsChart(stats.perMonth)
                }
            }

            if (stats.topGarments.isNotEmpty()) {
                item {
                    StatCard(icon = Icons.Default.Checkroom, title = "Most washed") {
                        stats.topGarments.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenItem(entry.garment.id) }
                                    .padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (entry.garment.imagePath != null) {
                                        AsyncImage(
                                            model = entry.garment.imagePath,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Checkroom,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Text(
                                    entry.garment.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (entry.trips == 1) "1 trip" else "${entry.trips} trips",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (stats.providers.isNotEmpty()) {
                item {
                    StatCard(icon = Icons.Default.Storefront, title = "Providers") {
                        stats.providers.forEachIndexed { index, provider ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        provider.name ?: "No provider set",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (provider.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${plural(provider.trips, "trip")} · ${plural(provider.garments, "garment")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (provider.lost > 0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${provider.lost} lost",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        if (provider.lostValues.isNotEmpty()) {
                                            Text(
                                                formatAmounts(provider.lostValues),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (stats.services.isNotEmpty()) {
                item {
                    StatCard(icon = Icons.Default.LocalLaundryService, title = "By service") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            stats.services.forEach { service ->
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Icon(
                                            serviceIcon(service.type),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            "${serviceLabel(service.type)} · ${service.trips}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(stats: StatsData) {
    InfoBlock(
        rows = listOf(
            listOf(
                InfoCell("Trips", stats.trips.toString(), Icons.Default.LocalLaundryService, sub = "to the laundry"),
                InfoCell("Garments", stats.garmentsSent.toString(), Icons.Default.Checkroom, sub = "sent"),
                InfoCell(
                    "Turnaround",
                    stats.avgTurnaroundDays?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                    Icons.Default.Schedule,
                    sub = if (stats.avgTurnaroundDays != null) "days avg" else "no data yet",
                ),
            ),
            listOf(
                InfoCell("Returned", stats.returned.toString(), Icons.Default.CheckCircle, sub = "back home"),
                InfoCell(
                    "Lost",
                    stats.lost.toString(),
                    Icons.Default.ReportProblem,
                    sub = if (stats.lost == 0) "none" else "garments",
                    valueColor = if (stats.lost > 0) MaterialTheme.colorScheme.error else null,
                ),
                InfoCell(
                    "On time",
                    stats.onTimePercent?.let { "$it%" } ?: "—",
                    Icons.Default.EventAvailable,
                    sub = if (stats.dueTracked > 0) "${stats.onTime} of ${stats.dueTracked}" else "no due dates",
                ),
            ),
        ),
    )
}

@Composable
private fun LostCard(amounts: List<CurrencyAmount>) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column {
                Text(
                    "Lost to the laundry",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    formatAmounts(amounts),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** Plain bar chart: one bar per month, with the count above and the month below. */
@Composable
private fun TripsChart(months: List<MonthTrips>) {
    val maxTrips = (months.maxOfOrNull { it.trips } ?: 0).coerceAtLeast(1)
    val maxBar: Dp = 80.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { month ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (month.trips > 0) month.trips.toString() else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (month.trips == 0) 4.dp else maxBar * month.trips / maxTrips)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            when {
                                month.trips == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                                month.isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            },
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    month.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

private fun formatAmounts(amounts: List<CurrencyAmount>): String =
    amounts.joinToString(" · ") { Currencies.format(it.total, it.currency) }
