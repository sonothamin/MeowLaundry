package com.sonothamin.meowlaundry.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.ThemeMode
import com.sonothamin.meowlaundry.ui.theme.UiFont
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

private data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/**
 * The settings menu: a short list of categories rather than every toggle and field dumped
 * onto one long scroll. Each row previews its current value so you don't have to drill in
 * just to check what's set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPrint: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    /** Opens the nav drawer. Null hides the hamburger icon. */
    onMenuClick: (() -> Unit)? = null,
) {
    val printSettings by viewModel.printSettings.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val storedFont by viewModel.uiFontName.collectAsStateWithLifecycle()
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentFont = remember(storedFont) { UiFont.resolve(context, storedFont) }

    val entries = listOf(
        SettingsEntry(
            icon = Icons.Default.Print,
            title = "Print & label",
            subtitle = if (printSettings.printMethod == PrintMethod.NETWORK) {
                "Via IP" + printSettings.host.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            } else {
                "Via app intent"
            },
            onClick = onOpenPrint,
        ),
        SettingsEntry(
            icon = Icons.Default.Notifications,
            title = "Reminders",
            subtitle = if (remindersEnabled) "On" else "Off",
            onClick = onOpenReminders,
        ),
        SettingsEntry(
            icon = Icons.Default.Palette,
            title = "Appearance",
            subtitle = "${themeModeLabel(themeMode)} · ${currentFont.label}",
            onClick = onOpenAppearance,
        ),
        SettingsEntry(
            icon = Icons.Default.Payments,
            title = "Currency",
            subtitle = defaultCurrency,
            onClick = onOpenCurrency,
        ),
        SettingsEntry(
            icon = Icons.Default.Backup,
            title = "Backup & restore",
            subtitle = "Export or import your data",
            onClick = onOpenBackup,
        ),
        SettingsEntry(
            icon = Icons.Default.Info,
            title = "About",
            subtitle = "Version, source code, links",
            onClick = onOpenAbout,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    onMenuClick?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(entries) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(entry.subtitle) },
                    leadingContent = { Icon(entry.icon, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onClick),
                )
            }
        }
    }
}

private fun themeModeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
