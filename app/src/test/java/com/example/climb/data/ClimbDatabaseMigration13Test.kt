package com.example.climb.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Verifies MIGRATION_12_13 (the Phase 1.2 addition of `attemptSource` to `climbs` itself) directly
 * against a minimal recording [SupportSQLiteDatabase] proxy — same approach as
 * [ClimbDatabaseMigrationTest] for MIGRATION_11_12 (see that file's own doc comment for why this
 * project uses a recording-proxy shape test rather than a real Room `MigrationTestHelper` run).
 */
class ClimbDatabaseMigration13Test {

    @Test
    fun `MIGRATION_12_13 only adds a nullable attemptSource column to climbs`() {
        val executedSql = mutableListOf<String>()
        val db = recordingDatabase(executedSql)

        ClimbDatabase.MIGRATION_12_13.migrate(db)

        assertTrue("must execute at least one ALTER TABLE statement", executedSql.isNotEmpty())
        executedSql.forEach { sql ->
            assertTrue(
                "every statement must be an additive ALTER TABLE climbs ADD COLUMN: $sql",
                sql.trim().startsWith("ALTER TABLE climbs ADD COLUMN"),
            )
            assertTrue(
                "every added column must default to NULL (additive, never data-lossy): $sql",
                sql.contains("DEFAULT NULL"),
            )
        }

        val addedColumns = executedSql.map { it.substringAfter("ADD COLUMN").trim().substringBefore(" ") }.toSet()
        assertEquals(setOf("attemptSource"), addedColumns)
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
