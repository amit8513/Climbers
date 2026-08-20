package com.example.climb.navigation

import com.example.climb.clubs.AttemptSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for [parseAttemptSourceArg]'s fallback-safety requirement: old restored
 * navigation state, or a missing/malformed `attemptSource` nav arg, must resolve to
 * [AttemptSource.LEGACY_UNKNOWN] rather than throwing or silently becoming
 * [AttemptSource.MANUAL_LOG] (which specifically means "no video at all" and is never correct for
 * any of these call sites — see `AttemptSource`'s own doc comment).
 */
class AttemptSourceArgParsingTest {

    @Test
    fun `null arg resolves to LEGACY_UNKNOWN`() {
        assertEquals(AttemptSource.LEGACY_UNKNOWN, parseAttemptSourceArg(null))
    }

    @Test
    fun `blank arg resolves to LEGACY_UNKNOWN`() {
        assertEquals(AttemptSource.LEGACY_UNKNOWN, parseAttemptSourceArg(""))
    }

    @Test
    fun `garbage unparseable arg resolves to LEGACY_UNKNOWN, not a crash`() {
        assertEquals(AttemptSource.LEGACY_UNKNOWN, parseAttemptSourceArg("NOT_A_REAL_ENUM_VALUE"))
    }

    @Test
    fun `a stale enum name from an older app version resolves to LEGACY_UNKNOWN`() {
        // Simulates restored/deep-linked navigation state referencing an AttemptSource value that
        // existed in a previous build but has since been renamed or removed.
        assertEquals(AttemptSource.LEGACY_UNKNOWN, parseAttemptSourceArg("SOME_REMOVED_LEGACY_VALUE"))
    }

    @Test
    fun `every real AttemptSource value round-trips through its own name`() {
        AttemptSource.entries.forEach { source ->
            assertEquals(source, parseAttemptSourceArg(source.name))
        }
    }
}
