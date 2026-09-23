package com.sonothamin.meowlaundry.ui.navigation

sealed class Destination(val route: String) {
    data object Closet : Destination("closet")
    data object Laundry : Destination("laundry")
    data object History : Destination("history")
    data object Settings : Destination("settings")

    data object ItemEditNew : Destination("item/new")
    data object ItemEdit : Destination("item/{itemId}") {
        fun route(itemId: Long) = "item/$itemId"
    }

    data object SendToLaundry : Destination("laundry/send")
    data object TicketDetail : Destination("laundry/ticket/{ticketId}") {
        fun route(ticketId: Long) = "laundry/ticket/$ticketId"
    }
}

/** The four top-level destinations shown in the bottom navigation bar. */
val topLevelDestinations = listOf(Destination.Closet, Destination.Laundry, Destination.History, Destination.Settings)
