package com.sonothamin.meowlaundry

import android.app.Application
import com.sonothamin.meowlaundry.data.AppDatabase
import com.sonothamin.meowlaundry.data.AppPreferences
import com.sonothamin.meowlaundry.data.ArchiveReason
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.PhotoStore
import com.sonothamin.meowlaundry.data.PrintPreferences
import com.sonothamin.meowlaundry.data.backup.BackupManager
import com.sonothamin.meowlaundry.print.PrintDispatcher
import com.sonothamin.meowlaundry.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled DI container. The app is small enough that a dependency-injection
 * framework would add more ceremony than it saves.
 */
class MeowLaundryApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var photoStore: PhotoStore
        private set
    lateinit var repository: ClosetRepository
        private set
    lateinit var printPreferences: PrintPreferences
        private set
    lateinit var appPreferences: AppPreferences
        private set
    lateinit var backupManager: BackupManager
        private set
    lateinit var printDispatcher: PrintDispatcher
        private set

    /**
     * One-shot: set right before navigating to the Archive tab (e.g. from the Closet screen's
     * "Lost" tile) so Archive opens pre-filtered. Read-and-cleared by whoever constructs
     * ArchiveViewModel next; a plain field is enough since it's only ever touched on the main
     * thread during navigation.
     */
    var pendingArchiveReasonFilter: ArchiveReason? = null

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        photoStore = PhotoStore(this)
        repository = ClosetRepository(database.clothingDao(), database.laundryDao(), photoStore, database.photoDao())
        printPreferences = PrintPreferences(this)
        appPreferences = AppPreferences(this)
        backupManager = BackupManager(this, repository)
        printDispatcher = PrintDispatcher(this, printPreferences)

        Reminders.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            repository.migrateLegacyLost()
            Reminders.sync(this@MeowLaundryApp)
        }
    }
}
