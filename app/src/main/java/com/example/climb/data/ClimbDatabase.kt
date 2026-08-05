package com.example.climb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.climb.analysis.AnalysisDao
import com.example.climb.analysis.ClimbAnalysisEntity
import com.example.climb.analysis.ClimbAttemptEntity

@Database(
    entities = [ClimbEntity::class, ClimbAttemptEntity::class, ClimbAnalysisEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ClimbDatabase : RoomDatabase() {
    abstract fun climbDao(): ClimbDao
    abstract fun analysisDao(): AnalysisDao

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

        // Additive only — the existing `climbs` table is untouched, this just adds the new
        // pose-analysis tables introduced alongside the AI video-analysis feature.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `climb_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `videoPath` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `vGrade` INTEGER,
                        `wallType` TEXT NOT NULL,
                        `attemptNumber` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `flash` INTEGER NOT NULL,
                        `routeName` TEXT,
                        `gymName` TEXT,
                        `notes` TEXT NOT NULL,
                        `visibility` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `climb_analyses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `attemptId` INTEGER NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `algorithmVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `confidence` REAL,
                        `climbStartMs` INTEGER,
                        `climbEndMs` INTEGER,
                        `videoDurationMs` INTEGER,
                        `videoWidth` INTEGER,
                        `videoHeight` INTEGER,
                        `poseFramesJson` TEXT NOT NULL,
                        `failureReason` TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): ClimbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, ClimbDatabase::class.java, "climb.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
