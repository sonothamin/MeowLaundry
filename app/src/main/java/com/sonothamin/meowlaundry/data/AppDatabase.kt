package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ClothingItem::class, LaundryTicket::class, LaundryTicketItem::class, ClothingItemPhoto::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao
    abstract fun laundryDao(): LaundryDao
    abstract fun photoDao(): ClothingPhotoDao

    companion object {
        private const val DB_NAME = "meowlaundry.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Version 5 is the first public schema. Only the pre-release dev versions (1-4) may be
         * wiped on upgrade; from 5 onward every schema change MUST ship a Migration (schemas are
         * exported to app/schemas/ - commit them), otherwise Room throws instead of losing data.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
                    .build()
                    .also { instance = it }
            }
    }
}
