package com.example.climb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClimbEntity::class], version = 2, exportSchema = true)
abstract class ClimbDatabase : RoomDatabase() {
    abstract fun climbDao(): ClimbDao

    companion object {
        @Volatile
        private var instance: ClimbDatabase? = null

        // Existing rows predate per-user ownership and can't be attributed to a real uid, so
        // they get an empty userId — which never matches a signed-in uid — rather than guessing
        // an owner or deleting anything. They just stop appearing to anyone.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climbs ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): ClimbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, ClimbDatabase::class.java, "climb.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
