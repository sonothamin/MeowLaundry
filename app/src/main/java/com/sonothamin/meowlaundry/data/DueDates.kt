package com.sonothamin.meowlaundry.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class DueState { OVERDUE, TODAY, SOON, LATER }

/** How a ticket stands against its due date. [daysUntil] is negative once overdue. */
data class DueInfo(val state: DueState, val daysUntil: Long, val label: String)

/**
 * Due dates are stored in [LaundryTicket.expectedReturnAt] as the start of the due day in the
 * device's time zone, so a ticket due "Friday" stays due Friday wherever the phone is.
 */
object DueDates {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone)

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Stored value for [days] days from today. */
    fun inDays(days: Long): Long = startOfDay(today().plusDays(days))

    fun toLocalDate(stored: Long): LocalDate = Instant.ofEpochMilli(stored).atZone(zone).toLocalDate()

    /** Material date pickers speak "UTC midnight of the chosen day"; convert to the stored form. */
    fun fromPickerUtcMillis(utc: Long): Long =
        startOfDay(Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate())

    fun toPickerUtcMillis(stored: Long): Long =
        toLocalDate(stored).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun todayPickerUtcMillis(): Long = today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** "Fri, 27 Sep" */
    fun format(stored: Long): String =
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(toLocalDate(stored))

    /** A ticket still has garments out (so a due date is meaningful) until everything is accounted for. */
    fun isOpen(ticket: LaundryTicket): Boolean =
        ticket.status == TicketStatus.SENT || ticket.status == TicketStatus.PARTIALLY_RECEIVED

    /** Null when the ticket has no due date or is no longer waiting on anything. */
    fun info(ticket: LaundryTicket, today: LocalDate = today()): DueInfo? {
        val due = ticket.expectedReturnAt ?: return null
        if (!isOpen(ticket)) return null
        val days = ChronoUnit.DAYS.between(today, toLocalDate(due))
        return when {
            days < 0 -> DueInfo(DueState.OVERDUE, days, "Overdue ${-days} ${if (days == -1L) "day" else "days"}")
            days == 0L -> DueInfo(DueState.TODAY, 0, "Due today")
            days == 1L -> DueInfo(DueState.SOON, 1, "Due tomorrow")
            days == 2L -> DueInfo(DueState.SOON, 2, "Due in 2 days")
            else -> DueInfo(DueState.LATER, days, "Due in $days days")
        }
    }
}
