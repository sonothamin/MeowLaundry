package com.sonothamin.meowlaundry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Single entry point the ViewModels talk to. Keeps Room, photo files and the two related
 * tables (closet + laundry tickets) consistent with each other.
 */
class ClosetRepository(
    private val clothingDao: ClothingDao,
    private val laundryDao: LaundryDao,
    private val photoStore: PhotoStore,
) {
    // --- Closet ---------------------------------------------------------

    fun observeAllClothing(): Flow<List<ClothingItem>> = clothingDao.observeAll()

    fun observeClothingByStatus(status: ClothingStatus): Flow<List<ClothingItem>> =
        clothingDao.observeByStatus(status)

    fun observeClothingById(id: Long): Flow<ClothingItem?> = clothingDao.observeById(id)

    suspend fun getClothingById(id: Long): ClothingItem? = clothingDao.getById(id)

    /** Summary counts/value for the closet dashboard, recomputed live. */
    data class ClosetSummary(
        val inClosetCount: Int,
        val atLaundryCount: Int,
        val lostCount: Int,
        val lostValue: Double,
    )

    fun observeSummary(): Flow<ClosetSummary> = combine(
        clothingDao.observeCountByStatus(ClothingStatus.IN_CLOSET),
        clothingDao.observeCountByStatus(ClothingStatus.AT_LAUNDRY),
        clothingDao.observeCountByStatus(ClothingStatus.LOST),
        clothingDao.observeValueByStatus(ClothingStatus.LOST),
    ) { inCloset, atLaundry, lost, lostValue ->
        ClosetSummary(inCloset, atLaundry, lost, lostValue)
    }

    suspend fun saveClothing(item: ClothingItem): Long = clothingDao.upsert(item)

    suspend fun deleteClothing(item: ClothingItem) {
        photoStore.delete(item.imagePath)
        clothingDao.delete(item)
    }

    // --- Laundry tickets --------------------------------------------------

    fun observeAllTickets(): Flow<List<LaundryTicket>> = laundryDao.observeAllTickets()

    fun observeActiveTickets(): Flow<List<LaundryTicket>> = laundryDao.observeActiveTickets()

    fun observeTicket(id: Long): Flow<LaundryTicket?> = laundryDao.observeTicket(id)

    fun observeItemsForTicket(ticketId: Long): Flow<List<LaundryTicketItem>> =
        laundryDao.observeItemsForTicket(ticketId)

    fun observeGarmentsForTicket(ticketId: Long): Flow<List<ClothingItem>> =
        laundryDao.observeGarmentsForTicket(ticketId)

    fun observeHistoryForItem(clothingItemId: Long): Flow<List<LaundryTicketItem>> =
        laundryDao.observeHistoryForItem(clothingItemId)

    suspend fun getTicket(id: Long) = laundryDao.getTicket(id)

    suspend fun getGarmentsForIds(ids: List<Long>): List<ClothingItem> =
        ids.mapNotNull { clothingDao.getById(it) }

    /** Sends a batch of garments to the laundry: creates the ticket and marks garments AT_LAUNDRY. */
    suspend fun sendToLaundry(ticket: LaundryTicket, clothingItemIds: List<Long>): Long =
        laundryDao.createTicketWithItems(ticket, clothingItemIds, clothingDao)

    /**
     * Records the laundry's answer for a ticket: each garment is either returned (back to
     * IN_CLOSET) or lost (flagged LOST, so its price counts toward the "lost value" total).
     */
    suspend fun resolveTicket(
        ticketId: Long,
        returnedItemIds: Set<Long>,
        lostItemIds: Set<Long>,
    ) {
        val items = laundryDao.getItemsForTicket(ticketId)
        val now = System.currentTimeMillis()
        val updated = items.map { item ->
            when (item.clothingItemId) {
                in returnedItemIds -> item.copy(returned = true, lost = false, returnedAt = now)
                in lostItemIds -> item.copy(returned = false, lost = true)
                else -> item
            }
        }
        laundryDao.updateTicketItems(updated)

        returnedItemIds.forEach { clothingDao.setStatus(it, ClothingStatus.IN_CLOSET, now) }
        lostItemIds.forEach { clothingDao.setStatus(it, ClothingStatus.LOST, now) }

        val allAccountedFor = updated.all { it.returned || it.lost }
        val newStatus = when {
            allAccountedFor -> TicketStatus.RECEIVED
            updated.any { it.returned || it.lost } -> TicketStatus.PARTIALLY_RECEIVED
            else -> TicketStatus.SENT
        }
        val ticket = laundryDao.getTicket(ticketId) ?: return
        laundryDao.updateTicket(
            ticket.copy(
                status = newStatus,
                receivedAt = if (newStatus == TicketStatus.RECEIVED) now else ticket.receivedAt,
            )
        )
    }

    suspend fun closeTicket(ticketId: Long) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        laundryDao.updateTicket(ticket.copy(status = TicketStatus.CLOSED))
    }

    suspend fun deleteTicket(ticket: LaundryTicket) = laundryDao.deleteTicket(ticket)

    // --- Backup -------------------------------------------------------

    suspend fun exportAll(): BackupPayload {
        val allClothing = allClothingSnapshot()
        val allTickets = allTicketsSnapshot()
        val allTicketItems = laundryDao.getAllTicketItems()
        return BackupPayload(
            clothingItems = allClothing.map {
                BackupClothingItem(
                    id = it.id,
                    title = it.title,
                    type = it.type.name,
                    imagePath = it.imagePath,
                    price = it.price,
                    status = it.status.name,
                    notes = it.notes,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            tickets = allTickets.map {
                BackupTicket(
                    id = it.id,
                    serviceType = it.serviceType.name,
                    providerName = it.providerName,
                    sentAt = it.sentAt,
                    expectedReturnAt = it.expectedReturnAt,
                    receivedAt = it.receivedAt,
                    status = it.status.name,
                    notes = it.notes,
                )
            },
            ticketItems = allTicketItems.map {
                BackupTicketItem(
                    id = it.id,
                    ticketId = it.ticketId,
                    clothingItemId = it.clothingItemId,
                    returned = it.returned,
                    lost = it.lost,
                    returnedAt = it.returnedAt,
                )
            },
        )
    }

    /** Replaces everything currently in the database with the contents of [payload]. */
    suspend fun importAll(payload: BackupPayload) {
        clothingDao.deleteAll()
        laundryDao.deleteAllTicketItems()
        laundryDao.deleteAllTickets()

        clothingDao.upsertAll(
            payload.clothingItems.map {
                ClothingItem(
                    id = it.id,
                    title = it.title,
                    type = ClothingType.valueOf(it.type),
                    imagePath = it.imagePath,
                    price = it.price,
                    status = ClothingStatus.valueOf(it.status),
                    notes = it.notes,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            }
        )
        laundryDao.upsertTickets(
            payload.tickets.map {
                LaundryTicket(
                    id = it.id,
                    serviceType = ServiceType.valueOf(it.serviceType),
                    providerName = it.providerName,
                    sentAt = it.sentAt,
                    expectedReturnAt = it.expectedReturnAt,
                    receivedAt = it.receivedAt,
                    status = TicketStatus.valueOf(it.status),
                    notes = it.notes,
                )
            }
        )
        laundryDao.upsertTicketItems(
            payload.ticketItems.map {
                LaundryTicketItem(
                    id = it.id,
                    ticketId = it.ticketId,
                    clothingItemId = it.clothingItemId,
                    returned = it.returned,
                    lost = it.lost,
                    returnedAt = it.returnedAt,
                )
            }
        )
    }

    private suspend fun allClothingSnapshot(): List<ClothingItem> = clothingDao.observeAll().first()

    private suspend fun allTicketsSnapshot(): List<LaundryTicket> = laundryDao.observeAllTickets().first()
}
