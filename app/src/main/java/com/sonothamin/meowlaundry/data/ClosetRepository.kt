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
    private val photoDao: ClothingPhotoDao,
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
        /** Lost value per currency, so different currencies are never summed together. */
        val lostValues: List<CurrencyAmount>,
    )

    fun observeSummary(): Flow<ClosetSummary> = combine(
        clothingDao.observeCountByStatus(ClothingStatus.IN_CLOSET),
        clothingDao.observeCountByStatus(ClothingStatus.AT_LAUNDRY),
        clothingDao.observeLostCount(),
        clothingDao.observeLostValueByCurrency(),
    ) { inCloset, atLaundry, lost, lostValues ->
        ClosetSummary(inCloset, atLaundry, lost, lostValues)
    }

    fun observeBrandHistory(): Flow<List<String>> = clothingDao.observeBrands()
    fun observeGarmentTypeHistory(): Flow<List<String>> = clothingDao.observeGarmentTypes()
    fun observeColorHistory(): Flow<List<String>> = clothingDao.observeColors()

    /**
     * Inserts a new garment or updates an existing one. Existing rows are updated in place:
     * the upsert used for inserts is INSERT OR REPLACE, which deletes the old row first and so
     * would cascade-delete the item's photos and laundry history on every edit.
     */
    suspend fun saveClothing(item: ClothingItem): Long =
        if (item.id != 0L) {
            clothingDao.update(item)
            item.id
        } else {
            clothingDao.upsert(item)
        }

    suspend fun deleteClothing(item: ClothingItem) {
        photoDao.getForItem(item.id).forEach { photoStore.delete(it.path) }
        photoStore.delete(item.imagePath)
        clothingDao.delete(item) // cascades to clothing_item_photos rows
    }

    suspend fun deleteClothingByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        photoDao.getForItems(ids).forEach { photoStore.delete(it.path) }
        clothingDao.getByIds(ids).forEach { photoStore.delete(it.imagePath) }
        clothingDao.deleteByIds(ids) // cascades to clothing_item_photos rows
    }

    suspend fun getClothingByIds(ids: List<Long>): List<ClothingItem> = clothingDao.getByIds(ids)

    // --- Photos -----------------------------------------------------------

    fun observePhotosForItem(itemId: Long): Flow<List<ClothingItemPhoto>> = photoDao.observeForItem(itemId)

    /** Adds a new photo. The very first photo added for an item becomes primary automatically. */
    suspend fun addPhoto(itemId: Long, path: String): Long {
        val existing = photoDao.getForItem(itemId)
        val makePrimary = existing.isEmpty()
        val id = photoDao.insert(
            ClothingItemPhoto(
                clothingItemId = itemId,
                path = path,
                isPrimary = makePrimary,
                sortOrder = existing.size,
            )
        )
        if (makePrimary) clothingDao.setImagePath(itemId, path)
        return id
    }

    /** Removes a photo. If it was primary, the next remaining photo (if any) becomes primary. */
    suspend fun removePhoto(photo: ClothingItemPhoto) {
        photoStore.delete(photo.path)
        photoDao.deleteById(photo.id)
        if (photo.isPrimary) {
            val remaining = photoDao.getForItem(photo.clothingItemId)
            val newPrimary = remaining.firstOrNull()
            if (newPrimary != null) {
                photoDao.markPrimary(newPrimary.id)
                clothingDao.setImagePath(photo.clothingItemId, newPrimary.path)
            } else {
                clothingDao.setImagePath(photo.clothingItemId, null)
            }
        }
    }

    /** Same as [removePhoto] but looked up by id alone, for callers that only kept the id around. */
    suspend fun removePhotoById(photoId: Long) {
        val photo = photoDao.getById(photoId) ?: return
        removePhoto(photo)
    }

    suspend fun setPrimaryPhoto(itemId: Long, photoId: Long) {
        val photo = photoDao.getForItem(itemId).firstOrNull { it.id == photoId } ?: return
        photoDao.clearPrimary(itemId)
        photoDao.markPrimary(photoId)
        clothingDao.setImagePath(itemId, photo.path)
    }

    // --- Archive ------------------------------------------------------------

    fun observeArchived(): Flow<List<ClothingItem>> = clothingDao.observeArchived()

    fun observeArchivedByReason(reason: ArchiveReason): Flow<List<ClothingItem>> =
        clothingDao.observeArchivedByReason(reason)

    suspend fun archiveItem(id: Long, reason: ArchiveReason, notes: String?) =
        clothingDao.archive(id, reason, notes?.trim()?.ifBlank { null })

    suspend fun unarchiveItem(id: Long) = clothingDao.unarchive(id)

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

    fun observeCareEventsForItem(clothingItemId: Long): Flow<List<ItemCareEvent>> =
        laundryDao.observeCareEvents(clothingItemId)

    suspend fun getTicket(id: Long) = laundryDao.getTicket(id)

    fun observeAllTicketItems(): Flow<List<LaundryTicketItem>> = laundryDao.observeAllTicketItems()

    suspend fun getItemsForTicket(ticketId: Long): List<LaundryTicketItem> = laundryDao.getItemsForTicket(ticketId)

    /** Sets, changes (or with null, clears) the day a ticket is expected back. */
    suspend fun setExpectedReturn(ticketId: Long, at: Long?) = laundryDao.setExpectedReturn(ticketId, at)

    suspend fun getOpenTicketsWithDueDate(): List<LaundryTicket> = laundryDao.getOpenTicketsWithDueDate()

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
        /** Garments to put back to "still out at the laundry" - this is how a saved decision is undone. */
        resetItemIds: Set<Long> = emptySet(),
    ) {
        val items = laundryDao.getItemsForTicket(ticketId)
        val now = System.currentTimeMillis()
        val updated = items.map { item ->
            when (item.clothingItemId) {
                in returnedItemIds -> item.copy(returned = true, lost = false, returnedAt = now)
                in lostItemIds -> item.copy(returned = false, lost = true, returnedAt = null)
                in resetItemIds -> item.copy(returned = false, lost = false, returnedAt = null)
                else -> item
            }
        }
        laundryDao.updateTicketItems(updated)

        returnedItemIds.forEach { putBack(it, ClothingStatus.IN_CLOSET, now) }
        // A lost garment goes to the Archive, filed under "Lost".
        lostItemIds.forEach {
            clothingDao.archive(it, ArchiveReason.LOST, "Lost at the laundry (ticket #$ticketId)", now)
        }
        resetItemIds.forEach { putBack(it, ClothingStatus.AT_LAUNDRY, now) }

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
                receivedAt = if (newStatus == TicketStatus.RECEIVED) now else null,
            )
        )
    }

    suspend fun closeTicket(ticketId: Long) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        laundryDao.updateTicket(ticket.copy(status = TicketStatus.CLOSED))
    }

    /**
     * Sets a garment's status after a laundry decision. If an earlier decision had archived it as
     * lost, it comes back out of the archive first, so undoing "lost" really restores it.
     */
    private suspend fun putBack(id: Long, status: ClothingStatus, now: Long) {
        val item = clothingDao.getById(id)
        if (item?.status == ClothingStatus.ARCHIVED && item.archiveReason == ArchiveReason.LOST) {
            clothingDao.unarchive(id, now)
        }
        clothingDao.setStatus(id, status, now)
    }

    /** Files garments marked lost under the old scheme into the archive. Safe to run repeatedly. */
    suspend fun migrateLegacyLost() = clothingDao.archiveLegacyLost()

    /**
     * Undoes [closeTicket]: puts the ticket back to the state its garments imply
     * (all accounted for -> RECEIVED, some -> PARTIALLY_RECEIVED, none -> SENT).
     */
    suspend fun reopenTicket(ticketId: Long) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        if (ticket.status != TicketStatus.CLOSED) return
        val items = laundryDao.getItemsForTicket(ticketId)
        val decided = items.count { it.returned || it.lost }
        val status = when {
            decided == items.size -> TicketStatus.RECEIVED
            decided > 0 -> TicketStatus.PARTIALLY_RECEIVED
            else -> TicketStatus.SENT
        }
        laundryDao.updateTicket(ticket.copy(status = status))
    }

    suspend fun deleteTicket(ticket: LaundryTicket) = laundryDao.deleteTicket(ticket)

    suspend fun deleteTicketsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        laundryDao.deleteTicketsByIds(ids)
    }

    suspend fun getTicketsByIds(ids: List<Long>): List<LaundryTicket> = laundryDao.getTicketsByIds(ids)

    /** Deletes only the CLOSED tickets among [ids]; returns how many were removed. */
    suspend fun deleteClosedTicketsByIds(ids: List<Long>): Int =
        if (ids.isEmpty()) 0 else laundryDao.deleteClosedTicketsAmong(ids)

    /** "Clear history": removes every CLOSED ticket and leaves open ones untouched. */
    suspend fun clearClosedTickets(): Int = laundryDao.deleteAllClosedTickets()

    /** Deletes every ticket, open or closed. Only for wiping data (e.g. restore); not "clear history". */
    suspend fun clearAllTickets() {
        laundryDao.deleteAllTicketItems()
        laundryDao.deleteAllTickets()
    }

    // --- Backup -------------------------------------------------------

    suspend fun exportAll(): BackupPayload {
        val allClothing = allClothingSnapshot()
        val allTickets = allTicketsSnapshot()
        val allTicketItems = laundryDao.getAllTicketItems()
        val photosByItem = photoDao.getForItems(allClothing.map { it.id }).groupBy { it.clothingItemId }
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
                    archiveReason = it.archiveReason?.name,
                    archivedAt = it.archivedAt,
                    archiveNotes = it.archiveNotes,
                    brand = it.brand,
                    garmentType = it.garmentType,
                    color = it.color,
                    currency = it.currency,
                    photos = photosByItem[it.id].orEmpty().map { photo ->
                        BackupPhoto(path = photo.path, isPrimary = photo.isPrimary, sortOrder = photo.sortOrder)
                    },
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
        clothingDao.deleteAll() // cascades to clothing_item_photos rows
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
                    archiveReason = it.archiveReason?.let { r -> runCatching { ArchiveReason.valueOf(r) }.getOrNull() },
                    archivedAt = it.archivedAt,
                    archiveNotes = it.archiveNotes,
                    brand = it.brand,
                    garmentType = it.garmentType,
                    color = it.color,
                    currency = it.currency,
                )
            }
        )
        payload.clothingItems.forEach { item ->
            if (item.photos.isNotEmpty()) {
                photoDao.insertAll(
                    item.photos.map { p ->
                        ClothingItemPhoto(
                            clothingItemId = item.id,
                            path = p.path,
                            isPrimary = p.isPrimary,
                            sortOrder = p.sortOrder,
                        )
                    }
                )
            }
        }
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
        clothingDao.archiveLegacyLost()
    }

    private suspend fun allClothingSnapshot(): List<ClothingItem> = clothingDao.observeAll().first()

    private suspend fun allTicketsSnapshot(): List<LaundryTicket> = laundryDao.observeAllTickets().first()
}
