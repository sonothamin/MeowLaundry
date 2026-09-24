package com.sonothamin.meowlaundry.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingDao {
    @Query("SELECT * FROM clothing_items ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE status = :status ORDER BY title COLLATE NOCASE ASC")
    fun observeByStatus(status: ClothingStatus): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getById(id: Long): ClothingItem?

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun observeById(id: Long): Flow<ClothingItem?>

    @Query("SELECT COUNT(*) FROM clothing_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clothing_items WHERE status = :status")
    fun observeCountByStatus(status: ClothingStatus): Flow<Int>

    @Query("SELECT COALESCE(SUM(price), 0.0) FROM clothing_items WHERE status = :status")
    fun observeValueByStatus(status: ClothingStatus): Flow<Double>

    @Query(
        """
        SELECT currency AS currency, COALESCE(SUM(price), 0.0) AS total
        FROM clothing_items
        WHERE status = :status AND price IS NOT NULL
        GROUP BY currency
        ORDER BY total DESC
        """
    )
    fun observeValueByStatusAndCurrency(status: ClothingStatus): Flow<List<CurrencyAmount>>

    // Suggestions from previous entries, most used first (case-insensitive).
    @Query("SELECT brand FROM clothing_items WHERE brand IS NOT NULL AND TRIM(brand) != '' GROUP BY LOWER(brand) ORDER BY COUNT(*) DESC, brand COLLATE NOCASE")
    fun observeBrands(): Flow<List<String>>

    @Query("SELECT garmentType FROM clothing_items WHERE garmentType IS NOT NULL AND TRIM(garmentType) != '' GROUP BY LOWER(garmentType) ORDER BY COUNT(*) DESC, garmentType COLLATE NOCASE")
    fun observeGarmentTypes(): Flow<List<String>>

    @Query("SELECT color FROM clothing_items WHERE color IS NOT NULL AND TRIM(color) != '' GROUP BY LOWER(color) ORDER BY COUNT(*) DESC, color COLLATE NOCASE")
    fun observeColors(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ClothingItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ClothingItem>)

    @Update
    suspend fun update(item: ClothingItem)

    @Delete
    suspend fun delete(item: ClothingItem)

    @Query("DELETE FROM clothing_items")
    suspend fun deleteAll()

    @Query("UPDATE clothing_items SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: ClothingStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE clothing_items SET status = :status, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setStatusForAll(ids: List<Long>, status: ClothingStatus, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM clothing_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ClothingItem>

    @Query("DELETE FROM clothing_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM clothing_items WHERE status = 'ARCHIVED' ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE status = 'ARCHIVED' AND archiveReason = :reason ORDER BY archivedAt DESC")
    fun observeArchivedByReason(reason: ArchiveReason): Flow<List<ClothingItem>>

    @Query(
        """
        UPDATE clothing_items
        SET status = 'ARCHIVED', archiveReason = :reason, archivedAt = :now, archiveNotes = :notes, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun archive(id: Long, reason: ArchiveReason, notes: String?, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE clothing_items
        SET status = 'IN_CLOSET', archiveReason = NULL, archivedAt = NULL, archiveNotes = NULL, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun unarchive(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE clothing_items SET imagePath = :path WHERE id = :id")
    suspend fun setImagePath(id: Long, path: String?)
}

@Dao
interface ClothingPhotoDao {
    @Query("SELECT * FROM clothing_item_photos WHERE id = :id")
    suspend fun getById(id: Long): ClothingItemPhoto?

    @Query("SELECT * FROM clothing_item_photos WHERE clothingItemId = :itemId ORDER BY sortOrder ASC, id ASC")
    fun observeForItem(itemId: Long): Flow<List<ClothingItemPhoto>>

    @Query("SELECT * FROM clothing_item_photos WHERE clothingItemId = :itemId ORDER BY sortOrder ASC, id ASC")
    suspend fun getForItem(itemId: Long): List<ClothingItemPhoto>

    @Query("SELECT * FROM clothing_item_photos WHERE clothingItemId IN (:itemIds)")
    suspend fun getForItems(itemIds: List<Long>): List<ClothingItemPhoto>

    @Insert
    suspend fun insert(photo: ClothingItemPhoto): Long

    @Insert
    suspend fun insertAll(photos: List<ClothingItemPhoto>)

    @Update
    suspend fun update(photo: ClothingItemPhoto)

    @Query("DELETE FROM clothing_item_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clothing_item_photos WHERE clothingItemId = :itemId")
    suspend fun deleteAllForItem(itemId: Long)

    @Query("UPDATE clothing_item_photos SET isPrimary = 0 WHERE clothingItemId = :itemId")
    suspend fun clearPrimary(itemId: Long)

    @Query("UPDATE clothing_item_photos SET isPrimary = 1 WHERE id = :photoId")
    suspend fun markPrimary(photoId: Long)
}

@Dao
interface LaundryDao {

    @Query("SELECT * FROM laundry_tickets ORDER BY sentAt DESC")
    fun observeAllTickets(): Flow<List<LaundryTicket>>

    @Query("SELECT * FROM laundry_tickets WHERE status != 'CLOSED' AND status != 'RECEIVED' ORDER BY sentAt DESC")
    fun observeActiveTickets(): Flow<List<LaundryTicket>>

    @Query("SELECT * FROM laundry_tickets WHERE id = :id")
    suspend fun getTicket(id: Long): LaundryTicket?

    @Query("UPDATE laundry_tickets SET expectedReturnAt = :at WHERE id = :id")
    suspend fun setExpectedReturn(id: Long, at: Long?)

    /** Tickets that still have garments out and a due date - what reminders look at. */
    @Query("SELECT * FROM laundry_tickets WHERE expectedReturnAt IS NOT NULL AND status IN ('SENT', 'PARTIALLY_RECEIVED')")
    suspend fun getOpenTicketsWithDueDate(): List<LaundryTicket>

    @Query("SELECT * FROM laundry_ticket_items")
    fun observeAllTicketItems(): Flow<List<LaundryTicketItem>>

    @Query("SELECT * FROM laundry_tickets WHERE id = :id")
    fun observeTicket(id: Long): Flow<LaundryTicket?>

    @Insert
    suspend fun insertTicket(ticket: LaundryTicket): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTickets(tickets: List<LaundryTicket>)

    @Update
    suspend fun updateTicket(ticket: LaundryTicket)

    @Delete
    suspend fun deleteTicket(ticket: LaundryTicket)

    @Query("DELETE FROM laundry_tickets")
    suspend fun deleteAllTickets()

    @Query("DELETE FROM laundry_tickets WHERE id IN (:ids)")
    suspend fun deleteTicketsByIds(ids: List<Long>)

    @Query("SELECT * FROM laundry_tickets WHERE id IN (:ids)")
    suspend fun getTicketsByIds(ids: List<Long>): List<LaundryTicket>

    @Insert
    suspend fun insertTicketItems(items: List<LaundryTicketItem>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTicketItems(items: List<LaundryTicketItem>)

    @Update
    suspend fun updateTicketItem(item: LaundryTicketItem)

    @Update
    suspend fun updateTicketItems(items: List<LaundryTicketItem>)

    @Query("DELETE FROM laundry_ticket_items")
    suspend fun deleteAllTicketItems()

    @Query("DELETE FROM laundry_ticket_items WHERE ticketId IN (:ticketIds)")
    suspend fun deleteTicketItemsByTicketIds(ticketIds: List<Long>)

    @Query("SELECT id FROM laundry_tickets WHERE status = 'CLOSED'")
    suspend fun getClosedTicketIds(): List<Long>

    @Query("SELECT id FROM laundry_tickets WHERE status = 'CLOSED' AND id IN (:ids)")
    suspend fun getClosedTicketIdsAmong(ids: List<Long>): List<Long>

    /**
     * Removes only tickets that are already CLOSED (and their items). Open tickets are left
     * alone: deleting them would strand their garments in AT_LAUNDRY with no ticket to resolve.
     * Returns how many tickets were removed.
     */
    @Transaction
    suspend fun deleteClosedTicketsAmong(ids: List<Long>): Int {
        val closed = getClosedTicketIdsAmong(ids)
        closed.chunked(500).forEach {
            deleteTicketItemsByTicketIds(it)
            deleteTicketsByIds(it)
        }
        return closed.size
    }

    @Transaction
    suspend fun deleteAllClosedTickets(): Int {
        val closed = getClosedTicketIds()
        closed.chunked(500).forEach {
            deleteTicketItemsByTicketIds(it)
            deleteTicketsByIds(it)
        }
        return closed.size
    }

    @Query("SELECT * FROM laundry_ticket_items WHERE ticketId = :ticketId")
    fun observeItemsForTicket(ticketId: Long): Flow<List<LaundryTicketItem>>

    @Query("SELECT * FROM laundry_ticket_items WHERE ticketId = :ticketId")
    suspend fun getItemsForTicket(ticketId: Long): List<LaundryTicketItem>

    @Query("SELECT * FROM laundry_ticket_items")
    suspend fun getAllTicketItems(): List<LaundryTicketItem>

    @Query(
        """
        SELECT ci.* FROM clothing_items ci
        INNER JOIN laundry_ticket_items lti ON lti.clothingItemId = ci.id
        WHERE lti.ticketId = :ticketId
        """
    )
    fun observeGarmentsForTicket(ticketId: Long): Flow<List<ClothingItem>>

    @Query(
        """
        SELECT lti.* FROM laundry_ticket_items lti
        INNER JOIN laundry_tickets t ON t.id = lti.ticketId
        WHERE lti.clothingItemId = :clothingItemId
        ORDER BY t.sentAt DESC
        """
    )
    fun observeHistoryForItem(clothingItemId: Long): Flow<List<LaundryTicketItem>>

    /** Every laundry trip of one garment, newest first. */
    @Query(
        """
        SELECT lti.id AS ticketItemId, t.id AS ticketId, t.serviceType AS serviceType,
               t.providerName AS providerName, t.sentAt AS sentAt, lti.returnedAt AS returnedAt,
               lti.returned AS returned, lti.lost AS lost, t.status AS ticketStatus
        FROM laundry_ticket_items lti
        INNER JOIN laundry_tickets t ON t.id = lti.ticketId
        WHERE lti.clothingItemId = :clothingItemId
        ORDER BY t.sentAt DESC
        """
    )
    fun observeCareEvents(clothingItemId: Long): Flow<List<ItemCareEvent>>

    @Transaction
    suspend fun createTicketWithItems(ticket: LaundryTicket, clothingItemIds: List<Long>, clothingDao: ClothingDao): Long {
        val ticketId = insertTicket(ticket)
        val items = clothingItemIds.map { LaundryTicketItem(ticketId = ticketId, clothingItemId = it) }
        insertTicketItems(items)
        clothingDao.setStatusForAll(clothingItemIds, ClothingStatus.AT_LAUNDRY)
        return ticketId
    }
}
