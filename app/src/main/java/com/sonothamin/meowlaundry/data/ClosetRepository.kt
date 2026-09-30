package com.sonothamin.meowlaundry.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Single entry point the ViewModels talk to. Keeps Room, photo files and the two related
 * tables (closet + laundry tickets) consistent with each other.
 */
class ClosetRepository(
    private val database: AppDatabase,
    private val photoStore: PhotoStore,
) {
    private val clothingDao = database.clothingDao()
    private val laundryDao = database.laundryDao()
    private val photoDao = database.photoDao()

    // --- Closet ---------------------------------------------------------

    fun observeAllClothing(): Flow<List<ClothingItem>> = clothingDao.observeAll()

    /** Id + photo path only; cheap enough to re-run whenever any garment changes. */
    fun observeItemThumbs(): Flow<List<ItemThumb>> = clothingDao.observeThumbs()

    fun observeClothingByStatus(status: ClothingStatus): Flow<List<ClothingItem>> =
        clothingDao.observeByStatus(status)

    /** Articles with at least one unit out at the laundry (including part-sent multi-unit ones). */
    fun observeAtLaundry(): Flow<List<ClothingItem>> = clothingDao.observeAtLaundry()

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

    /** Counts are units, not articles: 7 boxers in the closet count as 7. */
    fun observeSummary(): Flow<ClosetSummary> = combine(
        clothingDao.observeInClosetUnits(),
        clothingDao.observeAtLaundryUnits(),
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
            // The cached lost/out counts belong to the ticket rows, not to the edit form, so they
            // are re-read here rather than trusted from the (possibly stale) item being saved.
            val current = clothingDao.getById(item.id)
            val floor = ((current?.lostQuantity ?: 0) + (current?.atLaundryQuantity ?: 0)).coerceAtLeast(1)
            clothingDao.update(
                item.copy(
                    quantity = item.quantity.coerceAtLeast(floor),
                    atLaundryQuantity = current?.atLaundryQuantity ?: 0,
                    lostQuantity = current?.lostQuantity ?: 0,
                )
            )
            recomputeCounts(item.id)
            item.id
        } else {
            clothingDao.upsert(item.copy(quantity = item.quantity.coerceAtLeast(1), atLaundryQuantity = 0, lostQuantity = 0))
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

    fun observeTicket(id: Long): Flow<LaundryTicket?> = laundryDao.observeTicket(id)

    fun observeItemsForTicket(ticketId: Long): Flow<List<LaundryTicketItem>> =
        laundryDao.observeItemsForTicket(ticketId)

    fun observeGarmentsForTicket(ticketId: Long): Flow<List<ClothingItem>> =
        laundryDao.observeGarmentsForTicket(ticketId)

    /**
     * The garments on a ticket for printing, with each one's [ClothingItem.quantity] replaced by
     * the number of units on THIS ticket - so a label can say "Boxers x5" instead of the total owned.
     */
    suspend fun garmentsForPrint(ticketId: Long): List<ClothingItem> {
        val units = laundryDao.getItemsForTicket(ticketId).associate { it.clothingItemId to it.quantity }
        return laundryDao.observeGarmentsForTicket(ticketId).first().map { it.copy(quantity = units[it.id] ?: 1) }
    }

    fun observeHistoryForItem(clothingItemId: Long): Flow<List<LaundryTicketItem>> =
        laundryDao.observeHistoryForItem(clothingItemId)

    fun observeCareEventsForItem(clothingItemId: Long): Flow<List<ItemCareEvent>> =
        laundryDao.observeCareEvents(clothingItemId)

    suspend fun getTicket(id: Long) = laundryDao.getTicket(id)

    fun observeAllTicketItems(): Flow<List<LaundryTicketItem>> = laundryDao.observeAllTicketItems()

    suspend fun getItemsForTicket(ticketId: Long): List<LaundryTicketItem> = laundryDao.getItemsForTicket(ticketId)

    /** Sets, changes (or with null, clears) the day a ticket is expected back. */
    suspend fun setExpectedReturn(ticketId: Long, at: Long?) = laundryDao.setExpectedReturn(ticketId, at)

    /** Updates a ticket's editable fields (service, provider, due date, notes). */
    suspend fun updateTicket(ticket: LaundryTicket) = laundryDao.updateTicket(ticket)

    suspend fun getOpenTicketsWithDueDate(): List<LaundryTicket> = laundryDao.getOpenTicketsWithDueDate()

    /**
     * Sends articles to the laundry: creates the ticket with one row per article and marks the
     * units out. Each entry's quantity is clamped to what is actually in the closet.
     */
    suspend fun sendToLaundry(ticket: LaundryTicket, entries: List<TicketEntry>): Long {
        val items = clothingDao.getByIds(entries.map { it.clothingItemId }).associateBy { it.id }
        val clamped = entries.mapNotNull { e ->
            val item = items[e.clothingItemId] ?: return@mapNotNull null
            TicketEntry(e.clothingItemId, unitsToSend(e.quantity, item.inClosetQuantity))
        }
        val ticketId = laundryDao.createTicketWithItems(ticket, clamped)
        clamped.map { it.clothingItemId }.distinct().forEach { recomputeCounts(it) }
        return ticketId
    }

    /**
     * Adds more articles to a ticket that's already been sent, e.g. fixing a mistake in Edit ticket.
     * An article already on the ticket just gets more units on its existing row.
     */
    suspend fun addGarmentsToTicket(ticketId: Long, entries: List<TicketEntry>) {
        if (entries.isEmpty()) return
        val existing = laundryDao.getItemsForTicket(ticketId).associateBy { it.clothingItemId }
        val items = clothingDao.getByIds(entries.map { it.clothingItemId }).associateBy { it.id }
        val toInsert = mutableListOf<LaundryTicketItem>()
        val toUpdate = mutableListOf<LaundryTicketItem>()
        entries.forEach { e ->
            val item = items[e.clothingItemId] ?: return@forEach
            val q = unitsToSend(e.quantity, item.inClosetQuantity)
            val row = existing[e.clothingItemId]
            if (row != null) toUpdate += row.copy(quantity = row.quantity + q).synced()
            else toInsert += LaundryTicketItem(ticketId = ticketId, clothingItemId = e.clothingItemId, quantity = q)
        }
        if (toInsert.isNotEmpty()) laundryDao.insertTicketItems(toInsert)
        if (toUpdate.isNotEmpty()) laundryDao.updateTicketItems(toUpdate)
        entries.map { it.clothingItemId }.distinct().forEach { recomputeCounts(it) }
        refreshTicketStatus(ticketId)
    }

    /**
     * Changes how many units of an article are on a ticket. Never below what has already been
     * accounted for (returned or lost) and never more than the closet can supply.
     */
    suspend fun setTicketItemQuantity(ticketId: Long, clothingItemId: Long, quantity: Int) {
        val row = laundryDao.getItemsForTicket(ticketId).firstOrNull { it.clothingItemId == clothingItemId } ?: return
        val item = clothingDao.getById(clothingItemId) ?: return
        val floor = (row.returnedQuantity + row.lostQuantity).coerceAtLeast(1)
        val ceiling = row.quantity + item.inClosetQuantity
        val q = quantity.coerceIn(floor, ceiling.coerceAtLeast(floor))
        if (q == row.quantity) return
        laundryDao.updateTicketItem(row.copy(quantity = q).synced())
        recomputeCounts(clothingItemId)
        refreshTicketStatus(ticketId)
    }

    /**
     * Takes an article off a ticket, e.g. it was added by mistake. Units already returned or lost
     * on this ticket stay recorded, so only a row with nothing accounted for is removed outright.
     *
     * A ticket's last garment can't be removed: that would leave an empty ticket which can
     * never be resolved, closed or deleted. Returns whether the garment was actually removed.
     */
    suspend fun removeGarmentFromTicket(ticketId: Long, clothingItemId: Long): Boolean {
        val rows = laundryDao.getItemsForTicket(ticketId)
        val row = rows.firstOrNull { it.clothingItemId == clothingItemId } ?: return false
        if (row.returnedQuantity + row.lostQuantity > 0) return false
        if (rows.size <= 1) return false
        laundryDao.deleteTicketItem(ticketId, clothingItemId)
        recomputeCounts(clothingItemId)
        refreshTicketStatus(ticketId)
        return true
    }

    /**
     * Deletes tickets that have no garments (left behind by older versions, which let the last
     * garment be removed in Edit ticket). Returns how many were removed. Safe to run repeatedly.
     */
    suspend fun purgeEmptyTickets(): Int = laundryDao.deleteEmptyTickets()

    /**
     * Records the laundry's answer for a ticket. [resolutions] maps an article to how many of its
     * units on this ticket are now back and how many are lost (absolute, not a delta); the rest
     * are still out. (0, 0) undoes an earlier decision. Fully lost articles go to the Archive,
     * filed under "Lost", and come back out of it if the loss is undone.
     */
    suspend fun resolveTicket(ticketId: Long, resolutions: Map<Long, Resolution>) {
        val now = System.currentTimeMillis()
        val rows = laundryDao.getItemsForTicket(ticketId)
        val updated = rows.map { row -> resolutions[row.clothingItemId]?.let { row.resolvedTo(it, now) } ?: row }
        laundryDao.updateTicketItems(updated)
        resolutions.keys.forEach { recomputeCounts(it, now, "Lost at the laundry (ticket #$ticketId)") }
        refreshTicketStatus(ticketId, now)
    }

    /** Recomputes a ticket's status from its rows (all accounted for -> RECEIVED, some -> PARTIALLY_RECEIVED). */
    private suspend fun refreshTicketStatus(ticketId: Long, now: Long = System.currentTimeMillis()) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        if (ticket.status == TicketStatus.CLOSED) return
        val newStatus = ticketStatusFor(laundryDao.getItemsForTicket(ticketId))
        laundryDao.updateTicket(
            ticket.copy(status = newStatus, receivedAt = if (newStatus == TicketStatus.RECEIVED) now else null)
        )
    }


    /**
     * Recomputes an article's cached out/lost counts from its ticket rows - the rows are the
     * single source of truth - and settles its status: every unit lost -> archived as Lost; a
     * loss that was undone -> back out of the archive; otherwise AT_LAUNDRY only when no unit is
     * left in the closet. An article the person archived themselves is left alone.
     */
    suspend fun recomputeCounts(itemId: Long, now: Long = System.currentTimeMillis(), lostNote: String? = null) {
        val item = clothingDao.getById(itemId) ?: return
        val rows = laundryDao.getItemsForClothing(itemId)
        val lost = rows.sumOf { it.lostQuantity }
        val out = rows.sumOf { it.pendingQuantity }
        clothingDao.setCounts(itemId, out, lost)
        val owned = (item.quantity - lost).coerceAtLeast(0)
        val archivedAsLost = item.status == ClothingStatus.ARCHIVED && item.archiveReason == ArchiveReason.LOST
        when {
            owned == 0 && lost > 0 -> if (item.status != ClothingStatus.ARCHIVED) {
                clothingDao.archive(itemId, ArchiveReason.LOST, lostNote ?: "Lost at the laundry", now)
            }
            archivedAsLost && owned > 0 -> {
                clothingDao.unarchive(itemId, now)
                clothingDao.setStatus(itemId, if (out > 0 && out >= owned) ClothingStatus.AT_LAUNDRY else ClothingStatus.IN_CLOSET, now)
            }
            item.status == ClothingStatus.ARCHIVED -> Unit
            else -> clothingDao.setStatus(
                itemId,
                if (out > 0 && out >= owned) ClothingStatus.AT_LAUNDRY else ClothingStatus.IN_CLOSET,
                now,
            )
        }
    }

    suspend fun closeTicket(ticketId: Long) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        laundryDao.updateTicket(ticket.copy(status = TicketStatus.CLOSED))
    }


    /**
     * Undoes [closeTicket]: puts the ticket back to the state its garments imply
     * (all accounted for -> RECEIVED, some -> PARTIALLY_RECEIVED, none -> SENT).
     */
    suspend fun reopenTicket(ticketId: Long) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        if (ticket.status != TicketStatus.CLOSED) return
        laundryDao.updateTicket(ticket.copy(status = ticketStatusFor(laundryDao.getItemsForTicket(ticketId))))
    }

    /**
     * Deletes a ticket, open or closed. Garments still out simply come back to the closet (the
     * rows were the only thing keeping them "at the laundry"); units it lost stay lost.
     */
    suspend fun deleteTicketById(ticketId: Long) {
        val ticket = laundryDao.getTicket(ticketId) ?: return
        val rows = laundryDao.getItemsForTicket(ticketId)
        laundryDao.deleteTicket(ticket)
        settleAfterDelete(rows)
    }

    /**
     * Once a ticket's rows are gone, so is the only record of units it lost. Those units are gone
     * from the closet for good, so they are taken off the article's quantity now - otherwise the
     * next recount would quietly "find" them again.
     */
    private suspend fun settleAfterDelete(rows: List<LaundryTicketItem>) {
        rows.filter { it.lostQuantity > 0 }.groupBy { it.clothingItemId }.forEach { (id, lostRows) ->
            val item = clothingDao.getById(id) ?: return@forEach
            if (item.status != ClothingStatus.ARCHIVED) {
                clothingDao.setQuantity(id, (item.quantity - lostRows.sumOf { it.lostQuantity }).coerceAtLeast(1))
            }
        }
        rows.map { it.clothingItemId }.distinct().forEach { recomputeCounts(it) }
    }

    suspend fun getTicketsByIds(ids: List<Long>): List<LaundryTicket> = laundryDao.getTicketsByIds(ids)

    /** Provider names used before, most recently used first, for the order-creation autocomplete. */
    suspend fun getRecentProviderNames(): List<String> = laundryDao.getDistinctProviderNames()

    /** Deletes only the CLOSED tickets among [ids]; returns how many were removed. */
    suspend fun deleteClosedTicketsByIds(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val rows = laundryDao.getClosedTicketIdsAmong(ids).flatMap { laundryDao.getItemsForTicket(it) }
        val removed = laundryDao.deleteClosedTicketsAmong(ids)
        settleAfterDelete(rows)
        return removed
    }

    /** "Clear history": removes every CLOSED ticket and leaves open ones untouched. */
    suspend fun clearClosedTickets(): Int {
        val rows = laundryDao.getClosedTicketIds().flatMap { laundryDao.getItemsForTicket(it) }
        val removed = laundryDao.deleteAllClosedTickets()
        settleAfterDelete(rows)
        return removed
    }

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
                    type = it.type,
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
                    isWinterWear = it.isWinterWear,
                    quantity = it.quantity,
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
                    returnedAt = it.returnedAt,
                    quantity = it.quantity,
                    returnedQuantity = it.returnedQuantity,
                    lostQuantity = it.lostQuantity,
                )
            },
        )
    }

    /**
     * Replaces everything currently in the database with the contents of [payload].
     *
     * The payload is fully checked and converted before the database is touched, and the swap
     * itself is one transaction, so a bad or corrupt backup fails with the current data intact.
     */
    suspend fun importAll(payload: BackupPayload) {
        val items = payload.clothingItems.map {
            ClothingItem(
                id = it.id,
                title = it.title,
                type = it.type,
                imagePath = it.imagePath,
                price = it.price,
                status = backupEnum<ClothingStatus>(it.status, "garment status"),
                notes = it.notes,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                archiveReason = it.archiveReason?.let { r -> backupEnum<ArchiveReason>(r, "archive reason") },
                archivedAt = it.archivedAt,
                archiveNotes = it.archiveNotes,
                brand = it.brand,
                garmentType = it.garmentType,
                color = it.color,
                currency = it.currency,
                isWinterWear = it.isWinterWear,
                quantity = it.quantity.coerceAtLeast(1),
            )
        }
        val photos = payload.clothingItems.flatMap { item ->
            item.photos.map { p ->
                ClothingItemPhoto(clothingItemId = item.id, path = p.path, isPrimary = p.isPrimary, sortOrder = p.sortOrder)
            }
        }
        val tickets = payload.tickets.map {
            LaundryTicket(
                id = it.id,
                serviceType = backupEnum<ServiceType>(it.serviceType, "service type"),
                providerName = it.providerName,
                sentAt = it.sentAt,
                expectedReturnAt = it.expectedReturnAt,
                receivedAt = it.receivedAt,
                status = backupEnum<TicketStatus>(it.status, "ticket status"),
                notes = it.notes,
            )
        }
        val ticketItems = payload.ticketItems.map {
            val q = it.quantity.coerceAtLeast(1)
            val returnedQ = it.returnedQuantity.coerceIn(0, q)
            LaundryTicketItem(
                id = it.id,
                ticketId = it.ticketId,
                clothingItemId = it.clothingItemId,
                returnedAt = it.returnedAt,
                quantity = q,
                returnedQuantity = returnedQ,
                lostQuantity = it.lostQuantity.coerceIn(0, q - returnedQ),
            ).synced()
        }

        database.withTransaction {
            clothingDao.deleteAll() // cascades to clothing_item_photos rows
            clearAllTickets()
            clothingDao.upsertAll(items)
            if (photos.isNotEmpty()) photoDao.insertAll(photos)
            laundryDao.upsertTickets(tickets)
            laundryDao.upsertTicketItems(ticketItems)
            // The article counts are caches of the ticket rows, so rebuild them for everything imported.
            items.forEach { recomputeCounts(it.id) }
        }
    }

    private inline fun <reified E : Enum<E>> backupEnum(name: String, what: String): E =
        enumValues<E>().firstOrNull { it.name == name } ?: error("This backup has an unknown $what: \"$name\"")

    private suspend fun allClothingSnapshot(): List<ClothingItem> = clothingDao.observeAll().first()

    private suspend fun allTicketsSnapshot(): List<LaundryTicket> = laundryDao.observeAllTickets().first()
}
