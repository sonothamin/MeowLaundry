package com.sonothamin.meowlaundry.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintServerSettings
import com.sonothamin.meowlaundry.data.ThemeMode
import com.sonothamin.meowlaundry.ui.components.CurrencyDropdown
import com.sonothamin.meowlaundry.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val printSettings by viewModel.printSettings.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    var host by remember(printSettings.host) { mutableStateOf(printSettings.host) }
    var port by remember(printSettings.port) { mutableStateOf(printSettings.port.toString()) }
    var token by remember(printSettings.token) { mutableStateOf(printSettings.token) }
    var darkness by remember(printSettings.darkness) { mutableStateOf(printSettings.darkness.toString()) }
    var useToken by remember(printSettings.token) { mutableStateOf(printSettings.token.isNotBlank()) }
    var printMethod by remember(printSettings.printMethod) { mutableStateOf(printSettings.printMethod) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
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
            Text("MeowSpool print server", style = MaterialTheme.typography.titleMedium)
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

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP address") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                enabled = printMethod == PrintMethod.NETWORK,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
            OutlinedTextField(
                value = darkness,
                onValueChange = { darkness = it.filter(Char::isDigit) },
                label = { Text("Print darkness (0-100)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = {
                        viewModel.updateSettings(
                            PrintServerSettings(
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 8631,
                                token = if (useToken) token.trim() else "",
                                darkness = darkness.toIntOrNull() ?: 70,
                                dither = "sharp",
                                printMethod = printMethod,
                            )
                        )
                    },
                ) { Text("Save") }
                OutlinedButton(onClick = viewModel::testConnection, enabled = !uiState.isTestingConnection) {
                    Text(if (uiState.isTestingConnection) "Testing…" else "Test connection")
                }
            }
            uiState.connectionStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            Text("Theme", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.values().forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.values().size),
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        },
                    )
                }
            }

            HorizontalDivider()

            Text("Default currency", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pre-selected for the price of new articles. You can still change it per article.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CurrencyDropdown(
                selected = defaultCurrency,
                onSelect = viewModel::setDefaultCurrency,
                label = "Currency",
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Export everything (garments, tickets, photos) to a single file you keep, " +
                    "or restore from one. Nothing is uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = {
                        val name = "meowlaundry-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip"
                        exportLauncher.launch(name)
                    },
                    enabled = !uiState.isExporting,
                ) { Text(if (uiState.isExporting) "Exporting…" else "Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !uiState.isImporting,
                ) { Text(if (uiState.isImporting) "Importing…" else "Import") }
            }
            Text(
                "Importing replaces all current data with the contents of the backup file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
