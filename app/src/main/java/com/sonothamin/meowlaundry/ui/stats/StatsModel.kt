package com.sonothamin.meowlaundry.ui.stats

import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.CurrencyAmount
import com.sonothamin.meowlaundry.data.DueDates
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.LaundryTicketItem
import com.sonothamin.meowlaundry.data.ServiceType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Which tickets count, by the day they were sent. */
enum class StatsPeriod(val label: String, val days: Long?) {
    LAST_30("30 days", 30),
    LAST_6_MONTHS("6 months", 183),
    ALL("All time", null),
}

data class MonthTrips(val label: String, val trips: Int, val isCurrent: Boolean)
data class GarmentTrips(val garment: ClothingItem, val trips: Int)
data class ServiceCount(val type: ServiceType, val trips: Int)

/** [name] is null for tickets with no provider set. */
data class ProviderStats(
    val name: String?,
    val trips: Int,
    val garments: Int,
    val lost: Int,
    val lostValues: List<CurrencyAmount>,
)

data class StatsData(
    val trips: Int,
    val garmentsSent: Int,
    val returned: Int,
    val lost: Int,
    /** Replacement value of what was lost, per currency (never summed across currencies). */
    val lostValues: List<CurrencyAmount>,
    /** Tickets with a due date that came back on time, out of [dueTracked] (received ones plus overdue open ones). */
    val onTime: Int,
    val dueTracked: Int,
    val avgTurnaroundDays: Double?,
    /** The last 6 months of trips, oldest first. Ignores the period filter so the chart stays put. */
    val perMonth: List<MonthTrips>,
    val topGarments: List<GarmentTrips>,
    val providers: List<ProviderStats>,
    val services: List<ServiceCount>,
) {
    val onTimePercent: Int? get() = if (dueTracked == 0) null else (onTime * 100f / dueTracked).roundToInt()
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Pure aggregation over the whole history, so it can be reused and tested without a database. */
fun computeStats(
    tickets: List<LaundryTicket>,
    ticketItems: List<LaundryTicketItem>,
    clothing: List<ClothingItem>,
    period: StatsPeriod,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): StatsData {
    val cutoff = period.days?.let { now - it * DAY_MS }
    val inRange = tickets.filter { cutoff == null || it.sentAt >= cutoff }
    val rangeIds = inRange.map { it.id }.toSet()
    val items = ticketItems.filter { it.ticketId in rangeIds }
    val itemsByTicket = items.groupBy { it.ticketId }
    val clothingById = clothing.associateBy { it.id }

    fun lostValues(list: List<LaundryTicketItem>): List<CurrencyAmount> =
        list.filter { it.lost }
            .mapNotNull { clothingById[it.clothingItemId] }
            .filter { it.price != null }
            .groupBy { it.currency }
            .map { (currency, group) -> CurrencyAmount(currency, group.sumOf { it.price ?: 0.0 }) }
            .sortedByDescending { it.total }

    // On time = back on or before the due day. Still-out tickets already past due count as late.
    val startOfToday = DueDates.startOfDay(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
    var onTime = 0
    var tracked = 0
    inRange.forEach { ticket ->
        val due = ticket.expectedReturnAt ?: return@forEach
        val received = ticket.receivedAt
        if (received != null) {
            tracked++
            if (received < due + DAY_MS) onTime++
        } else if (DueDates.isOpen(ticket) && due < startOfToday) {
            tracked++
        }
    }

    val turnarounds = inRange.mapNotNull { ticket ->
        ticket.receivedAt?.let { (it - ticket.sentAt).toDouble() / DAY_MS }
    }.filter { it >= 0 }

    val currentMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
    val tripsByMonth = tickets.groupingBy { YearMonth.from(Instant.ofEpochMilli(it.sentAt).atZone(zone)) }.eachCount()
    val perMonth = (5 downTo 0).map { back ->
        val month = currentMonth.minusMonths(back.toLong())
        MonthTrips(
            label = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            trips = tripsByMonth[month] ?: 0,
            isCurrent = month == currentMonth,
        )
    }

    val topGarments = items.groupingBy { it.clothingItemId }.eachCount().entries
        .sortedByDescending { it.value }
        .mapNotNull { entry -> clothingById[entry.key]?.let { GarmentTrips(it, entry.value) } }
        .take(5)

    val providers = inRange
        .groupBy { ticket -> ticket.providerName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } }
        .map { (_, group) ->
            val groupItems = group.flatMap { itemsByTicket[it.id].orEmpty() }
            ProviderStats(
                name = group.first().providerName?.trim()?.takeIf { it.isNotEmpty() },
                trips = group.size,
                garments = groupItems.size,
                lost = groupItems.count { it.lost },
                lostValues = lostValues(groupItems),
            )
        }
        .sortedWith(compareByDescending<ProviderStats> { it.trips }.thenByDescending { it.lost })
        .take(5)

    val services = inRange.groupingBy { it.serviceType }.eachCount()
        .map { ServiceCount(it.key, it.value) }
        .sortedByDescending { it.trips }

    return StatsData(
        trips = inRange.size,
        garmentsSent = items.size,
        returned = items.count { it.returned },
        lost = items.count { it.lost },
        lostValues = lostValues(items),
        onTime = onTime,
        dueTracked = tracked,
        avgTurnaroundDays = turnarounds.takeIf { it.isNotEmpty() }?.average(),
        perMonth = perMonth,
        topGarments = topGarments,
        providers = providers,
        services = services,
    )
}
