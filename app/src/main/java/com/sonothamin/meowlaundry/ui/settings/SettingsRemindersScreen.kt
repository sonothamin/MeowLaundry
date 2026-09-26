package com.sonothamin.meowlaundry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.ui.components.rememberNotificationPermissionRequester
import com.sonothamin.meowlaundry.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRemindersScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val reminderHour by viewModel.reminderHour.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequester()

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
                val times = listOf("Morning" to 9, "Afternoon" to 14, "Evening" to 18)
                Text("Remind me in the", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    times.forEachIndexed { index, (label, hour) ->
                        SegmentedButton(
                            selected = reminderHour == hour,
                            onClick = { viewModel.setReminderHour(hour) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = times.size),
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedButton(onClick = { askNotificationPermission(); viewModel.sendTestReminder() }) {
                    Text("Send a test notification")
                }
            }
        }
    }
}
