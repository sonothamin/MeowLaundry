package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ClothingItem::class, LaundryTicket::class, LaundryTicketItem::class, ClothingItemPhoto::class],
    version = 3,
    exportSchema = false,
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
         * Pre-alpha: there are no migrations. Any schema change wipes the local database on the
         * next launch instead of crashing. Use Settings -> Backup if the data matters.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
