package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore(name = "app_settings")

/** Which color scheme to use, independent of the system setting. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** How the closet grid is laid out. */
enum class ClosetViewMode { GRID, LIST }

/** How the closet list is ordered. Session-only (not persisted) - defaults back to Newest each launch. */
enum class ClosetSortOption { NEWEST, OLDEST, NAME_ASC, NAME_DESC, PRICE_HIGH, PRICE_LOW }

/** Small app-wide preferences that aren't specific to the print server. */
class AppPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CLOSET_VIEW_MODE = stringPreferencesKey("closet_view_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val UI_FONT = stringPreferencesKey("ui_font")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_REMINDER_DAY = longPreferencesKey("last_reminder_day")
    }

    /** Currency pre-selected for new articles. Falls back to the device's regional currency. */
    val defaultCurrency: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_CURRENCY] ?: Currencies.deviceDefault()
    }

    suspend fun setDefaultCurrency(code: String) {
        context.appDataStore.edit { it[Keys.DEFAULT_CURRENCY] = code }
    }

    /**
     * Raw name of the chosen UiFont, or null when the user never picked one. Resolve it with
     * UiFont.resolve(), which also handles unknown or no-longer-available fonts.
     */
    val uiFont: Flow<String?> = context.appDataStore.data.map { prefs -> prefs[Keys.UI_FONT] }

    suspend fun setUiFont(name: String) {
        context.appDataStore.edit { it[Keys.UI_FONT] = name }
    }

    /** Whether "laundry is due" notifications are on. */
    val remindersEnabled: Flow<Boolean> = context.appDataStore.data.map { it[Keys.REMINDERS_ENABLED] ?: true }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    /** Hour of day (0-23, local time) the daily reminder check runs. */
    val reminderHour: Flow<Int> = context.appDataStore.data.map { it[Keys.REMINDER_HOUR] ?: 9 }

    suspend fun setReminderHour(hour: Int) {
        context.appDataStore.edit { it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    /** Minute within [reminderHour] (0-59, local time) the daily reminder check runs. */
    val reminderMinute: Flow<Int> = context.appDataStore.data.map { it[Keys.REMINDER_MINUTE] ?: 0 }

    suspend fun setReminderMinute(minute: Int) {
        context.appDataStore.edit { it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59) }
    }

    /** Epoch day of the last reminder run, so a rescheduled worker never notifies twice in one day. */
    val lastReminderDay: Flow<Long> = context.appDataStore.data.map { it[Keys.LAST_REMINDER_DAY] ?: -1L }

    suspend fun setLastReminderDay(epochDay: Long) {
        context.appDataStore.edit { it[Keys.LAST_REMINDER_DAY] = epochDay }
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
