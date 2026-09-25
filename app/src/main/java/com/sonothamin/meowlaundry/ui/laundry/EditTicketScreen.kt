package com.sonothamin.meowlaundry.ui.laundry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.ui.components.DueDateChips
import com.sonothamin.meowlaundry.ui.components.serviceIcon
import com.sonothamin.meowlaundry.ui.components.serviceLabel
import com.sonothamin.meowlaundry.ui.theme.Spacing

/**
 * Edit an existing ticket's service, provider, due date and notes - the fields chosen once at
 * "send to laundry" time and otherwise stuck. Which garments are on the ticket and their
 * pending/returned/lost state stay on Ticket Detail; this screen is purely the ticket's own info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTicketScreen(
    viewModel: EditTicketViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit ticket") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledIconButton(onClick = viewModel::save, enabled = state.isValid) {
                        Icon(Icons.Default.Check, contentDescription = "Save changes")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Service + provider grouped in one card, same language as the info block on
            // Ticket Detail - they're read together there, so they're edited together here.
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Service", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ServiceType.values().forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = state.serviceType == type,
                                onClick = { viewModel.onServiceTypeChange(type) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ServiceType.values().size),
                                icon = {
                                    Icon(
                                        serviceIcon(type),
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                },
                                label = { Text(serviceLabel(type), maxLines = 1) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.providerName,
                        onValueChange = viewModel::onProviderNameChange,
                        label = { Text("Laundry / provider name") },
                        placeholder = { Text("Optional") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            DueDateChips(dueAt = state.dueAt, onChange = viewModel::onDueAtChange)

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                placeholder = { Text("Anything worth remembering about this run") },
                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
