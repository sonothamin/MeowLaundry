package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "print_settings")

/** Everything needed to reach a MeowSpool print server, plus a couple of label defaults. */
data class PrintServerSettings(
    val host: String = "",
    val port: Int = 8631,
    val token: String = "",
    val darkness: Int = 70,
    val dither: String = "sharp",
)

class PrintPreferences(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val TOKEN = stringPreferencesKey("token")
        val DARKNESS = intPreferencesKey("darkness")
        val DITHER = stringPreferencesKey("dither")
    }

    val settings: Flow<PrintServerSettings> = context.dataStore.data.map { prefs ->
        PrintServerSettings(
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: 8631,
            token = prefs[Keys.TOKEN] ?: "",
            darkness = prefs[Keys.DARKNESS] ?: 70,
            dither = prefs[Keys.DITHER] ?: "sharp",
        )
    }

    suspend fun update(settings: PrintServerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = settings.host
            prefs[Keys.PORT] = settings.port
            prefs[Keys.TOKEN] = settings.token
            prefs[Keys.DARKNESS] = settings.darkness
            prefs[Keys.DITHER] = settings.dither
        }
    }
}
