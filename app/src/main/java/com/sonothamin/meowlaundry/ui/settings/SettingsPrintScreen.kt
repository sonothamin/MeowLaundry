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
import com.sonothamin.meowlaundry.data.PageSize
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintServerSettings
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.data.TicketFormat
import com.sonothamin.meowlaundry.print.LabelRenderer
import com.sonothamin.meowlaundry.print.PdfPageRenderer
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
    var ticketFormat by remember(labelCustomization.format) { mutableStateOf(labelCustomization.format) }
    var pageSize by remember(labelCustomization.pageSize) { mutableStateOf(labelCustomization.pageSize) }
    // Recomputed on every edit so the preview always reflects exactly what's about to be saved.
    val previewCustomization = LabelCustomization(headerText, footerText, showGarmentType, ticketFormat, pageSize)
    val previewBitmap = remember(previewCustomization) {
        if (previewCustomization.format == TicketFormat.PAGE) {
            PdfPageRenderer.renderPreviewBitmap(previewTicket, previewGarments, previewCustomization)
        } else {
            LabelRenderer.renderTicket(previewTicket, previewGarments, previewCustomization)
        }
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
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = { Text("IP") },
                )
                SegmentedButton(
                    selected = printMethod == PrintMethod.SHARE_INTENT,
                    onClick = { printMethod = PrintMethod.SHARE_INTENT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = { Text("App intent") },
                )
                SegmentedButton(
                    selected = printMethod == PrintMethod.NATIVE,
                    onClick = { printMethod = PrintMethod.NATIVE },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = { Text("System") },
                )
            }
            Text(
                when (printMethod) {
                    PrintMethod.NETWORK -> "Talks to the MeowSpool HTTP API directly at the host/port below."
                    PrintMethod.SHARE_INTENT -> "Hands the label to the MeowSpool app via a share intent - no host/port needed."
                    PrintMethod.NATIVE -> "Opens Android's own print dialog - any printer the OS knows about, " +
                        "or \"Save as PDF\". Not MeowSpool-specific."
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

            Text("Ticket format", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = ticketFormat == TicketFormat.RECEIPT,
                    onClick = { ticketFormat = TicketFormat.RECEIPT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = { Text("Receipt") },
                )
                SegmentedButton(
                    selected = ticketFormat == TicketFormat.RECTANGULAR,
                    onClick = { ticketFormat = TicketFormat.RECTANGULAR },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = { Text("Rectangular") },
                )
                SegmentedButton(
                    selected = ticketFormat == TicketFormat.PAGE,
                    onClick = { ticketFormat = TicketFormat.PAGE },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = { Text("Page (PDF)") },
                )
            }
            Text(
                when (ticketFormat) {
                    TicketFormat.RECEIPT -> "An open continuous strip with dashed tear lines, like a thermal receipt."
                    TicketFormat.RECTANGULAR -> "A bordered, self-contained card - a good fit for a sheet-fed printer via System."
                    TicketFormat.PAGE -> "A real text document on standard paper - selectable text, not a bitmap image. " +
                        "Prints via the system dialog, or shares as a plain PDF file."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (ticketFormat == TicketFormat.PAGE) {
                Text("Paper size", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PageSize.entries.forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = pageSize == size,
                            onClick = { pageSize = size },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = PageSize.entries.size),
                            label = { Text(size.label) },
                        )
                    }
                }
            }

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
