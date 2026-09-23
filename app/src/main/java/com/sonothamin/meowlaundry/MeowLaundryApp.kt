package com.sonothamin.meowlaundry

import android.app.Application
import com.sonothamin.meowlaundry.data.AppDatabase
import com.sonothamin.meowlaundry.data.ClosetRepository
import com.sonothamin.meowlaundry.data.PhotoStore
import com.sonothamin.meowlaundry.data.PrintPreferences
import com.sonothamin.meowlaundry.data.backup.BackupManager

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
    lateinit var backupManager: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        photoStore = PhotoStore(this)
        repository = ClosetRepository(database.clothingDao(), database.laundryDao(), photoStore)
        printPreferences = PrintPreferences(this)
        backupManager = BackupManager(this, repository)
    }
}
