package com.sonothamin.meowlaundry.print

import android.graphics.Bitmap
import com.sonothamin.meowlaundry.data.PrintServerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class MeowSpoolResult<out T> {
    data class Success<T>(val value: T) : MeowSpoolResult<T>()
    data class Failure(val message: String) : MeowSpoolResult<Nothing>()
}

data class PrinterStatus(
    val connection: String,
    val selectedPrinter: String?,
    val printing: Boolean,
    val outOfPaper: Boolean,
    val coverOpen: Boolean,
)

/**
 * Thin wrapper around the MeowSpool HTTP API (see API.md). Talks to a phone on the same
 * Wi-Fi, so every call needs a generous timeout - a print job blocks until the printer
 * has physically finished.
 */
class MeowSpoolClient(private val settings: PrintServerSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseUrl() = "http://${settings.host}:${settings.port}"

    private fun Request.Builder.withAuth(): Request.Builder = apply {
        if (settings.token.isNotBlank()) {
            addHeader("Authorization", "Bearer ${settings.token}")
        }
    }

    suspend fun status(): MeowSpoolResult<PrinterStatus> = withContext(Dispatchers.IO) {
        if (settings.host.isBlank()) return@withContext MeowSpoolResult.Failure("No print server configured")
        runCatching {
            val request = Request.Builder().url("${baseUrl()}/api/status").withAuth().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext MeowSpoolResult.Failure(parseError(body, response.code))
                val json = JSONObject(body)
                MeowSpoolResult.Success(
                    PrinterStatus(
                        connection = json.optString("connection", "idle"),
                        selectedPrinter = json.optString("selected").takeIf { it.isNotBlank() && it != "null" },
                        printing = json.optBoolean("printing", false),
                        outOfPaper = json.optJSONObject("status")?.optBoolean("outOfPaper", false) ?: false,
                        coverOpen = json.optJSONObject("status")?.optBoolean("coverOpen", false) ?: false,
                    )
                )
            }
        }.getOrElse { MeowSpoolResult.Failure(it.message ?: "Network error") }
    }

    /** Prints [bitmap] as a PNG, using the darkness/dither defaults from [settings]. */
    suspend fun printBitmap(bitmap: Bitmap, copies: Int = 1): MeowSpoolResult<Int> = withContext(Dispatchers.IO) {
        if (settings.host.isBlank()) return@withContext MeowSpoolResult.Failure("No print server configured")
        runCatching {
            val bytes = ByteArrayOutputStream().apply {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
            }.toByteArray()

            val url = "${baseUrl()}/api/print?darkness=${settings.darkness}&dither=${settings.dither}&copies=$copies"
            val request = Request.Builder()
                .url(url)
                .withAuth()
                .post(bytes.toRequestBody("image/png".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext MeowSpoolResult.Failure(parseError(body, response.code))
                val json = JSONObject(body)
                MeowSpoolResult.Success(json.optInt("rows", 0))
            }
        }.getOrElse { MeowSpoolResult.Failure(it.message ?: "Network error") }
    }

    private fun parseError(body: String, code: Int): String = runCatching {
        JSONObject(body).optString("error", "HTTP $code")
    }.getOrDefault("HTTP $code")
}
