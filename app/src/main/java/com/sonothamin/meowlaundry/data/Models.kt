package com.sonothamin.meowlaundry.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Where a single garment currently is. */
enum class ClothingStatus {
    IN_CLOSET, AT_LAUNDRY, ARCHIVED
}

/** Why a garment was archived (taken out of active closet rotation for good). */
enum class ArchiveReason {
    DONATED, SOLD, DISCARDED, GIVEN_AWAY, LOST, OTHER
}

/** What the laundromat/laundry service is asked to do with a batch of clothes. */
enum class ServiceType {
    WASH, PRESS, WASH_AND_PRESS, DRY_CLEAN
}

/** Lifecycle of a laundry ticket (a batch of clothes sent out together). */
enum class TicketStatus {
    SENT, PARTIALLY_RECEIVED, RECEIVED, CLOSED
}

/**
 * A single garment the user owns. [imagePath] is a path under the app's private
 * files directory (see [com.sonothamin.meowlaundry.data.PhotoStore]), never a content:// URI,
 * so the picture keeps working across app restarts and backups.
 */
@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /**
     * Free-text category ("Top", "Bedding", "Kitchen linen"...), used for grouping and the closet
     * icon. Typed by hand with suggestions rather than picked from a closed list - see
     * [com.sonothamin.meowlaundry.data.Suggestions.builtInCategories] for the built-in presets and
     * [com.sonothamin.meowlaundry.data.Suggestions.categoryFor] for auto-detection from the garment
     * type. Stored exactly as typed; [com.sonothamin.meowlaundry.data.Suggestions.displayCategory]
     * normalizes older ALL-CAPS values from before this was free text.
     */
    val type: String,
    val imagePath: String? = null,
    /** Replacement price, used to value what was lost if the laundry loses this item. */
    val price: Double? = null,
    val status: ClothingStatus = ClothingStatus.IN_CLOSET,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Set together with [status] = ARCHIVED. Null otherwise. */
    val archiveReason: ArchiveReason? = null,
    val archivedAt: Long? = null,
    val archiveNotes: String? = null,
    /** Free-text brand, e.g. "Uniqlo". Feeds the generated [title] and brand suggestions. */
    val brand: String? = null,
    /** Specific kind of garment, e.g. "Oxford shirt". [type] stays the broad category used for filtering. */
    val garmentType: String? = null,
    val color: String? = null,
    /** ISO 4217 code that [price] is expressed in, e.g. "USD". */
    val currency: String = "USD",
    /** Tagged as a seasonal/winter garment - can be hidden from the everyday closet view. */
    val isWinterWear: Boolean = false,
    /**
     * How many identical units this article stands for (socks, boxers, undershirts...). Counts
     * every unit still owned, including ones out at the laundry. 1 for an ordinary garment.
     */
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    /**
     * Cache of how many units are out at the laundry right now, and how many were lost there in
     * total. Both are recomputed from the ticket rows (see ClosetRepository.recomputeCounts), so
     * the ticket rows stay the single source of truth.
     */
    @ColumnInfo(defaultValue = "0") val atLaundryQuantity: Int = 0,
    @ColumnInfo(defaultValue = "0") val lostQuantity: Int = 0,
) {
    /** Units still owned (not lost). */
    val ownedQuantity: Int get() = (quantity - lostQuantity).coerceAtLeast(0)

    /** Units sitting in the closet, i.e. available to send. */
    val inClosetQuantity: Int get() = (ownedQuantity - atLaundryQuantity).coerceAtLeast(0)
}

/** Just enough of a garment to draw its thumbnail - avoids loading whole rows for photo lookups. */
data class ItemThumb(val id: Long, val imagePath: String?)

/**
 * One time a garment went to the laundry, flattened with the ticket it belonged to so the article
 * screen can build "last washed / last pressed" and its activity feed without extra lookups.
 */
