package com.sonothamin.meowlaundry.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.sonothamin.meowlaundry.MeowLaundryApp
import com.sonothamin.meowlaundry.ui.LambdaViewModelFactory
import com.sonothamin.meowlaundry.ui.archive.ArchiveScreen
import com.sonothamin.meowlaundry.ui.archive.ArchiveViewModel
import com.sonothamin.meowlaundry.ui.articleview.ArticleViewModel
import com.sonothamin.meowlaundry.ui.articleview.ArticleViewScreen
import com.sonothamin.meowlaundry.ui.closet.ClosetScreen
import com.sonothamin.meowlaundry.ui.closet.ClosetViewModel
import com.sonothamin.meowlaundry.ui.itemedit.ItemEditScreen
import com.sonothamin.meowlaundry.ui.itemedit.ItemEditViewModel
import com.sonothamin.meowlaundry.ui.laundry.EditTicketScreen
import com.sonothamin.meowlaundry.ui.laundry.EditTicketViewModel
import com.sonothamin.meowlaundry.ui.laundry.LaundryScreen
import com.sonothamin.meowlaundry.ui.laundry.LaundryViewModel
import com.sonothamin.meowlaundry.ui.laundry.SendToLaundryScreen
import com.sonothamin.meowlaundry.ui.laundry.SendToLaundryViewModel
import com.sonothamin.meowlaundry.ui.laundry.TicketDetailScreen
import com.sonothamin.meowlaundry.ui.laundry.TicketDetailViewModel
import com.sonothamin.meowlaundry.ui.onboarding.OnboardingScreen
import com.sonothamin.meowlaundry.ui.settings.SettingsScreen
import com.sonothamin.meowlaundry.ui.theme.Spacing
import com.sonothamin.meowlaundry.ui.stats.StatsScreen
import com.sonothamin.meowlaundry.ui.stats.StatsViewModel
import com.sonothamin.meowlaundry.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

/** savedStateHandle keys used to hand a result back to the screen underneath. */
private const val KEY_ORDER_CREATED = "laundry_order_created"
private const val KEY_FLASH_MESSAGE = "flash_message"
private const val KEY_FLASH_UNDO_TICKET = "flash_undo_ticket_id"

private const val FADE_IN_MS = 320
private const val FADE_OUT_MS = 200

private data class TopLevelTab(val destination: Destination, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(Destination.Closet, "Closet", Icons.Default.Checkroom),
    TopLevelTab(Destination.Laundry, "Laundry", Icons.Default.LocalLaundryService),
    TopLevelTab(Destination.Archive, "Archive", Icons.Default.Inventory2),
    TopLevelTab(Destination.Stats, "Stats", Icons.Default.Insights),
    TopLevelTab(Destination.Settings, "Settings", Icons.Default.Settings),
)

