package com.sonothamin.meowlaundry.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonothamin.meowlaundry.R
import com.sonothamin.meowlaundry.ui.theme.Spacing

private const val REPO_URL = "https://github.com/sonothamin/MeowLaundry"
private const val MEOWSPOOL_URL = "https://github.com/sonothamin/MeowSpool"
private const val ISSUES_URL = "https://github.com/sonothamin/MeowLaundry/issues"

private data class AboutLink(val icon: ImageVector, val title: String, val subtitle: String, val url: String)

/**
 * App identity: icon, name, version, where the source lives, the companion printer app, and a
 * quick privacy note - the boring-but-necessary "about" screen every app ends up needing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionLabel = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${info.versionName}"
        }.getOrDefault("")
    }

    val links = listOf(
        AboutLink(Icons.Default.Code, "Source code", "github.com/sonothamin/MeowLaundry", REPO_URL),
        AboutLink(Icons.Default.Print, "MeowSpool", "The companion cat printer app", MEOWSPOOL_URL),
        AboutLink(Icons.Default.BugReport, "Report an issue", "Open a GitHub issue", ISSUES_URL),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.lg),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // painterResource() can't load an <adaptive-icon> mipmap directly (it isn't a
                    // plain VectorDrawable/BitmapDrawable, and trying crashes at runtime) - so the
                    // background and foreground layers are drawn separately here instead, exactly
                    // like the launcher itself composites them, just clipped to a circle.
                    Box(
                        modifier = Modifier.size(88.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text("MeowLaundry", style = MaterialTheme.typography.headlineSmall)
                    if (versionLabel.isNotBlank()) {
                        Text(
                            versionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Tracks what's in your closet and what's out at the laundry, entirely on-device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            items(links) { link ->
                ListItem(
                    headlineContent = { Text(link.title) },
                    supportingContent = { Text(link.subtitle) },
                    leadingContent = { Icon(link.icon, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(link.url) },
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        "Privacy",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "All data stays in this app's private storage. The only network calls it " +
                            "ever makes are to a MeowSpool print server address you configure yourself, " +
                            "on your own local network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Inter and Google Sans Flex are bundled under the SIL Open Font License.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}
