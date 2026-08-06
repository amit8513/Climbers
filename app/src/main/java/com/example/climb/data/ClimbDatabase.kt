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
    version = 7,
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

        // Additive: persisted color-isolation tuning per climb, and a link from an analysis
        // attempt back to the climb it was run on (when analyzing an already-logged climb).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climbs ADD COLUMN hueOffsetDegrees REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE climbs ADD COLUMN hueToleranceDegrees REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN sourceClimbId INTEGER DEFAULT NULL")
            }
        }

        // Additive: metrics/events/coaching-tip JSON columns, added alongside the metrics +
        // deterministic coaching engine phase of the pose-analysis feature.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN metricsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN eventsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN tipsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        // Additive: sharing visibility per climb, defaulting to private — sharing is opt-in,
        // never on by default for existing rows.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climbs ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE'")
            }
        }

        // Additive: phase timeline + six-category performance scoring columns, added alongside
        // the evidence-based performance report rework.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN phasesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN categoryScoresJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN overallScore INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN overallConfidence REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_analyses ADD COLUMN scoringConfigVersion INTEGER DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): ClimbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, ClimbDatabase::class.java, "climb.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
    }
}
