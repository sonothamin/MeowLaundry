package com.sonothamin.meowlaundry.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import com.sonothamin.meowlaundry.ui.components.EmptyState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonothamin.meowlaundry.ui.theme.Spacing

/**
 * Material 3 window width classes (compact < 600dp, medium < 840dp, expanded < 1200dp, large from
 * 1200dp). Measured on the window, not the physical screen, so split-screen and freeform windows
 * get the layout that actually fits them.
 */
enum class WidthClass { Compact, Medium, Expanded, Large }

@Composable
fun rememberWidthClass(): WidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> WidthClass.Compact
        widthDp < 840 -> WidthClass.Medium
        widthDp < 1200 -> WidthClass.Expanded
        else -> WidthClass.Large
    }
}

/** Wide enough to show a list and the thing it opens side by side. */
val WidthClass.isTwoPane: Boolean get() = this == WidthClass.Expanded || this == WidthClass.Large

/** Wide enough that navigation lives in a rail / permanent drawer instead of a modal drawer. */
val WidthClass.hasPersistentNav: Boolean get() = this != WidthClass.Compact

object ContentWidth {
    /** Forms, settings pages and other reading-width content. */
    val Form = 640.dp
    /** Single-column lists of cards. */
    val List = 720.dp
}

/**
 * True for screens hosted inside the detail pane of a list-detail layout. They are not a separate
 * destination, so they drop their back arrow (there is nothing to go back to).
 */
val LocalEmbeddedInPane = compositionLocalOf { false }

/** Top-app-bar back arrow that disappears when the screen is embedded in a detail pane. */
@Composable
fun BackNavigationIcon(onBack: () -> Unit) {
    if (LocalEmbeddedInPane.current) return
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/**
 * Caps the width of a screen's main content and centers it horizontally. Put it right after the
 * Scaffold padding, e.g. `Modifier.fillMaxSize().padding(padding).contentMaxWidth()`. Below [max]
 * it changes nothing, so phones and narrow panes are unaffected.
 */
fun Modifier.contentMaxWidth(max: Dp = ContentWidth.Form): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** Centers [content] and caps its width, so forms don't stretch edge to edge on a tablet. */
@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ContentWidth.Form,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}

/**
 * Expressive list-detail layout: two rounded panes floating on a tinted surface. The detail pane
 * is marked [LocalEmbeddedInPane] so the screens inside it behave as panes, not destinations.
 */
@Composable
fun ListDetailPane(
    modifier: Modifier = Modifier,
    listPaneWidth: Dp = 400.dp,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val paneShape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // Pad (and consume) the system bar insets once for both panes, so the Scaffolds inside
            // them don't each add their own status-bar gap on top of the rounded corners.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                ),
            )
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(modifier = Modifier.width(listPaneWidth).fillMaxHeight().clip(paneShape)) { list() }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(paneShape)) {
            CompositionLocalProvider(LocalEmbeddedInPane provides true) { detail() }
        }
    }
}

/** What the detail pane shows until something is picked in the list. */
@Composable
fun DetailPlaceholder(icon: ImageVector, title: String, subtitle: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        EmptyState(icon = icon, title = title, subtitle = subtitle)
    }
}
