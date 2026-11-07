package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Trip::class,
        PnrStatusCheck::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(TripTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun pnrStatusCheckDao(): PnrStatusCheckDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to trips table
                db.execSQL("ALTER TABLE trips ADD COLUMN currentStatus TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE trips ADD COLUMN lastCheckedAt TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE trips ADD COLUMN lastRawSmsReply TEXT DEFAULT NULL")

                // Create pnr_status_checks table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pnr_status_checks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tripId` INTEGER NOT NULL,
                        `pnr` TEXT NOT NULL,
                        `requestedAt` INTEGER NOT NULL,
                        `replyReceivedAt` INTEGER DEFAULT NULL,
                        `rawReply` TEXT DEFAULT NULL,
                        `parsedStatus` TEXT DEFAULT NULL,
                        `success` INTEGER NOT NULL,
                        `isManual` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                // Create indices for fast lookup
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pnr_status_checks_tripId` ON `pnr_status_checks` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pnr_status_checks_pnr` ON `pnr_status_checks` (`pnr`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN passengers TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pnr_status_checks ADD COLUMN method TEXT NOT NULL DEFAULT 'PREFILLED_SMS'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "travel_track.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
