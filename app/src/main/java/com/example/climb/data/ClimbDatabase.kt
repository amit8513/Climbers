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
    entities = [
        ClimbEntity::class, ClimbAttemptEntity::class, ClimbAnalysisEntity::class,
    ],
    version = 13,
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

        // Additive: the new Clubs/Organizations tables (entirely new, no existing table touched
        // by their creation), plus nullable route-context columns on `climbs` and
        // `climb_attempts` — every existing row gets NULL for all five, which is exactly the
        // "no gym" state for a normal/outdoor climber, not a special case to migrate around.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `organizations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `organization_memberships` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `organizationId` INTEGER NOT NULL,
                        `userId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `joinedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `venues` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `organizationId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `address` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `zones` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `venueId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `zoneId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `vGrade` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `retiredAt` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `route_versions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `routeId` INTEGER NOT NULL,
                        `setterUserId` TEXT NOT NULL,
                        `versionNumber` INTEGER NOT NULL,
                        `colorHex` INTEGER,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL("ALTER TABLE climbs ADD COLUMN organizationId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climbs ADD COLUMN venueId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climbs ADD COLUMN zoneId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climbs ADD COLUMN routeId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climbs ADD COLUMN routeVersionId INTEGER DEFAULT NULL")

                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN organizationId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN venueId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN zoneId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN routeId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN routeVersionId INTEGER DEFAULT NULL")
            }
        }

        // Additive: a club is no longer joined or created instantly — this just adds the two
        // request/approval tables the new flow needs. No existing table is touched.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `organization_join_requests` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `organizationId` INTEGER NOT NULL,
                        `userId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `requestedAt` INTEGER NOT NULL,
                        `decidedAt` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `club_creation_requests` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `requesterUid` TEXT NOT NULL,
                        `message` TEXT,
                        `status` TEXT NOT NULL,
                        `requestedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        // The Clubs/Organizations tables (created by MIGRATION_7_8/8_9) moved to Firestore so
        // club data is shared across phones instead of stuck on whichever device created it —
        // see ClubRepository. Room simply stops tracking those tables here; nothing about them
        // is dropped or touched, so any local rows just become inert leftovers, never read again.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: only Room's tracked-entity set changed, not any table this app still uses.
            }
        }

        // Additive: a successful "Calibrate on this hold" result, so reopening a climb restores
        // it instead of requiring the user to tap-to-calibrate again every time.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climbs ADD COLUMN calibratedColorModelJson TEXT DEFAULT NULL")
            }
        }

        // Additive: four nullable columns on climb_attempts for the gym-camera automatic-route-
        // attribution work (Phase 1 — schema only, nothing writes non-null values here yet).
        // `attemptSource` is a plain enum column (Room stores it as TEXT via `.name`, same as
        // `wallType`/`visibility` already do on this table, so no TypeConverter is needed).
        // Internal (not private) so ClimbDatabaseMigrationTest can exercise it directly.
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN attemptSource TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN wallId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN wallCalibrationId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE climb_attempts ADD COLUMN captureSessionId TEXT DEFAULT NULL")
            }
        }

        // Additive: `attemptSource` on `climbs` itself (the equivalent column already exists on
        // `climb_attempts` since MIGRATION_11_12) — every existing row gets NULL, meaning "logged
        // before source tracking existed, or genuinely unknown provenance," same LEGACY_UNKNOWN
        // spirit as that column, not something that needs backfilling.
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE climbs ADD COLUMN attemptSource TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): ClimbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, ClimbDatabase::class.java, "climb.db")
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
