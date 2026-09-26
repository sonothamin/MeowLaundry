package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "print_settings")

/**
 * How a print job actually leaves the app:
 *  - NETWORK: talk to the MeowSpool HTTP API directly (see MeowSpoolClient).
 *  - SHARE_INTENT: hand the rendered label to the MeowSpool app itself via a share
 *    (ACTION_SEND) intent, exactly like sharing a photo to it from the gallery.
 */
enum class PrintMethod { NETWORK, SHARE_INTENT }

/** Everything needed to reach a MeowSpool print server, plus a couple of label defaults. */
data class PrintServerSettings(
    val host: String = "",
    val port: Int = 8631,
    val token: String = "",
    val darkness: Int = 70,
    val dither: String = "sharp",
    val printMethod: PrintMethod = PrintMethod.SHARE_INTENT,
)

/** What goes on the printed ticket itself, as opposed to how it reaches the printer. */
data class LabelCustomization(
    /** Masthead line at the top of the ticket. Defaults to the app's own name. */
    val headerText: String = "MeowLaundry",
    /** Line at the bottom of the ticket, e.g. a pickup reminder or a shop's own note. */
    val footerText: String = "Please keep this ticket until pickup",
    /** Whether each garment's category (Top, Bottom...) prints as a line under its name. */
    val showGarmentType: Boolean = true,
)

class PrintPreferences(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val TOKEN = stringPreferencesKey("token")
        val DARKNESS = intPreferencesKey("darkness")
        val DITHER = stringPreferencesKey("dither")
        val PRINT_METHOD = stringPreferencesKey("print_method")
        val LABEL_HEADER = stringPreferencesKey("label_header")
        val LABEL_FOOTER = stringPreferencesKey("label_footer")
        val LABEL_SHOW_GARMENT_TYPE = androidx.datastore.preferences.core.booleanPreferencesKey("label_show_garment_type")
    }

    val settings: Flow<PrintServerSettings> = context.dataStore.data.map { prefs ->
        PrintServerSettings(
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: 8631,
            token = prefs[Keys.TOKEN] ?: "",
            darkness = prefs[Keys.DARKNESS] ?: 70,
            dither = prefs[Keys.DITHER] ?: "sharp",
            printMethod = prefs[Keys.PRINT_METHOD]
                ?.let { runCatching { PrintMethod.valueOf(it) }.getOrNull() }
                ?: PrintMethod.SHARE_INTENT,
        )
    }

    suspend fun update(settings: PrintServerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = settings.host
            prefs[Keys.PORT] = settings.port
            prefs[Keys.TOKEN] = settings.token
            prefs[Keys.DARKNESS] = settings.darkness
            prefs[Keys.DITHER] = settings.dither
            prefs[Keys.PRINT_METHOD] = settings.printMethod.name
        }
    }

    val labelCustomization: Flow<LabelCustomization> = context.dataStore.data.map { prefs ->
        LabelCustomization(
            headerText = prefs[Keys.LABEL_HEADER]?.takeIf { it.isNotBlank() } ?: "MeowLaundry",
            footerText = prefs[Keys.LABEL_FOOTER] ?: "Please keep this ticket until pickup",
            showGarmentType = prefs[Keys.LABEL_SHOW_GARMENT_TYPE] ?: true,
        )
    }

    suspend fun updateLabelCustomization(customization: LabelCustomization) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LABEL_HEADER] = customization.headerText
            prefs[Keys.LABEL_FOOTER] = customization.footerText
            prefs[Keys.LABEL_SHOW_GARMENT_TYPE] = customization.showGarmentType
        }
    }
}
