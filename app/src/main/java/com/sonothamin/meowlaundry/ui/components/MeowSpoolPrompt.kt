package com.sonothamin.meowlaundry.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sonothamin.meowlaundry.print.MeowSpoolInstall

/**
 * Whether MeowSpool is installed right now. Re-checked every time the screen resumes, so the
 * prompt disappears by itself when the person installs it and switches back to this app.
 */
@Composable
fun rememberMeowSpoolInstalled(): State<Boolean> {
    val context = LocalContext.current
    val installed = remember { mutableStateOf(MeowSpoolInstall.isInstalled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        installed.value = MeowSpoolInstall.isInstalled(context)
    }
    return installed
}

/** Inline, non-blocking nudge to install MeowSpool. */
@Composable
fun MeowSpoolInstallBanner(message: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Button(onClick = { MeowSpoolInstall.openInstallPage(context) }) { Text("Get MeowSpool") }
        }
    }
}

/** Shown when a print was sent to MeowSpool but the app isn't there to receive it. */
@Composable
fun MeowSpoolMissingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Print, contentDescription = null) },
        title = { Text("MeowSpool isn't installed") },
        text = {
            Text(
                "This ticket is set to print through the MeowSpool app, but it isn't on this phone. " +
                    "Install it to print to your thermal printer, or switch to \"System\" printing in " +
                    "Settings > Print & label."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                MeowSpoolInstall.openInstallPage(context)
                onDismiss()
            }) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/**
 * Everything a screen needs to hand a print job to MeowSpool: [launch] an intent aimed at it, or
 * [showMeowSpoolMissing] when the app is known to be absent, then call [Dialog] once in the
 * screen. If launching fails because the app vanished, the same install prompt appears.
 */
class PrintHandoff internal constructor(private val context: Context) {
    private var missing by mutableStateOf(false)

    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            missing = true
        }
    }

    fun showMeowSpoolMissing() {
        missing = true
    }

    @Composable
    fun Dialog() {
        if (missing) MeowSpoolMissingDialog(onDismiss = { missing = false })
    }
}

@Composable
fun rememberPrintHandoff(): PrintHandoff {
    val context = LocalContext.current
    return remember(context) { PrintHandoff(context) }
}
