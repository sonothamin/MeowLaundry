package com.sonothamin.meowlaundry.ui.articleview

import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.ClothingStatus
import com.sonothamin.meowlaundry.data.ItemCareEvent
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.ui.components.archiveReasonLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Care facts derived from a garment's laundry trips. Dry cleaning counts as washing. */
data class CareSummary(
    val lastWashedAt: Long?,
    val lastPressedAt: Long?,
    val timesSent: Int,
    /** The ticket the garment is currently out on, if any. */
    val activeTicketId: Long?,
)

private val WASH_SERVICES = setOf(ServiceType.WASH, ServiceType.WASH_AND_PRESS, ServiceType.DRY_CLEAN)
private val PRESS_SERVICES = setOf(ServiceType.PRESS, ServiceType.WASH_AND_PRESS)

/** When a trip actually finished; only trips where the garment came back count as care. */
private fun ItemCareEvent.completedAt(): Long? = if (returned) (returnedAt ?: sentAt) else null

fun summarizeCare(events: List<ItemCareEvent>): CareSummary = CareSummary(
    lastWashedAt = events.filter { it.serviceType in WASH_SERVICES }.mapNotNull { it.completedAt() }.maxOrNull(),
    lastPressedAt = events.filter { it.serviceType in PRESS_SERVICES }.mapNotNull { it.completedAt() }.maxOrNull(),
    timesSent = events.size,
    activeTicketId = events.firstOrNull { !it.returned && !it.lost }?.ticketId,
)

enum class ActivityKind { ADDED, WASH, PRESS, WASH_AND_PRESS, DRY_CLEAN, OUT_FOR_CARE, LOST, ARCHIVED }

data class ActivityEntry(
    val at: Long,
    val kind: ActivityKind,
    val title: String,
    val detail: String?,
    /** Set for laundry trips so the row can open the ticket. */
    val ticketId: Long? = null,
)

/** Care history plus lifecycle moments (added, archived), newest first. */
fun buildActivity(item: ClothingItem, events: List<ItemCareEvent>): List<ActivityEntry> {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    fun fmt(ts: Long) = date.format(Date(ts))

    val entries = mutableListOf<ActivityEntry>()

    events.forEach { e ->
        val extras = listOfNotNull(e.providerName?.takeIf { it.isNotBlank() }, "Ticket #${e.ticketId}")
        val entry = when {
            e.lost -> ActivityEntry(
                at = e.sentAt,
                kind = ActivityKind.LOST,
                title = "Lost at the laundry",
                detail = (listOf("Sent ${fmt(e.sentAt)}") + extras).joinToString(" · "),
                ticketId = e.ticketId,
            )
            e.returned -> {
                val at = e.returnedAt ?: e.sentAt
                val (kind, title) = when (e.serviceType) {
                    ServiceType.WASH -> ActivityKind.WASH to "Washed"
                    ServiceType.PRESS -> ActivityKind.PRESS to "Pressed"
                    ServiceType.WASH_AND_PRESS -> ActivityKind.WASH_AND_PRESS to "Washed & pressed"
                    ServiceType.DRY_CLEAN -> ActivityKind.DRY_CLEAN to "Dry cleaned"
                }
                ActivityEntry(at, kind, title, (listOf("Back ${fmt(at)}") + extras).joinToString(" · "), e.ticketId)
            }
            else -> {
                val doing = when (e.serviceType) {
                    ServiceType.WASH -> "washing"
                    ServiceType.PRESS -> "pressing"
                    ServiceType.WASH_AND_PRESS -> "washing & pressing"
                    ServiceType.DRY_CLEAN -> "dry cleaning"
                }
                ActivityEntry(
                    at = e.sentAt,
                    kind = ActivityKind.OUT_FOR_CARE,
                    title = "Out for $doing",
                    detail = (listOf("Sent ${fmt(e.sentAt)}") + extras).joinToString(" · "),
                    ticketId = e.ticketId,
                )
            }
        }
        entries += entry
    }

    if (item.status == ClothingStatus.ARCHIVED && item.archivedAt != null) {
        val reason = item.archiveReason?.let { archiveReasonLabel(it) }
        entries += ActivityEntry(
            at = item.archivedAt,
            kind = ActivityKind.ARCHIVED,
            title = if (reason != null) "Archived · $reason" else "Archived",
            detail = listOfNotNull(fmt(item.archivedAt), item.archiveNotes?.takeIf { it.isNotBlank() }).joinToString(" · "),
        )
    }

    entries += ActivityEntry(item.createdAt, ActivityKind.ADDED, "Added to closet", fmt(item.createdAt))

    return entries.sortedByDescending { it.at }
}
