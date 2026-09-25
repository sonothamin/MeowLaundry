package com.sonothamin.meowlaundry.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import com.sonothamin.meowlaundry.ui.components.rememberNotificationPermissionRequester

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** True only for the final "ask for notification permission" step. */
    val isNotificationStep: Boolean = false,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Checkroom,
        title = "Meet your closet",
        body = "Snap a photo of each garment and MeowLaundry keeps track of what's clean, " +
            "what's out, and what it's worth.",
    ),
    OnboardingPage(
        icon = Icons.Default.LocalLaundryService,
        title = "Send batches to the laundry",
        body = "Pick garments, choose a service, and MeowLaundry remembers exactly what went " +
            "out so nothing gets lost in the wash.",
    ),
    OnboardingPage(
        icon = Icons.Default.Print,
        title = "Print a ticket with MeowSpool",
        body = "Connect a MeowSpool printer over Wi-Fi, or just share the label straight to " +
            "the MeowSpool app - your call, in Settings.",
    ),
    OnboardingPage(
        icon = Icons.Default.NotificationsActive,
        title = "Never miss a pickup",
        body = "Set a due date on a ticket and MeowLaundry can remind you before it's due. " +
            "Turn this on now, or skip it and set it up later in Settings.",
        isNotificationStep = true,
    ),
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val onLastPage = pagerState.currentPage == pages.lastIndex
    val onNotificationStep = pages[pagerState.currentPage].isNotificationStep

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinished, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Skip")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            PageIndicator(pageCount = pages.size, currentPage = pagerState.currentPage)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    when {
                        onNotificationStep -> {
                            requestNotificationPermission()
                            onFinished()
                        }
                        onLastPage -> onFinished()
                        else -> scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            ) {
                Text(
                    when {
                        onNotificationStep -> "Enable notifications"
                        onLastPage -> "Get started"
                        else -> "Next"
                    }
                )
                if (!onNotificationStep) {
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            if (onNotificationStep) {
                TextButton(onClick = onFinished) { Text("Not now") }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.55f)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f),
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateFloatAsState(targetValue = if (selected) 24f else 8f, animationSpec = tween(250), label = "dot")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .size(width.dp, 8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}
