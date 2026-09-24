package com.sonothamin.meowlaundry.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonothamin.meowlaundry.data.AppPreferences
import com.sonothamin.meowlaundry.data.PrintPreferences
import com.sonothamin.meowlaundry.data.PrintServerSettings
import com.sonothamin.meowlaundry.data.ThemeMode
import com.sonothamin.meowlaundry.data.backup.BackupManager
import com.sonothamin.meowlaundry.print.MeowSpoolClient
import com.sonothamin.meowlaundry.print.MeowSpoolResult
import com.sonothamin.meowlaundry.ui.theme.UiFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val connectionStatus: String? = null,
    val isTestingConnection: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val printPreferences: PrintPreferences,
    private val backupManager: BackupManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val printSettings: StateFlow<PrintServerSettings> = printPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintServerSettings())

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Raw stored font name (null = never chosen); the screen resolves it against what's available. */
    val uiFontName: StateFlow<String?> = appPreferences.uiFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUiFont(font: UiFont) {
        viewModelScope.launch { appPreferences.setUiFont(font.name) }
    }

    val defaultCurrency: StateFlow<String> = appPreferences.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.sonothamin.meowlaundry.data.Currencies.deviceDefault())

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { appPreferences.setDefaultCurrency(code) }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun updateSettings(settings: PrintServerSettings) {
        viewModelScope.launch { printPreferences.update(settings) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, connectionStatus = null)
            val settings = printSettings.value
            val client = MeowSpoolClient(settings)
            val result = client.status()
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                connectionStatus = when (result) {
                    is MeowSpoolResult.Success -> "Connected · printer ${result.value.connection}" +
                        (result.value.selectedPrinter?.let { " ($it)" } ?: "")
                    is MeowSpoolResult.Failure -> "Failed: ${result.message}"
                },
            )
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { _uiState.value = _uiState.value.copy(isExporting = false, message = "Export complete") }
                .onFailure { _uiState.value = _uiState.value.copy(isExporting = false, message = "Export failed: ${it.message}") }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            runCatching { backupManager.importFrom(uri) }
                .onSuccess { _uiState.value = _uiState.value.copy(isImporting = false, message = "Import complete") }
                .onFailure { _uiState.value = _uiState.value.copy(isImporting = false, message = "Import failed: ${it.message}") }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
