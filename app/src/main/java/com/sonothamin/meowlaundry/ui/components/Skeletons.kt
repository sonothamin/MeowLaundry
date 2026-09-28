package com.sonothamin.meowlaundry.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sonothamin.meowlaundry.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

// --- Shimmer -----------------------------------------------------------------------------------

/**
 * Sweeping highlight for placeholder shapes. The animated value is only read while drawing, so
 * the animation repaints the shape but never recomposes anything.
 */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val startX = -w + progress * 2f * w
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(startX, 0f),
                end = Offset(startX + w, 0f),
            ),
        )
    }
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier = modifier.clip(shape).shimmer())
}

// --- Skeleton shapes matching the real cards ---------------------------------------------------

@Composable
fun ClothingCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), RectangleShape)
            Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.4f).height(10.dp))
            }
        }
    }
}

@Composable
fun ClothingListRowSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBox(Modifier.size(56.dp), MaterialTheme.shapes.medium)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(0.6f).height(14.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.3f).height(10.dp))
            }
            SkeletonBox(Modifier.size(32.dp), CircleShape)
        }
    }
}

@Composable
fun TicketCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SkeletonBox(Modifier.width(140.dp).height(16.dp))
                SkeletonBox(Modifier.width(64.dp).height(24.dp), CircleShape)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                repeat(3) { SkeletonBox(Modifier.size(44.dp), CircleShape) }
            }
            SkeletonBox(Modifier.fillMaxWidth().height(8.dp), CircleShape)
        }
    }
}

// --- Whole-screen skeleton lists (same geometry as the real lists so nothing jumps) ----------

@Composable
fun ClothingGridSkeleton(modifier: Modifier = Modifier, count: Int = 12) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(count) { ClothingCardSkeleton() }
    }
}

@Composable
fun ClothingListSkeleton(modifier: Modifier = Modifier, count: Int = 10) {
    LazyColumn(
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(count) { ClothingListRowSkeleton() }
    }
}

@Composable
fun TicketListSkeleton(modifier: Modifier = Modifier, count: Int = 5) {
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(count) { TicketCardSkeleton() }
    }
}

// --- List footers --------------------------------------------------------------------------------

/** Shown at the bottom while more pages are still to come. */
@Composable
fun LoadMoreFooter(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
        SkeletonBox(Modifier.width(120.dp).height(12.dp), CircleShape)
    }
}

/** Shown once the last item has been reached. */
@Composable
fun EndOfList(modifier: Modifier = Modifier, text: String = "You've reached the end") {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Default.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// --- Load-more trigger ---------------------------------------------------------------------------

@Composable
private fun NearEndEffect(
    key: Any,
    enabled: Boolean,
    buffer: Int,
    probe: () -> Pair<Int, Int>,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentProbe by rememberUpdatedState(probe)
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        // Keyed on the total too, so a freshly revealed page that still leaves us near the end
        // (tall screens, short pages) immediately asks for the next one.
        snapshotFlow {
            val (lastVisible, total) = currentProbe()
            (lastVisible >= 0 && lastVisible >= total - 1 - buffer) to total
        }
            .distinctUntilChanged()
            .filter { it.first }
            .collect { currentOnLoadMore() }
    }
}

/** Calls [onLoadMore] when the grid is scrolled to within [buffer] items of its end. */
@Composable
fun LazyGridState.LoadMoreOnNearEnd(enabled: Boolean, buffer: Int = 8, onLoadMore: () -> Unit) {
    NearEndEffect(
        key = this,
        enabled = enabled,
        buffer = buffer,
        probe = { (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to layoutInfo.totalItemsCount },
        onLoadMore = onLoadMore,
    )
}

/** Calls [onLoadMore] when the list is scrolled to within [buffer] items of its end. */
@Composable
fun LazyListState.LoadMoreOnNearEnd(enabled: Boolean, buffer: Int = 8, onLoadMore: () -> Unit) {
    NearEndEffect(
        key = this,
        enabled = enabled,
        buffer = buffer,
        probe = { (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to layoutInfo.totalItemsCount },
        onLoadMore = onLoadMore,
    )
}

// --- Photos --------------------------------------------------------------------------------------

/**
 * Garment photo with a shimmer while it decodes and a short fade-in. Give it a size via [modifier];
 * the caller keeps drawing its own background behind it.
 */
@Composable
fun ThumbImage(path: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var settled by remember(path) { mutableStateOf(false) }
    val request = remember(path) {
        ImageRequest.Builder(context).data(path).crossfade(true).build()
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { settled = true },
            onError = { settled = true },
        )
        if (!settled) Box(Modifier.fillMaxSize().shimmer())
    }
}
