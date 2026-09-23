package com.sonothamin.meowlaundry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonothamin.meowlaundry.data.ThemeMode
import com.sonothamin.meowlaundry.ui.navigation.MeowLaundryNavHost
import com.sonothamin.meowlaundry.ui.theme.MeowLaundryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MeowLaundryApp

        setContent {
            val themeMode by app.appPreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            MeowLaundryTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MeowLaundryNavHost(app = app)
                }
            }
        }
    }
}
