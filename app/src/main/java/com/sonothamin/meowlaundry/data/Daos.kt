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
}

@Dao
interface LaundryDao {

    @Query("SELECT * FROM laundry_tickets ORDER BY sentAt DESC")
    fun observeAllTickets(): Flow<List<LaundryTicket>>

    @Query("SELECT * FROM laundry_tickets WHERE status != 'CLOSED' AND status != 'RECEIVED' ORDER BY sentAt DESC")
    fun observeActiveTickets(): Flow<List<LaundryTicket>>

    @Query("SELECT * FROM laundry_tickets WHERE id = :id")
    suspend fun getTicket(id: Long): LaundryTicket?

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

    @Transaction
    suspend fun createTicketWithItems(ticket: LaundryTicket, clothingItemIds: List<Long>, clothingDao: ClothingDao): Long {
        val ticketId = insertTicket(ticket)
        val items = clothingItemIds.map { LaundryTicketItem(ticketId = ticketId, clothingItemId = it) }
        insertTicketItems(items)
        clothingDao.setStatusForAll(clothingItemIds, ClothingStatus.AT_LAUNDRY)
        return ticketId
    }
}
