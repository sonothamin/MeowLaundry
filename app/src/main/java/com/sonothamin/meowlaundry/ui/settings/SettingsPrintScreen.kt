package com.sonothamin.meowlaundry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintServerSettings
import com.sonothamin.meowlaundry.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrintScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val printSettings by viewModel.printSettings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    var host by remember(printSettings.host) { mutableStateOf(printSettings.host) }
    var port by remember(printSettings.port) { mutableStateOf(printSettings.port.toString()) }
    var token by remember(printSettings.token) { mutableStateOf(printSettings.token) }
    var darkness by remember(printSettings.darkness) { mutableStateOf(printSettings.darkness.toFloat()) }
    var useToken by remember(printSettings.token) { mutableStateOf(printSettings.token.isNotBlank()) }
    var printMethod by remember(printSettings.printMethod) { mutableStateOf(printSettings.printMethod) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print & label") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Point this at the phone running MeowSpool with its print server switched on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Default print method", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = printMethod == PrintMethod.NETWORK,
                    onClick = { printMethod = PrintMethod.NETWORK },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("IP") },
                )
                SegmentedButton(
                    selected = printMethod == PrintMethod.SHARE_INTENT,
                    onClick = { printMethod = PrintMethod.SHARE_INTENT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("App intent") },
                )
            }
            Text(
                if (printMethod == PrintMethod.NETWORK) {
                    "Talks to the MeowSpool HTTP API directly at the host/port below."
                } else {
                    "Hands the label to the MeowSpool app via a share intent - no host/port needed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Host, port and token only matter when talking to MeowSpool over the network.
            if (printMethod == PrintMethod.NETWORK) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP address") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useToken, onCheckedChange = { useToken = it })
                    Text("Require access token", modifier = Modifier.padding(start = Spacing.sm))
                }
                if (useToken) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Print darkness", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${darkness.roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(value = darkness, onValueChange = { darkness = it }, valueRange = 0f..100f)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = {
                        viewModel.updateSettings(
                            PrintServerSettings(
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 8631,
                                token = if (useToken) token.trim() else "",
                                darkness = darkness.roundToInt().coerceIn(0, 100),
                                dither = "sharp",
                                printMethod = printMethod,
                            )
                        )
                    },
                ) { Text("Save") }
                if (printMethod == PrintMethod.NETWORK) {
                    OutlinedButton(onClick = viewModel::testConnection, enabled = !uiState.isTestingConnection) {
                        Text(if (uiState.isTestingConnection) "Testing…" else "Test connection")
                    }
                }
            }
            if (printMethod == PrintMethod.NETWORK) {
                uiState.connectionStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
