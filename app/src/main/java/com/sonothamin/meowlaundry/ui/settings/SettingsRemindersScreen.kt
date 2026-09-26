package com.sonothamin.meowlaundry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.ui.components.rememberNotificationPermissionRequester
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Presets for quick access; "Custom" opens the exact-time picker below. */
private val presets = listOf("Morning" to (9 to 0), "Afternoon" to (14 to 0), "Evening" to (18 to 0))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRemindersScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val reminderHour by viewModel.reminderHour.collectAsStateWithLifecycle()
    val reminderMinute by viewModel.reminderMinute.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequester()
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedPreset = presets.firstOrNull { it.second == reminderHour to reminderMinute }?.first
    val timeLabel = remember(reminderHour, reminderMinute) {
        LocalTime.of(reminderHour, reminderMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = {
                        viewModel.setRemindersEnabled(it)
                        if (it) askNotificationPermission()
                    },
                )
                Text("Remind me when laundry is due", modifier = Modifier.padding(start = Spacing.sm))
            }
            Text(
                "A notification the day before, on the due date, and each day a ticket is overdue. " +
                    "Set a due date when you send clothes, or on the ticket later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (remindersEnabled) {
                Text("Remind me at", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    presets.forEachIndexed { index, (label, time) ->
                        SegmentedButton(
                            selected = selectedPreset == label,
                            onClick = { viewModel.setReminderTime(time.first, time.second) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size),
                            label = { Text(label) },
                        )
                    }
                }

                // Exact time, for anyone the three presets don't fit.
                Card(
                    onClick = { showTimePicker = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exact time", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (selectedPreset != null) "Currently $timeLabel · tap to pick a different time"
                                else "Tap to change",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(timeLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedButton(onClick = { askNotificationPermission(); viewModel.sendTestReminder() }) {
                    Text("Send a test notification")
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    viewModel.setReminderTime(timePickerState.hour, timePickerState.minute)
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}
