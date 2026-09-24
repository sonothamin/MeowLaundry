package com.sonothamin.meowlaundry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ThemeMode
import com.sonothamin.meowlaundry.reminders.Reminders
import com.sonothamin.meowlaundry.ui.navigation.MeowLaundryNavHost
import com.sonothamin.meowlaundry.ui.theme.MeowLaundryTheme
import com.sonothamin.meowlaundry.ui.theme.UiFont

class MainActivity : ComponentActivity() {

    /** Ticket to open, set when the app is launched from a reminder notification. */
    private var openTicketId by mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTicketId = ticketIdFrom(intent)
    }

    private fun ticketIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(Reminders.EXTRA_TICKET_ID, -1L)?.takeIf { it > 0 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MeowLaundryApp
        openTicketId = ticketIdFrom(intent)

        setContent {
            val themeMode by app.appPreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            val storedFont by app.appPreferences.uiFont.collectAsStateWithLifecycle(initialValue = null)
            val context = LocalContext.current
            val uiFont = remember(storedFont) { UiFont.resolve(context, storedFont) }

            MeowLaundryTheme(themeMode = themeMode, uiFont = uiFont) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MeowLaundryNavHost(app = app, openTicketId = openTicketId, onTicketOpened = {
                        openTicketId = null
                        intent?.removeExtra(Reminders.EXTRA_TICKET_ID)
                    })
                }
            }
        }
    }
}
