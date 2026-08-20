package com.example.climb.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Verifies MIGRATION_11_12 (the Phase 1 gym-camera schema addition to `climb_attempts`) directly
 * against a minimal recording [SupportSQLiteDatabase] proxy, rather than a full Room
 * open-database-and-read-back cycle.
 *
 * This project has no Robolectric/`room-testing` setup, and no prior migration (there are ten)
 * has ever had a dedicated test either — real end-to-end migration verification (Room's own
 * `MigrationTestHelper`) needs a device/emulator, which isn't reliably available in this
 * environment. This test instead proves the migration's *shape* is safe: every statement it
 * executes must be an additive `ALTER TABLE climb_attempts ADD COLUMN` with a NULL default,
 * touching no other table and no existing column — exactly the same additive/nullable-only
 * pattern this file's own doc comments establish for every migration before it. A real on-device
 * run (open a pre-migration database, run the migration, read an old row back) is the natural
 * follow-up once a device is reachable.
 */
class ClimbDatabaseMigrationTest {

    @Test
    fun `MIGRATION_11_12 only adds nullable columns to climb_attempts`() {
        val executedSql = mutableListOf<String>()
        val db = recordingDatabase(executedSql)

        ClimbDatabase.MIGRATION_11_12.migrate(db)

        assertTrue("must execute at least one ALTER TABLE statement", executedSql.isNotEmpty())
        executedSql.forEach { sql ->
            assertTrue(
                "every statement must be an additive ALTER TABLE climb_attempts ADD COLUMN: $sql",
                sql.trim().startsWith("ALTER TABLE climb_attempts ADD COLUMN"),
            )
            assertTrue(
                "every added column must default to NULL (additive, never data-lossy): $sql",
                sql.contains("DEFAULT NULL"),
            )
        }

        val addedColumns = executedSql.map { it.substringAfter("ADD COLUMN").trim().substringBefore(" ") }.toSet()
        assertEquals(setOf("attemptSource", "wallId", "wallCalibrationId", "captureSessionId"), addedColumns)
    }

    /** A `java.lang.reflect.Proxy` implementing [SupportSQLiteDatabase] that records every
     * `execSQL(String)` call and no-ops everything else — sufficient for a migration that (as
     * every one in this file does) only ever calls `execSQL`. */
    private fun recordingDatabase(executedSql: MutableList<String>): SupportSQLiteDatabase {
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1 && args[0] is String) {
                executedSql += args[0] as String
                null
            } else {
                when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        }
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase
    }
}
