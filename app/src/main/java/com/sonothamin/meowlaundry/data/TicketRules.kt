package com.sonothamin.meowlaundry.data

/*
 * The laundry rules that need no database: what a ticket's rows say about the ticket, and how a
 * laundry's answer changes a row. Pure functions, so they can be read (and tested) on their own;
 * ClosetRepository only decides *when* to apply them and persists the result.
 */

/** How many units of an article on one ticket are back and how many are lost; the rest are still out. */
data class Resolution(val returned: Int, val lost: Int)

/** Units of an article that can go on a ticket: at least one, at most what the closet holds. */
fun unitsToSend(requested: Int, inCloset: Int): Int = requested.coerceIn(1, inCloset.coerceAtLeast(1))

/** Keeps the legacy returned/lost flags in step with the quantities: true only once a row is fully accounted for. */
fun LaundryTicketItem.synced(): LaundryTicketItem {
    val resolved = isResolved
    return copy(returned = resolved && returnedQuantity > 0, lost = resolved && lostQuantity > 0)
}

/**
 * This row after the laundry says [answer] (absolute counts, not a delta), clamped to the units
 * on the row. (0, 0) undoes an earlier answer. [LaundryTicketItem.returnedAt] is only refreshed
 * when the returned count actually changes.
 */
fun LaundryTicketItem.resolvedTo(answer: Resolution, now: Long): LaundryTicketItem {
    val back = answer.returned.coerceIn(0, quantity)
    val gone = answer.lost.coerceIn(0, quantity - back)
    val at = when {
        back == 0 -> null
        back == returnedQuantity -> returnedAt ?: now
        else -> now
    }
    return copy(returnedQuantity = back, lostQuantity = gone, returnedAt = at).synced()
}

/**
 * The status a ticket's rows imply: all accounted for -> RECEIVED, some -> PARTIALLY_RECEIVED,
 * otherwise SENT. A ticket with no rows is just SENT, never "received".
 */
fun ticketStatusFor(rows: List<LaundryTicketItem>): TicketStatus = when {
    rows.isNotEmpty() && rows.all { it.isResolved } -> TicketStatus.RECEIVED
    rows.any { it.returnedQuantity + it.lostQuantity > 0 } -> TicketStatus.PARTIALLY_RECEIVED
    else -> TicketStatus.SENT
}
