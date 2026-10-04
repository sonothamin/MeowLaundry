package com.sonothamin.meowlaundry.ui.settings

import com.sonothamin.meowlaundry.ui.adaptive.contentMaxWidth
import com.sonothamin.meowlaundry.ui.adaptive.BackNavigationIcon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LabelCustomization
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.PDF_HEADER_COLOR_PRESETS
import com.sonothamin.meowlaundry.data.LabelFont
import com.sonothamin.meowlaundry.data.PageMargin
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintServerSettings
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.data.TicketFormat
import com.sonothamin.meowlaundry.print.LabelRenderer
import com.sonothamin.meowlaundry.print.PdfPageRenderer
import com.sonothamin.meowlaundry.ui.components.MeowSpoolInstallBanner
import com.sonothamin.meowlaundry.ui.components.rememberMeowSpoolInstalled
import com.sonothamin.meowlaundry.ui.theme.Spacing
import kotlin.math.roundToInt

/** Fake ticket + garments used only to render the live preview - never saved anywhere. */
private val previewTicket = LaundryTicket(id = 12, serviceType = ServiceType.WASH_AND_PRESS, providerName = "Fresh & Clean")
private val previewGarments = listOf(
    ClothingItem(id = 1, title = "Blue Oxford Shirt", type = "Top"),
    ClothingItem(id = 2, title = "Grey Wool Trousers", type = "Bottom"),
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
            snackbarHostState.showSnackbar(it, withDismissAction = true)
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
    var headerColor by remember(labelCustomization.headerColor) { mutableStateOf(labelCustomization.headerColor) }
    var font by remember(labelCustomization.font) { mutableStateOf(labelCustomization.font) }
    var pageMargin by remember(labelCustomization.pageMargin) { mutableStateOf(labelCustomization.pageMargin) }
    // Recomputed on every edit so the preview always reflects exactly what's about to be saved.
    val previewCustomization = LabelCustomization(
        headerText = headerText,
        footerText = footerText,
        showGarmentType = showGarmentType,
        format = ticketFormat,
        headerColor = headerColor,
        font = font,
        pageMargin = pageMargin,
    )
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
                    BackNavigationIcon(onBack)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .contentMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Hint("Point this at the phone running MeowSpool with its print server switched on.")

            MeowSpoolNudge(printMethod, ticketFormat)

            FieldLabel("Default print method")
            ChoiceRow(PrintMethod.entries, printMethod, { printMethod = it }, PrintMethod::label)
            Hint(printMethod.hint)

            // Host, port, token and darkness only matter when talking to MeowSpool over the network
            // (darkness is thermal-printer contrast; meaningless for the system dialog or a PDF page).
            if (printMethod == PrintMethod.NETWORK) {
                TextInput(host, { host = it }, "Host / IP address", placeholder = "192.168.1.20")
                TextInput(port, { port = it.filter(Char::isDigit) }, "Port")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useToken, onCheckedChange = { useToken = it })
                    Text("Require access token", modifier = Modifier.padding(start = Spacing.sm))
                }
                if (useToken) TextInput(token, { token = it }, "Access token")
                DarknessSlider(darkness) { darkness = it }
            }

            HorizontalDivider()

            Text("Ticket layout", style = MaterialTheme.typography.titleMedium)
            Hint("What prints on the ticket itself. The preview below updates as you type.")

            FieldLabel("Ticket format")
            ChoiceRow(TicketFormat.entries, ticketFormat, { ticketFormat = it }, TicketFormat::label)
            Hint(ticketFormat.hint)

            if (ticketFormat == TicketFormat.PAGE) {
                Hint(
                    "Paper size follows whatever you pick in the print dialog itself (or \u201cSave as PDF\u201d), " +
                        "so the page is never cropped to a size chosen here ahead of time."
                )
                FieldLabel("Margins")
                ChoiceRow(PageMargin.entries, pageMargin, { pageMargin = it }) { it.displayName }
                FieldLabel("Header color")
                ColorSwatches(PDF_HEADER_COLOR_PRESETS, headerColor) { headerColor = it }
            }

            TextInput(headerText, { headerText = it }, "Header", placeholder = "MeowLaundry")
            TextInput(footerText, { footerText = it }, "Footer", placeholder = "Please keep this ticket until pickup")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showGarmentType, onCheckedChange = { showGarmentType = it })
                Text("Show each garment's category (Top, Bottom...)", modifier = Modifier.padding(start = Spacing.sm))
            }

            FieldLabel("Font")
            ChoiceRow(LabelFont.entries, font, { font = it }) { it.displayName }

            FieldLabel("Preview")
            TicketPreview(previewBitmap)

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = {
                        viewModel.savePrintSettings(
                            PrintServerSettings(
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 8631,
                                token = if (useToken) token.trim() else "",
                                darkness = darkness.roundToInt().coerceIn(0, 100),
                                dither = "sharp",
                                printMethod = printMethod,
                            ),
                            previewCustomization,
                        )
                    },
                ) { Text("Save") }
                if (printMethod == PrintMethod.NETWORK) {
                    OutlinedButton(onClick = viewModel::testConnection, enabled = !uiState.isTestingConnection) {
                        Text(if (uiState.isTestingConnection) "Testing\u2026" else "Test connection")
                    }
                }
            }
            if (printMethod == PrintMethod.NETWORK) {
                uiState.connectionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// --- Building blocks --------------------------------------------------------

private fun PrintMethod.label() = when (this) {
    PrintMethod.NETWORK -> "IP"
    PrintMethod.SHARE_INTENT -> "App intent"
    PrintMethod.NATIVE -> "System"
}

private val PrintMethod.hint: String
    get() = when (this) {
        PrintMethod.NETWORK -> "Talks to the MeowSpool HTTP API directly at the host/port below."
        PrintMethod.SHARE_INTENT -> "Hands the label to the MeowSpool app via a share intent - no host/port needed."
        PrintMethod.NATIVE -> "Opens Android's own print dialog - any printer the OS knows about, " +
            "or \"Save as PDF\". Not MeowSpool-specific."
    }

private fun TicketFormat.label() = when (this) {
    TicketFormat.RECEIPT -> "Receipt"
    TicketFormat.PAGE -> "Page (PDF)"
}

private val TicketFormat.hint: String
    get() = when (this) {
        TicketFormat.RECEIPT -> "An open continuous strip with dashed tear lines, like a thermal receipt."
        TicketFormat.PAGE -> "A real text document on standard paper - selectable text, not a bitmap image. " +
            "Always opens Android's own print dialog, which also offers \u201cSave as PDF\u201d."
    }

/** Small grey explanatory text under a control. */
@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, label: String, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Segmented picker over any small set of options (print method, format, margins, font). */
@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun DarknessSlider(value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Print darkness", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${value.roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..100f)
    }
}

@Composable
private fun ColorSwatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        colors.forEach { argb ->
            val isSelected = selected == argb
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TicketPreview(bitmap: android.graphics.Bitmap) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Preview of the printed ticket with your current header, footer and layout choices",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().widthIn(max = 260.dp).padding(Spacing.sm),
        )
    }
}

/**
 * Suggests installing MeowSpool when the current (unsaved) choices lean on it. Disappears on its
 * own once the app is installed.
 */
@Composable
private fun MeowSpoolNudge(method: PrintMethod, format: TicketFormat) {
    val installed by rememberMeowSpoolInstalled()
    if (installed) return
    val message = when {
        method == PrintMethod.SHARE_INTENT ->
            "MeowSpool isn't installed on this phone. \"App intent\" printing hands the label to it, so it won't work until you install it."
        method == PrintMethod.NETWORK ->
            "MeowSpool isn't installed on this phone. That's fine if the print server runs on another phone; otherwise install it here."
        format == TicketFormat.RECEIPT ->
            "The receipt style is made for thermal printers, which MeowSpool drives. It isn't installed on this phone yet."
        else -> return
    }
    MeowSpoolInstallBanner(message)
}
