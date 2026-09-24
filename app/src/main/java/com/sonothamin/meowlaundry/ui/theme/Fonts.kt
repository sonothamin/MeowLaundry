package com.sonothamin.meowlaundry.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sonothamin.meowlaundry.R

// Inter and Google Sans Flex are bundled as regular resources (both OFL-licensed).
private val InterFamily = FontFamily(
    Font(R.font.inter_regular),
    Font(R.font.inter_medium, FontWeight.Medium),
)
private val GoogleSansFamily = FontFamily(
    Font(R.font.gsf_regular),
    Font(R.font.gsf_medium, FontWeight.Medium),
    Font(R.font.gsf_bold, FontWeight.Bold),
)

/**
 * UI typeface choices offered in Settings. Ndot and NType (Nothing) and Samsung Sans are third-party
 * faces that are fetched into assets/fonts at build time rather than committed, so they're only
 * offered when [isAvailable] finds the file. Ndot/NType are display faces: when picked they dress
 * titles and headings only, and body text stays on Inter for legibility.
 */
enum class UiFont(val label: String, val titlesOnly: Boolean, internal val assetPath: String? = null) {
    NDOT("Ndot", true, "fonts/ndot.otf"),
    NTYPE("Ntype", true, "fonts/ntype.otf"),
    INTER("Inter", false),
    GOOGLE_SANS("Google Sans", false),
    SAMSUNG_SANS("Samsung Sans", false, "fonts/samsungsans.ttf"),
    DEFAULT("System default", false);

    /** Always true except for the fetched fonts, which need their asset present. */
    fun isAvailable(context: Context): Boolean = assetPath == null || assetFamily(context, assetPath) != null

    companion object {
        /**
         * The font to actually use for a stored choice. A missing/unknown/unavailable choice falls back to
         * Samsung Sans on Samsung hardware (when it was fetched), and the system default elsewhere.
         */
        fun resolve(context: Context, stored: String?): UiFont {
            values().firstOrNull { it.name == stored && it.isAvailable(context) }?.let { return it }
            return if (Build.MANUFACTURER.equals("samsung", ignoreCase = true) && SAMSUNG_SANS.isAvailable(context)) {
                SAMSUNG_SANS
            } else {
                DEFAULT
            }
        }
    }
}

private fun assetFamily(context: Context, path: String): FontFamily? = try {
    context.assets.open(path).close() // throws if the fetch never produced this file
    FontFamily(Typeface.createFromAsset(context.assets, path))
} catch (e: Exception) {
    null
}

/** The family a font draws with, or null for the system default. */
fun fontFamilyFor(context: Context, font: UiFont): FontFamily? = when (font) {
    UiFont.DEFAULT -> null
    UiFont.INTER -> InterFamily
    UiFont.GOOGLE_SANS -> GoogleSansFamily
    UiFont.NDOT, UiFont.NTYPE, UiFont.SAMSUNG_SANS -> font.assetPath?.let { assetFamily(context, it) }
}

/** [AppTypography] re-dressed in [font]: display faces get titles only, everything else gets every role. */
fun typographyFor(context: Context, font: UiFont): Typography {
    val heading = fontFamilyFor(context, font) ?: return AppTypography
    val body = if (font.titlesOnly) InterFamily else heading
    val base = AppTypography
    fun TextStyle.h() = copy(fontFamily = heading)
    fun TextStyle.b() = copy(fontFamily = body)
    return Typography(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(), headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.h(), titleMedium = base.titleMedium.h(), titleSmall = base.titleSmall.h(),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b(), labelMedium = base.labelMedium.b(), labelSmall = base.labelSmall.b(),
    )
}
