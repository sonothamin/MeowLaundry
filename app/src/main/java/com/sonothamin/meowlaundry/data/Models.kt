package com.sonothamin.meowlaundry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Broad category of a garment, used for filtering the closet and choosing an icon. */
enum class ClothingType {
    TOP, BOTTOM, DRESS, OUTERWEAR, UNDERWEAR, SLEEPWEAR, ACCESSORY, FOOTWEAR, OTHER
}

/** Where a single garment currently is. */
enum class ClothingStatus {
    IN_CLOSET, AT_LAUNDRY, LOST, ARCHIVED
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
    val type: ClothingType,
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
)

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
    val returned: Boolean = false,
    val lost: Boolean = false,
    val returnedAt: Long? = null,
)

/** [LaundryTicket] together with the garments in it, for list/detail screens. */
data class TicketWithItems(
    val ticket: LaundryTicket,
    val items: List<LaundryTicketItem>,
    val garments: List<ClothingItem>,
)

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
    val returned: Boolean,
    val lost: Boolean,
    val returnedAt: Long?,
)
