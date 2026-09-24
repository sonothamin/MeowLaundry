package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore(name = "app_settings")

/** Which color scheme to use, independent of the system setting. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** How the closet grid is laid out. */
enum class ClosetViewMode { GRID, LIST }

/** Small app-wide preferences that aren't specific to the print server. */
class AppPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CLOSET_VIEW_MODE = stringPreferencesKey("closet_view_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }

    /** Currency pre-selected for new articles. Falls back to the device's regional currency. */
    val defaultCurrency: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_CURRENCY] ?: Currencies.deviceDefault()
    }

    suspend fun setDefaultCurrency(code: String) {
        context.appDataStore.edit { it[Keys.DEFAULT_CURRENCY] = code }
    }

    val themeMode: Flow<ThemeMode> = context.appDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val closetViewMode: Flow<ClosetViewMode> = context.appDataStore.data.map { prefs ->
        prefs[Keys.CLOSET_VIEW_MODE]?.let { runCatching { ClosetViewMode.valueOf(it) }.getOrNull() } ?: ClosetViewMode.GRID
    }

    suspend fun setClosetViewMode(mode: ClosetViewMode) {
        context.appDataStore.edit { it[Keys.CLOSET_VIEW_MODE] = mode.name }
    }

    val onboardingComplete: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.appDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }
}
