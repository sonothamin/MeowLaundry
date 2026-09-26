package com.sonothamin.meowlaundry.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingType
import com.sonothamin.meowlaundry.data.LabelCustomization
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintServerSettings
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.print.LabelRenderer
import com.sonothamin.meowlaundry.ui.theme.Spacing
import kotlin.math.roundToInt

/** Fake ticket + garments used only to render the live preview - never saved anywhere. */
private val previewTicket = LaundryTicket(id = 12, serviceType = ServiceType.WASH_AND_PRESS, providerName = "Fresh & Clean")
private val previewGarments = listOf(
    ClothingItem(id = 1, title = "Blue Oxford Shirt", type = ClothingType.TOP),
    ClothingItem(id = 2, title = "Grey Wool Trousers", type = ClothingType.BOTTOM),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrintScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val printSettings by viewModel.printSettings.collectAsStateWithLifecycle()
    val labelCustomization by viewModel.labelCustomization.collectAsStateWithLifecycle()
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

    var headerText by remember(labelCustomization.headerText) { mutableStateOf(labelCustomization.headerText) }
    var footerText by remember(labelCustomization.footerText) { mutableStateOf(labelCustomization.footerText) }
    var showGarmentType by remember(labelCustomization.showGarmentType) { mutableStateOf(labelCustomization.showGarmentType) }
    // Recomputed on every edit so the preview always reflects exactly what's about to be saved.
    val previewCustomization = LabelCustomization(headerText, footerText, showGarmentType)
    val previewBitmap = remember(previewCustomization) {
        LabelRenderer.renderTicket(previewTicket, previewGarments, previewCustomization)
    }

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

            HorizontalDivider()

            Text("Ticket layout", style = MaterialTheme.typography.titleMedium)
            Text(
                "What prints on the ticket itself. The preview below updates as you type.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = headerText,
                onValueChange = { headerText = it },
                label = { Text("Header") },
                placeholder = { Text("MeowLaundry") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = footerText,
                onValueChange = { footerText = it },
                label = { Text("Footer") },
                placeholder = { Text("Please keep this ticket until pickup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showGarmentType, onCheckedChange = { showGarmentType = it })
                Text("Show each garment's category (Top, Bottom...)", modifier = Modifier.padding(start = Spacing.sm))
            }

            Text("Preview", style = MaterialTheme.typography.labelLarge)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Preview of the printed ticket with your current header, footer and layout choices",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 260.dp).padding(Spacing.sm),
                )
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
                        viewModel.updateLabelCustomization(previewCustomization)
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
