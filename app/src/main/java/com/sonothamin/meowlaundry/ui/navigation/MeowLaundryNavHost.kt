package com.sonothamin.meowlaundry.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sonothamin.meowlaundry.ui.closet.ClosetScreen
import com.sonothamin.meowlaundry.ui.closet.ClosetViewModel
import com.sonothamin.meowlaundry.ui.history.HistoryScreen
import com.sonothamin.meowlaundry.ui.history.HistoryViewModel
import com.sonothamin.meowlaundry.ui.itemedit.ItemEditScreen
import com.sonothamin.meowlaundry.ui.itemedit.ItemEditViewModel
import com.sonothamin.meowlaundry.ui.laundry.LaundryScreen
import com.sonothamin.meowlaundry.ui.laundry.LaundryViewModel
import com.sonothamin.meowlaundry.ui.laundry.SendToLaundryScreen
import com.sonothamin.meowlaundry.ui.laundry.SendToLaundryViewModel
import com.sonothamin.meowlaundry.ui.laundry.TicketDetailScreen
import com.sonothamin.meowlaundry.ui.laundry.TicketDetailViewModel
import com.sonothamin.meowlaundry.ui.settings.SettingsScreen
import com.sonothamin.meowlaundry.ui.settings.SettingsViewModel

private data class TopLevelTab(val destination: Destination, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopLevelTab(Destination.Closet, "Closet", Icons.Default.Checkroom),
    TopLevelTab(Destination.Laundry, "Laundry", Icons.Default.LocalLaundryService),
    TopLevelTab(Destination.History, "History", Icons.Default.History),
    TopLevelTab(Destination.Settings, "Settings", Icons.Default.Settings),
)

@Composable
fun MeowLaundryNavHost(app: MeowLaundryApp) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Closet.route,
            modifier = androidx.compose.ui.Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Destination.Closet.route) {
                val vm: ClosetViewModel = viewModel(factory = LambdaViewModelFactory { ClosetViewModel(app.repository) })
                ClosetScreen(
                    viewModel = vm,
                    onAddItem = { navController.navigate(Destination.ItemEditNew.route) },
                    onOpenItem = { id -> navController.navigate(Destination.ItemEdit.route(id)) },
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
                val vm: LaundryViewModel = viewModel(factory = LambdaViewModelFactory { LaundryViewModel(app.repository) })
                LaundryScreen(
                    viewModel = vm,
                    onSendNew = { navController.navigate(Destination.SendToLaundry.route) },
                    onOpenTicket = { id -> navController.navigate(Destination.TicketDetail.route(id)) },
                )
            }

            composable(Destination.SendToLaundry.route) {
                val vm: SendToLaundryViewModel = viewModel(
                    factory = LambdaViewModelFactory { SendToLaundryViewModel(app.repository) },
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
                        TicketDetailViewModel(app.repository, app.printPreferences, ticketId)
                    },
                )
                TicketDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }

            composable(Destination.History.route) {
                val vm: HistoryViewModel = viewModel(factory = LambdaViewModelFactory { HistoryViewModel(app.repository) })
                HistoryScreen(viewModel = vm, onOpenTicket = { id -> navController.navigate(Destination.TicketDetail.route(id)) })
            }

            composable(Destination.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = LambdaViewModelFactory { SettingsViewModel(app.printPreferences, app.backupManager) },
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
