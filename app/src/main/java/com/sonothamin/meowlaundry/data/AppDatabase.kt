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
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao
    abstract fun laundryDao(): LaundryDao
    abstract fun photoDao(): ClothingPhotoDao

    companion object {
        private const val DB_NAME = "meowlaundry.db"

        /**
         * Adds archiving to [ClothingItem] and the multi-photo table. Existing single-photo
         * garments get a matching row in clothing_item_photos so the new gallery UI has
         * something to show without any data loss.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN archiveReason TEXT")
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN archiveNotes TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS clothing_item_photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clothingItemId INTEGER NOT NULL,
                        path TEXT NOT NULL,
                        isPrimary INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(clothingItemId) REFERENCES clothing_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clothing_item_photos_clothingItemId ON clothing_item_photos(clothingItemId)")
                db.execSQL(
                    """
                    INSERT INTO clothing_item_photos (clothingItemId, path, isPrimary, sortOrder, createdAt)
                    SELECT id, imagePath, 1, 0, ${System.currentTimeMillis()}
                    FROM clothing_items
                    WHERE imagePath IS NOT NULL AND imagePath != ''
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
