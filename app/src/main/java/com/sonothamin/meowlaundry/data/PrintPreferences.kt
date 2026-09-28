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
 *  - NATIVE: hand the rendered label to Android's own print framework (PrintManager), which
 *    opens the system print dialog - any printer the OS already knows about, "Save as PDF",
 *    a cloud print service, etc. Not MeowSpool-specific.
 */
enum class PrintMethod { NETWORK, SHARE_INTENT, NATIVE }

/** The printed ticket's overall shape. */
enum class TicketFormat {
    /** An open continuous strip with dashed tear lines, like a real receipt roll. */
    RECEIPT,

    /**
     * A real text document sized for standard paper (see [PageSize]) - drawn with actual text
     * (selectable/searchable in the resulting PDF), not a rasterized bitmap image like the
     * receipt format. Meant for printing via the system dialog or sharing/saving as a PDF.
     */
    PAGE,
}

/**
 * A standard paper size, in PDF points (1/72 inch). Not user-configurable anymore - see
 * [defaultPageSize] and the print-dialog-driven sizing in PrintDispatcher/PdfPageRenderer -
 * kept only as a fallback for contexts with no print dialog to ask (the live settings preview,
 * and the "share this PDF" path when the person isn't using the system print dialog).
 */
enum class PageSize(val widthPt: Int, val heightPt: Int) {
    A4(595, 842),
    LETTER(612, 792),
}

/** A4 almost everywhere; Letter in North America and a couple of other Letter-paper countries. */
fun defaultPageSize(): PageSize {
    val letterCountries = setOf("US", "CA", "MX", "PH", "CL", "CO", "VE", "DO", "PR")
    return if (java.util.Locale.getDefault().country in letterCountries) PageSize.LETTER else PageSize.A4
}

/**
 * Page margin presets, matching the three most-used options in Microsoft Word's own margin
 * picker (its "Normal" 1in-all-round default isn't offered here - "Moderate" already covers
 * that middle ground closely enough for a two/three-option picker).
 */
enum class PageMargin(val horizontalPt: Float, val verticalPt: Float, val displayName: String) {
    NARROW(36f, 36f, "Narrow"),
    MODERATE(54f, 72f, "Moderate"),
    WIDE(144f, 72f, "Wide"),
}

/** A small, legible set of print fonts - kept deliberately short rather than exposing every family on-device. */
enum class LabelFont(val typefaceFamily: String, val displayName: String) {
    SANS("sans-serif", "Sans-serif"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace"),
    CONDENSED("sans-serif-condensed", "Condensed"),
}

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
    /** Receipt strip or full-page document - see [TicketFormat]. */
    val format: TicketFormat = TicketFormat.RECEIPT,
    /** ARGB color of the masthead band on a [TicketFormat.PAGE] document. Ignored otherwise. */
    val headerColor: Int = DEFAULT_HEADER_COLOR,
    /** Typeface used for every piece of text on the ticket, receipt or page alike. */
    val font: LabelFont = LabelFont.SANS,
    /** Page margin, [TicketFormat.PAGE] only - ignored by the bitmap formats. */
    val pageMargin: PageMargin = PageMargin.MODERATE,
)

/** The launcher icon's purple - the default PDF header band color. */
val DEFAULT_HEADER_COLOR: Int = 0xFF6750A4.toInt()

/** A curated set of header band colors offered in Settings, all dark enough for white text. */
val PDF_HEADER_COLOR_PRESETS: List<Int> = listOf(
    DEFAULT_HEADER_COLOR, // purple
    0xFF1A237E.toInt(), // navy
    0xFF00695C.toInt(), // teal
    0xFF2E7D32.toInt(), // forest green
    0xFFB71C1C.toInt(), // crimson
    0xFF4E342E.toInt(), // espresso
    0xFF37474F.toInt(), // charcoal
    0xFF212121.toInt(), // black
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
        val LABEL_FORMAT = stringPreferencesKey("label_format")
        val LABEL_HEADER_COLOR = intPreferencesKey("label_header_color")
        val LABEL_FONT = stringPreferencesKey("label_font")
        val LABEL_PAGE_MARGIN = stringPreferencesKey("label_page_margin")
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
            format = prefs[Keys.LABEL_FORMAT]
                ?.let { runCatching { TicketFormat.valueOf(it) }.getOrNull() }
                ?: TicketFormat.RECEIPT,
            headerColor = prefs[Keys.LABEL_HEADER_COLOR] ?: DEFAULT_HEADER_COLOR,
            font = prefs[Keys.LABEL_FONT]
                ?.let { runCatching { LabelFont.valueOf(it) }.getOrNull() }
                ?: LabelFont.SANS,
            pageMargin = prefs[Keys.LABEL_PAGE_MARGIN]
                ?.let { runCatching { PageMargin.valueOf(it) }.getOrNull() }
                ?: PageMargin.MODERATE,
        )
    }

    suspend fun updateLabelCustomization(customization: LabelCustomization) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LABEL_HEADER] = customization.headerText
            prefs[Keys.LABEL_FOOTER] = customization.footerText
            prefs[Keys.LABEL_SHOW_GARMENT_TYPE] = customization.showGarmentType
            prefs[Keys.LABEL_FORMAT] = customization.format.name
            prefs[Keys.LABEL_HEADER_COLOR] = customization.headerColor
            prefs[Keys.LABEL_FONT] = customization.font.name
            prefs[Keys.LABEL_PAGE_MARGIN] = customization.pageMargin.name
        }
    }
}
