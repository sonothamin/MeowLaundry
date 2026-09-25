package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonothamin.meowlaundry.data.DueDates
import com.sonothamin.meowlaundry.data.DueInfo
import com.sonothamin.meowlaundry.data.DueState
import com.sonothamin.meowlaundry.ui.theme.Spacing

/** Small pill: "Overdue 2 days" (red), "Due today" / "Due tomorrow" (accent), or a quiet "Due in 5 days". */
@Composable
fun DueChip(info: DueInfo, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (info.state) {
        DueState.OVERDUE -> scheme.errorContainer to scheme.onErrorContainer
        DueState.TODAY, DueState.SOON -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DueState.LATER -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = when (info.state) {
                    DueState.OVERDUE -> Icons.Default.Warning
                    DueState.TODAY -> Icons.Default.Today
                    else -> Icons.Default.Event
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(info.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Material date picker for a due date. Only today and later can be picked; [onClear] adds a "Clear" button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerDialog(
    initial: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val startAt = initial?.takeIf { it >= DueDates.inDays(0) } ?: DueDates.inDays(1)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DueDates.toPickerUtcMillis(startAt),
        selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= DueDates.todayPickerUtcMillis()
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(DueDates.fromPickerUtcMillis(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("Set date") }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** "Expected back" row of quick picks (None / Tomorrow / 3 days / 1 week) plus a custom date. */
@Composable
fun DueDateChips(
    dueAt: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val tomorrow = remember { DueDates.inDays(1) }
    val threeDays = remember { DueDates.inDays(3) }
    val week = remember { DueDates.inDays(7) }
    val isPreset = dueAt == null || dueAt == tomorrow || dueAt == threeDays || dueAt == week

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(
                Icons.Default.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text("Expected back (optional)", style = MaterialTheme.typography.labelLarge)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(selected = dueAt == null, onClick = { onChange(null) }, label = { Text("No date") })
            FilterChip(selected = dueAt == tomorrow, onClick = { onChange(tomorrow) }, label = { Text("Tomorrow") })
            FilterChip(selected = dueAt == threeDays, onClick = { onChange(threeDays) }, label = { Text("3 days") })
            FilterChip(selected = dueAt == week, onClick = { onChange(week) }, label = { Text("1 week") })
            FilterChip(
                selected = !isPreset,
                onClick = { showPicker = true },
                label = { Text(if (!isPreset && dueAt != null) DueDates.format(dueAt) else "Pick date") },
                leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            // Matches the row's own leading inset, so the last chip doesn't sit flush against
            // the screen edge when scrolled all the way - without this it reads as clipped.
            Spacer(modifier = Modifier.width(Spacing.md))
        }
    }

    if (showPicker) {
        DueDatePickerDialog(
            initial = dueAt,
            onDismiss = { showPicker = false },
            onConfirm = { showPicker = false; onChange(it) },
        )
    }
}
