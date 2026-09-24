package com.sonothamin.meowlaundry.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
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
import com.sonothamin.meowlaundry.ui.laundry.LaundryScreen
import com.sonothamin.meowlaundry.ui.laundry.LaundryViewModel
import com.sonothamin.meowlaundry.ui.laundry.SendToLaundryScreen
import com.sonothamin.meowlaundry.ui.laundry.SendToLaundryViewModel
import com.sonothamin.meowlaundry.ui.laundry.TicketDetailScreen
import com.sonothamin.meowlaundry.ui.laundry.TicketDetailViewModel
import com.sonothamin.meowlaundry.ui.onboarding.OnboardingScreen
import com.sonothamin.meowlaundry.ui.settings.SettingsScreen
import com.sonothamin.meowlaundry.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private data class TopLevelTab(val destination: Destination, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(Destination.Closet, "Closet", Icons.Default.Checkroom),
    TopLevelTab(Destination.Laundry, "Laundry", Icons.Default.LocalLaundryService),
    TopLevelTab(Destination.Archive, "Archive", Icons.Default.Inventory2),
    TopLevelTab(Destination.Settings, "Settings", Icons.Default.Settings),
)

@Composable
fun MeowLaundryNavHost(app: MeowLaundryApp) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // null while we haven't read the preference yet - avoids a flash of the wrong start screen.
    val onboardingComplete by app.appPreferences.onboardingComplete.collectAsStateWithLifecycle(initialValue = null)
    val startDestination = when (onboardingComplete) {
        null -> return // still loading the preference; render nothing for one frame rather than guess
        false -> Destination.Onboarding.route
        true -> Destination.Closet.route
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = backStackEntry?.destination?.route != Destination.Onboarding.route

    Scaffold(
        bottomBar = { if (showBottomBar) BottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
        ) {
            composable(Destination.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        scope.launch {
                            app.appPreferences.setOnboardingComplete(true)
                            navController.navigate(Destination.Closet.route) {
                                popUpTo(Destination.Onboarding.route) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(Destination.Closet.route) {
                val vm: ClosetViewModel = viewModel(
                    factory = LambdaViewModelFactory { ClosetViewModel(app.repository, app.appPreferences) },
                )
                ClosetScreen(
                    viewModel = vm,
                    onAddItem = { navController.navigate(Destination.ItemEditNew.route) },
                    onOpenItem = { id -> navController.navigate(Destination.ArticleView.route(id)) },
                    onSendSelectedToLaundry = { ids -> navController.navigate(Destination.SendToLaundry.route(ids)) },
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
                )
            }

            composable(Destination.ItemEditNew.route) {
                val vm: ItemEditViewModel = viewModel(
                    factory = LambdaViewModelFactory { ItemEditViewModel(app.repository, app.photoStore, null) },
                )
                ItemEditScreen(viewModel = vm, photoStore = app.photoStore, onDone = { navController.popBackStack() })
            }

            composable(
                route = Destination.ItemEdit.route,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId")
                val vm: ItemEditViewModel = viewModel(
                    factory = LambdaViewModelFactory { ItemEditViewModel(app.repository, app.photoStore, itemId) },
                )
                ItemEditScreen(viewModel = vm, photoStore = app.photoStore, onDone = { navController.popBackStack() })
            }

            composable(Destination.Laundry.route) {
                val vm: LaundryViewModel = viewModel(
                    factory = LambdaViewModelFactory { LaundryViewModel(app.repository, app.printDispatcher) },
                )
                LaundryScreen(
                    viewModel = vm,
                    onSendNew = { navController.navigate(Destination.SendToLaundry.route()) },
                    onOpenTicket = { id -> navController.navigate(Destination.TicketDetail.route(id)) },
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
                        navController.popBackStack()
                        navController.navigate(Destination.TicketDetail.route(ticketId))
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
                TicketDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }

            composable(Destination.Archive.route) {
                val vm: ArchiveViewModel = viewModel(
                    factory = LambdaViewModelFactory { ArchiveViewModel(app.repository) },
                )
                ArchiveScreen(viewModel = vm, onOpenItem = { id -> navController.navigate(Destination.ArticleView.route(id)) })
            }

            composable(Destination.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = LambdaViewModelFactory {
                        SettingsViewModel(app.printPreferences, app.backupManager, app.appPreferences)
                    },
                )
                SettingsScreen(viewModel = vm)
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.destination.route,
                onClick = {
                    navController.navigate(tab.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
