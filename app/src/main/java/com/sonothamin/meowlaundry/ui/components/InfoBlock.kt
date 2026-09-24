package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonothamin.meowlaundry.ui.theme.Spacing

/** One segment of an [InfoBlock]: icon + label on top, a value, and an optional small sub-line. */
data class InfoCell(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val sub: String? = null,
    val valueColor: Color? = null,
)

/** One rounded surface split into equal segments (dividers between), like the closet summary. */
@Composable
fun InfoBlock(rows: List<List<InfoCell>>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            rows.forEachIndexed { rowIndex, cells ->
                if (rowIndex > 0) HorizontalDivider()
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    cells.forEachIndexed { cellIndex, cell ->
                        if (cellIndex > 0) VerticalDivider()
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm, vertical = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // Icon + label, with extra breathing room before the value below.
                            Row(
                                modifier = Modifier.padding(bottom = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Icon(
                                    imageVector = cell.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    cell.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                cell.value,
                                style = MaterialTheme.typography.titleSmall,
                                color = cell.valueColor ?: MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                cell.sub ?: " ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
