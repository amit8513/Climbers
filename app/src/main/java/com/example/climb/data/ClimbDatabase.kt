package com.example.climb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClimbEntity::class], version = 1, exportSchema = true)
abstract class ClimbDatabase : RoomDatabase() {
    abstract fun climbDao(): ClimbDao

    companion object {
        @Volatile
        private var instance: ClimbDatabase? = null

        fun getInstance(context: Context): ClimbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, ClimbDatabase::class.java, "climb.db")
                    .build()
                    .also { instance = it }
            }
    }
}
