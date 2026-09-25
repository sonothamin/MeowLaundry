package com.sonothamin.meowlaundry.ui.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")

    data object Closet : Destination("closet")
    data object Laundry : Destination("laundry")
    data object Archive : Destination("archive")
    data object Stats : Destination("stats")
    data object Settings : Destination("settings")

    data object ItemEditNew : Destination("item/new")
    data object ItemEdit : Destination("item/{itemId}/edit") {
        fun route(itemId: Long) = "item/$itemId/edit"
    }
    data object ArticleView : Destination("item/{itemId}") {
        fun route(itemId: Long) = "item/$itemId"
    }

    /** [preselectedIds] lets the closet's multiselect "send to laundry" action pre-fill the picker. */
    data object SendToLaundry : Destination("laundry/send?preselected={preselected}") {
        fun route(preselectedIds: List<Long> = emptyList()) =
            "laundry/send?preselected=${preselectedIds.joinToString(",")}"
    }
    data object TicketDetail : Destination("laundry/ticket/{ticketId}") {
        fun route(ticketId: Long) = "laundry/ticket/$ticketId"
    }
    data object EditTicket : Destination("laundry/ticket/{ticketId}/edit") {
        fun route(ticketId: Long) = "laundry/ticket/$ticketId/edit"
    }
}

/** The top-level destinations shown in the bottom navigation bar. */
val topLevelDestinations = listOf(
    Destination.Closet,
    Destination.Laundry,
    Destination.Archive,
    Destination.Stats,
    Destination.Settings,
)