@Composable
fun MeowLaundryNavHost(
    app: MeowLaundryApp,
    /** Ticket to jump to (from a reminder notification); [onTicketOpened] is called once it's been handled. */
    openTicketId: Long? = null,
    onTicketOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // null while we haven't read the preference yet - avoids a flash of the wrong start screen.
    val onboardingComplete by app.appPreferences.onboardingComplete.collectAsStateWithLifecycle(initialValue = null)
    if (onboardingComplete == null) return // still loading the preference; render nothing for one frame rather than guess
    // Decide the start destination ONCE. If it followed the preference live, finishing onboarding
    // would swap the start destination, rebuild the whole graph and hard-cut to the main menu
    // instead of running the navigation transition.
    val startDestination = remember {
        if (onboardingComplete == true) Destination.Closet.route else Destination.Onboarding.route
    }
    var finishingOnboarding by remember { mutableStateOf(false) }

    // Opened from a reminder: go straight to that ticket (back returns to the closet).
    LaunchedEffect(openTicketId) {
        val id = openTicketId ?: return@LaunchedEffect
        if (startDestination != Destination.Onboarding.route) {
            navController.navigate(Destination.TicketDetail.route(id)) { launchSingleTop = true }
        }
        onTicketOpened()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The drawer only makes sense on a tab's own root screen: on onboarding there's nothing to
    // navigate to yet, and on a sub-screen (article, ticket...) swiping it open would fight with
    // that screen's own back gesture.
    val isTopLevelRoute = tabs.any { it.destination.route == currentRoute }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isTopLevelRoute,
        drawerContent = {
            ModalDrawerSheet {
                NavDrawerContent(
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        scope.launch { drawerState.close() }
                        // Always land on the tab's main screen, never on whatever sub-page was last
                        // open inside it. Closet is the root of the back stack once onboarding is
                        // done, so pop everything above it, then open the tab fresh.
                        navController.navigate(tab.destination.route) {
                            popUpTo(Destination.Closet.route) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(FADE_IN_MS)) },
            exitTransition = { fadeOut(tween(FADE_OUT_MS)) },
            popEnterTransition = { fadeIn(tween(FADE_IN_MS)) },
            popExitTransition = { fadeOut(tween(FADE_OUT_MS)) },
        ) {
            composable(Destination.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        if (finishingOnboarding) return@OnboardingScreen // Skip/Next double tap
                        finishingOnboarding = true
                        scope.launch {
                            app.appPreferences.setOnboardingComplete(true)
                            navController.navigate(Destination.Closet.route) {
                                popUpTo(Destination.Onboarding.route) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(Destination.Closet.route) { entry ->
                val vm: ClosetViewModel = viewModel(
                    factory = LambdaViewModelFactory { ClosetViewModel(app.repository, app.appPreferences) },
                )
                // The send-to-laundry screen reports back when an order was actually created (not
                // when the user merely backed out), so the multi-selection is dismissed only then.
                val orderCreated by entry.savedStateHandle
                    .getStateFlow(KEY_ORDER_CREATED, false)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(orderCreated) {
                    if (orderCreated) {
                        vm.clearSelection()
                        entry.savedStateHandle[KEY_ORDER_CREATED] = false
                    }
                }
                ClosetScreen(
                    viewModel = vm,
                    onAddItem = { navController.navigate(Destination.ItemEditNew.route) },
                    onOpenItem = { id -> navController.navigate(Destination.ArticleView.route(id)) },
                    onSendSelectedToLaundry = { ids -> navController.navigate(Destination.SendToLaundry.route(ids)) },
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            }

            composable(
                route = Destination.ArticleView.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
                val vm: ArticleViewModel = viewModel(
                    factory = LambdaViewModelFactory { ArticleViewModel(app.repository, itemId) },
                )
                ArticleViewScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Destination.ItemEdit.route(id)) },
                    onSendToLaundry = { id -> navController.navigate(Destination.SendToLaundry.route(listOf(id))) },
                    onOpenTicket = { id -> navController.navigate(Destination.TicketDetail.route(id)) },
                )
            }

            composable(Destination.ItemEditNew.route) {
                val vm: ItemEditViewModel = viewModel(
                    factory = LambdaViewModelFactory { ItemEditViewModel(app.repository, app.photoStore, app.appPreferences, null) },
                )
                ItemEditScreen(viewModel = vm, photoStore = app.photoStore, onDone = { navController.popBackStack() })
            }

            composable(
                route = Destination.ItemEdit.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId")
                val vm: ItemEditViewModel = viewModel(
                    factory = LambdaViewModelFactory { ItemEditViewModel(app.repository, app.photoStore, app.appPreferences, itemId) },
                )
                ItemEditScreen(viewModel = vm, photoStore = app.photoStore, onDone = { navController.popBackStack() })
            }

            composable(Destination.Laundry.route) { entry ->
                val vm: LaundryViewModel = viewModel(
                    factory = LambdaViewModelFactory { LaundryViewModel(app.repository, app.printDispatcher) },
                )
                val flash by entry.savedStateHandle
                    .getStateFlow<String?>(KEY_FLASH_MESSAGE, null)
                    .collectAsStateWithLifecycle()
                val undoTicketId by entry.savedStateHandle
                    .getStateFlow<Long?>(KEY_FLASH_UNDO_TICKET, null)
                    .collectAsStateWithLifecycle()
                LaundryScreen(
                    flashMessage = flash,
                    flashUndoTicketId = undoTicketId,
                    onFlashShown = {
                        entry.savedStateHandle[KEY_FLASH_MESSAGE] = null
                        entry.savedStateHandle[KEY_FLASH_UNDO_TICKET] = null
                    },
                    viewModel = vm,
                    onSendNew = { navController.navigate(Destination.SendToLaundry.route()) },
                    onOpenTicket = { id -> navController.navigate(Destination.TicketDetail.route(id)) },
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            }

            composable(
                route = Destination.SendToLaundry.route,
                arguments = listOf(navArgument("preselected") { type = NavType.StringType; defaultValue = "" }),
            ) { backStackEntry ->
                val preselected = backStackEntry.arguments?.getString("preselected")
                    ?.split(",")
                    ?.mapNotNull { it.toLongOrNull() }
                    ?.toSet()
                    ?: emptySet()
                val vm: SendToLaundryViewModel = viewModel(
                    factory = LambdaViewModelFactory { SendToLaundryViewModel(app.repository, preselected) },
                )
                SendToLaundryScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSent = { ticketId ->
                        // Tell the screen underneath (Closet/Laundry) an order went through so it can
                        // drop any multi-selection, then swap this screen for the new ticket in one
                        // navigation step (no pop-then-push flicker).
                        navController.previousBackStackEntry?.savedStateHandle?.set(KEY_ORDER_CREATED, true)
                        navController.navigate(Destination.TicketDetail.route(ticketId)) {
                            popUpTo(Destination.SendToLaundry.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Destination.TicketDetail.route,
                arguments = listOf(navArgument("ticketId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val ticketId = backStackEntry.arguments?.getLong("ticketId") ?: return@composable
                val vm: TicketDetailViewModel = viewModel(
                    factory = LambdaViewModelFactory {
                        TicketDetailViewModel(app.repository, app.printDispatcher, ticketId)
                    },
                )
                TicketDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Destination.EditTicket.route(id)) },
                    // A closed ticket has nothing left to do, so leave for the previous page and
                    // confirm there with a snackbar rather than stranding the user on a dead screen.
                    onClosed = {
                        navController.previousBackStackEntry?.savedStateHandle?.let {
                            it[KEY_FLASH_UNDO_TICKET] = ticketId // set first so the message never shows without it
                            it[KEY_FLASH_MESSAGE] = "Ticket #$ticketId closed"
                        }
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Destination.EditTicket.route,
                arguments = listOf(navArgument("ticketId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val ticketId = backStackEntry.arguments?.getLong("ticketId") ?: return@composable
                val vm: EditTicketViewModel = viewModel(
                    factory = LambdaViewModelFactory { EditTicketViewModel(app.repository, ticketId) },
                )
                EditTicketScreen(viewModel = vm, onDone = { navController.popBackStack() })
            }

            composable(Destination.Archive.route) {
                val vm: ArchiveViewModel = viewModel(
                    factory = LambdaViewModelFactory { ArchiveViewModel(app.repository) },
                )
                ArchiveScreen(
                    viewModel = vm,
                    onOpenItem = { id -> navController.navigate(Destination.ArticleView.route(id)) },
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            }

            composable(Destination.Stats.route) {
                val vm: StatsViewModel = viewModel(
                    factory = LambdaViewModelFactory { StatsViewModel(app.repository) },
                )
                StatsScreen(
                    viewModel = vm,
                    onOpenItem = { id -> navController.navigate(Destination.ArticleView.route(id)) },
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            }

            composable(Destination.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = LambdaViewModelFactory {
                        SettingsViewModel(app.printPreferences, app.backupManager, app.appPreferences, app.applicationContext)
                    },
                )
                SettingsScreen(viewModel = vm, onMenuClick = { scope.launch { drawerState.open() } })
            }
        }
    }
}

@Composable
private fun NavDrawerContent(currentRoute: String?, onSelect: (TopLevelTab) -> Unit) {
    Column(modifier = Modifier.padding(vertical = Spacing.md)) {
        Text(
            "MeowLaundry",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        HorizontalDivider(modifier = Modifier.padding(bottom = Spacing.sm))
        tabs.forEach { tab ->
            NavigationDrawerItem(
                selected = currentRoute == tab.destination.route,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            )
        }
    }
}
