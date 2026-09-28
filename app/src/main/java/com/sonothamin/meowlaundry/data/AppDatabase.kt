package com.sonothamin.meowlaundry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClothingItem::class, LaundryTicket::class, LaundryTicketItem::class, ClothingItemPhoto::class],
    version = 5,
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
         * v4 -> v5: article quantities. Every existing row is one unit, so nothing the person has
         * already entered changes meaning: a ticket row that was "returned" is 1 of 1 returned, a
         * "lost" one 1 of 1 lost, and the article's cached counts are recomputed from those rows.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN atLaundryQuantity INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN lostQuantity INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE laundry_ticket_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE laundry_ticket_items ADD COLUMN returnedQuantity INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE laundry_ticket_items ADD COLUMN lostQuantity INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE laundry_ticket_items SET returnedQuantity = CASE WHEN returned = 1 THEN 1 ELSE 0 END")
                db.execSQL("UPDATE laundry_ticket_items SET lostQuantity = CASE WHEN lost = 1 THEN 1 ELSE 0 END")
                db.execSQL(
                    """
                    UPDATE clothing_items SET
                      lostQuantity = COALESCE((SELECT SUM(lostQuantity) FROM laundry_ticket_items WHERE clothingItemId = clothing_items.id), 0),
                      atLaundryQuantity = COALESCE((SELECT SUM(quantity - returnedQuantity - lostQuantity)
                                                    FROM laundry_ticket_items
                                                    WHERE clothingItemId = clothing_items.id AND returnedQuantity + lostQuantity < quantity), 0)
                    """.trimIndent()
                )
            }
        }

        /**
         * Older (pre-v4) databases are still wiped on upgrade instead of crashing; from v4 on there
         * are real migrations so the person's closet survives an update. Settings -> Backup is
         * still the safety net.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