data class ItemCareEvent(
    val ticketItemId: Long,
    val ticketId: Long,
    val serviceType: ServiceType,
    val providerName: String?,
    val sentAt: Long,
    val returnedAt: Long?,
    val returned: Boolean,
    val lost: Boolean,
    val ticketStatus: TicketStatus,
    /** Units of the article on that trip and how many came back / were lost (1/…/… for a single garment). */
    val quantity: Int = 1,
    val returnedQuantity: Int = 0,
    val lostQuantity: Int = 0,
)

/** Sum of prices for one currency; used so values in different currencies are never added together. */
data class CurrencyAmount(val currency: String, val total: Double)

/**
 * One of possibly several photos of a garment. [isPrimary] marks the one shown in the
 * closet grid/list and used as [ClothingItem.imagePath] (kept in sync as a denormalized
 * cache so existing single-photo call sites don't need to change).
 */
@Entity(
    tableName = "clothing_item_photos",
    foreignKeys = [
        ForeignKey(
            entity = ClothingItem::class,
            parentColumns = ["id"],
            childColumns = ["clothingItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clothingItemId")],
)
data class ClothingItemPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clothingItemId: Long,
    val path: String,
    val isPrimary: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A batch of clothes sent to a laundry/press service together. */
@Entity(tableName = "laundry_tickets")
data class LaundryTicket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceType: ServiceType,
    val providerName: String? = null,
    val sentAt: Long = System.currentTimeMillis(),
    val expectedReturnAt: Long? = null,
    val receivedAt: Long? = null,
    val status: TicketStatus = TicketStatus.SENT,
    val notes: String? = null,
)

/**
 * Join row: one garment inside one ticket. Kept even after the ticket is closed so
 * we retain full history and can tell which garments a given laundry run ever lost.
 */
@Entity(
    tableName = "laundry_ticket_items",
    foreignKeys = [
        ForeignKey(
            entity = LaundryTicket::class,
            parentColumns = ["id"],
            childColumns = ["ticketId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClothingItem::class,
            parentColumns = ["id"],
            childColumns = ["clothingItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ticketId"), Index("clothingItemId")],
)
data class LaundryTicketItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: Long,
    val clothingItemId: Long,
    /** Fully accounted for with at least one unit back. Derived from the quantities below. */
    val returned: Boolean = false,
    /** Fully accounted for with at least one unit lost. Derived from the quantities below. */
    val lost: Boolean = false,
    val returnedAt: Long? = null,
    /** Units of the article sent on this ticket, and how many of them have come back / been lost. */
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    @ColumnInfo(defaultValue = "0") val returnedQuantity: Int = 0,
    @ColumnInfo(defaultValue = "0") val lostQuantity: Int = 0,
) {
    /** Units not yet accounted for. */
    val pendingQuantity: Int get() = (quantity - returnedQuantity - lostQuantity).coerceAtLeast(0)
    val isResolved: Boolean get() = pendingQuantity == 0
}

/** Plain, serializable snapshot of the whole database, used by export/import. */
@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val clothingItems: List<BackupClothingItem>,
    val tickets: List<BackupTicket>,
    val ticketItems: List<BackupTicketItem>,
)

@Serializable
data class BackupClothingItem(
    val id: Long,
    val title: String,
    val type: String,
    val imagePath: String?,
    val price: Double?,
    val status: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archiveReason: String? = null,
    val archivedAt: Long? = null,
    val archiveNotes: String? = null,
    val photos: List<BackupPhoto> = emptyList(),
    val brand: String? = null,
    val garmentType: String? = null,
    val color: String? = null,
    val currency: String,
    val isWinterWear: Boolean,
    val quantity: Int,
)

@Serializable
data class BackupPhoto(
    val path: String,
    val isPrimary: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupTicket(
    val id: Long,
    val serviceType: String,
    val providerName: String?,
    val sentAt: Long,
    val expectedReturnAt: Long?,
    val receivedAt: Long?,
    val status: String,
    val notes: String?,
)

@Serializable
data class BackupTicketItem(
    val id: Long,
    val ticketId: Long,
    val clothingItemId: Long,
    val returnedAt: Long?,
    val quantity: Int,
    val returnedQuantity: Int,
    val lostQuantity: Int,
)
