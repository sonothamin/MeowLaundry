package com.sonothamin.meowlaundry.ui.articleview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen photo viewer, like a messaging app: a black modal over the article with
 * - swipe left/right to move between photos,
 * - pinch or double-tap to zoom, drag to pan (at the image edge a swipe passes on to the pager),
 * - swipe up/down to dismiss (the picture follows the finger and the backdrop fades),
 * - tap to hide or show the close button and photo counter.
 *
 * [onDismiss] receives the photo the viewer ended on, so the caller can move its own carousel there.
 */
@Composable
internal fun PhotoViewer(paths: List<String>, startPage: Int, title: String, onDismiss: (lastPage: Int) -> Unit) {
    val pagerState = rememberPagerState(
        initialPage = startPage.coerceIn(0, paths.lastIndex),
        pageCount = { paths.size },
    )
    val scope = rememberCoroutineScope()
    var dragY by remember { mutableFloatStateOf(0f) }
    var chromeVisible by remember { mutableStateOf(true) }

    val dismissDistance = with(LocalDensity.current) { 140.dp.toPx() }
    val progress = (abs(dragY) / (dismissDistance * 2f)).coerceIn(0f, 1f)
    val close = { onDismiss(pagerState.currentPage) }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - 0.75f * progress)),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomablePhoto(
                    path = paths[page],
                    description = "$title, photo ${page + 1} of ${paths.size}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = dragY
                            val s = 1f - 0.1f * progress
                            scaleX = s
                            scaleY = s
                        },
                    onTap = { chromeVisible = !chromeVisible },
                    onVerticalDrag = { dragY += it },
                    onVerticalDragEnd = {
                        if (abs(dragY) > dismissDistance) close()
                        else scope.launch { animate(dragY, 0f) { value, _ -> dragY = value } }
                    },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible && dragY == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = close) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = if (paths.size > 1) "${pagerState.currentPage + 1} / ${paths.size}" else "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(48.dp)) // balances the close button so the counter is centered
                }
            }
        }
    }
}

/**
 * One photo with pinch/double-tap zoom and pan. Gestures are only claimed when they are ours:
 * a single finger at normal size leaves horizontal swipes to the pager, and turns a mostly
 * vertical drag into [onVerticalDrag] (swipe to dismiss).
 */
@Composable
private fun ZoomablePhoto(
    path: String,
    description: String,
    modifier: Modifier,
    onTap: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    // The gesture blocks below run for the composable's whole life, so read callbacks through these.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnVerticalDrag by rememberUpdatedState(onVerticalDrag)
    val currentOnVerticalDragEnd by rememberUpdatedState(onVerticalDragEnd)

    /** Keeps the zoomed image from being dragged past its own edges. */
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (s - 1f) * boxSize.width / 2f
        val maxY = (s - 1f) * boxSize.height / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            val fromScale = scale
                            val fromOffset = offset
                            val toScale = if (fromScale > 1.05f) 1f else DOUBLE_TAP_ZOOM
                            // Zoom in around the tapped point; zooming out returns to centre.
                            val centre = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val toOffset = if (toScale == 1f) Offset.Zero else clamp((tap - centre) * (1f - toScale), toScale)
                            animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                                scale = fromScale + (toScale - fromScale) * t
                                offset = fromOffset + (toOffset - fromOffset) * t
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var vertical: Boolean? = null // null until the drag has picked a direction
                    var travelled = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (fingers > 1 || scale > 1f) {
                            val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            val focus = event.calculateCentroid(useCurrent = false)
                                .takeIf { it.isSpecified }?.minus(centre) ?: Offset.Zero
                            // Keep the point under the fingers fixed while scaling, then apply the pan.
                            val moved = (offset - focus) * (newScale / scale) + focus + pan
                            // A single finger pushing past the image edge hands the swipe to the pager.
                            val pastEdge = fingers == 1 && abs(offset.x + pan.x) > (newScale - 1f) * size.width / 2f
                            scale = newScale
                            offset = clamp(moved, newScale)
                            if (!pastEdge) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            travelled += pan
                            if (vertical == null && travelled.getDistance() > viewConfiguration.touchSlop) {
                                vertical = abs(travelled.y) > abs(travelled.x)
                            }
                            if (vertical == true) {
                                currentOnVerticalDrag(pan.y)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (scale < 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    if (vertical == true) currentOnVerticalDragEnd()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = path,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
