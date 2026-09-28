package com.sonothamin.meowlaundry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketRulesTest {

    private fun row(quantity: Int, returned: Int = 0, lost: Int = 0, at: Long? = null) = LaundryTicketItem(
        ticketId = 1, clothingItemId = 1, quantity = quantity,
        returnedQuantity = returned, lostQuantity = lost, returnedAt = at,
    )

    @Test fun unitsToSend_isAtLeastOneAndAtMostWhatTheClosetHolds() {
        assertEquals(1, unitsToSend(0, 5))
        assertEquals(5, unitsToSend(9, 5))
        assertEquals(1, unitsToSend(3, 0))
    }

    @Test fun emptyTicketIsSentNeverReceived() {
        assertEquals(TicketStatus.SENT, ticketStatusFor(emptyList()))
    }

    @Test fun statusFollowsHowMuchIsAccountedFor() {
        assertEquals(TicketStatus.SENT, ticketStatusFor(listOf(row(2))))
        assertEquals(TicketStatus.PARTIALLY_RECEIVED, ticketStatusFor(listOf(row(2, returned = 1))))
        assertEquals(TicketStatus.PARTIALLY_RECEIVED, ticketStatusFor(listOf(row(1, returned = 1), row(1))))
        assertEquals(TicketStatus.RECEIVED, ticketStatusFor(listOf(row(2, returned = 1, lost = 1), row(1, returned = 1))))
    }

    @Test fun resolvingStampsReturnTimeOnlyWhenTheReturnedCountChanges() {
        val first = row(3).resolvedTo(Resolution(2, 0), now = 100)
        assertEquals(100L, first.returnedAt)
        assertFalse(first.returned) // one unit still out

        val second = first.resolvedTo(Resolution(2, 1), now = 200)
        assertEquals(100L, second.returnedAt) // returned count unchanged, keep the stamp
        assertTrue(second.returned && second.lost) // a mixed, fully accounted row sets both flags

        assertEquals(300L, second.resolvedTo(Resolution(3, 0), now = 300).returnedAt)
    }

    @Test fun resolvingToNothingUndoesTheAnswer() {
        val undone = row(3, returned = 3, at = 100).resolvedTo(Resolution(0, 0), now = 400)
        assertNull(undone.returnedAt)
        assertFalse(undone.returned || undone.lost)
        assertEquals(3, undone.pendingQuantity)
    }

    @Test fun answersAreClampedToTheUnitsOnTheRow() {
        val clamped = row(2).resolvedTo(Resolution(5, 5), now = 1)
        assertEquals(2, clamped.returnedQuantity)
        assertEquals(0, clamped.lostQuantity)
    }
}
