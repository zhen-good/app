package com.example.thelastone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// data/local/AppDatabase.kt
@Database(
    entities = [MessageEntity::class, SavedPlaceEntity::class],
    version = 3, // ← bump 2 -> 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun savedPlaceDao(): SavedPlaceDao

    companion object {
        // 1 -> 2: 建表
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_places(
                        placeId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        address TEXT,
                        lat REAL NOT NULL,
                        lng REAL NOT NULL,
                        rating REAL,
                        userRatingsTotal INTEGER,
                        photoUrl TEXT,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_saved_places_placeId ON saved_places(placeId)")
            }
        }

        // 2 -> 3: 加三個欄位
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_places ADD COLUMN openingHoursJson TEXT")
                db.execSQL("ALTER TABLE saved_places ADD COLUMN openNow INTEGER")         // Room 用 INTEGER 表示 Boolean（0/1/NULL）
                db.execSQL("ALTER TABLE saved_places ADD COLUMN openStatusText TEXT")
            }
        }
    }
}