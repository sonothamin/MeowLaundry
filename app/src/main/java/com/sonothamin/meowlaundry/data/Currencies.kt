package com.sonothamin.meowlaundry.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Currency helpers: the picker list, symbols and locale-aware price formatting. */
object Currencies {

    /** Offered in every currency picker. Any other code already stored on an item is added on top. */
    val presets: List<String> = listOf(
        "USD", "EUR", "GBP", "JPY", "CNY", "INR", "BDT", "PKR", "LKR", "NPR",
        "CAD", "AUD", "NZD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF",
        "RUB", "TRY", "AED", "SAR", "QAR", "KWD", "EGP", "ZAR", "NGN", "KES",
        "SGD", "HKD", "MYR", "IDR", "THB", "PHP", "VND", "KRW", "BRL", "MXN",
    )

    private fun currencyOrNull(code: String): Currency? = runCatching { Currency.getInstance(code) }.getOrNull()

    /** The regional currency of the device, or USD if it can't be determined. */
    fun deviceDefault(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"

    fun symbol(code: String): String =
        currencyOrNull(code)?.getSymbol(Locale.getDefault()) ?: code

    fun displayName(code: String): String =
        currencyOrNull(code)?.getDisplayName(Locale.getDefault()) ?: code

    /** "USD · US Dollar" */
    fun label(code: String): String = "$code · ${displayName(code)}"

    /** [presets], with [extra] codes (e.g. a currency an old item uses) guaranteed to be present. */
    fun options(vararg extra: String): List<String> =
        (extra.filter { it.isNotBlank() } + presets).distinct()

    /** "$12", "$12.50", "¥1,200" - whole amounts drop the decimals. */
    fun format(amount: Double, code: String): String {
        val currency = currencyOrNull(code)
            ?: return "${"%.2f".format(amount).removeSuffix(".00")} $code"
        val whole = amount == amount.toLong().toDouble()
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            this.currency = currency
            val digits = if (whole) 0 else currency.defaultFractionDigits.coerceAtLeast(0)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(amount)
    }
}
