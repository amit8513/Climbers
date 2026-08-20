package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the corrected security shape: the raw NFC tag UID must never be persisted
 * or exposed as a plain field (it would be a clonable physical-access credential if readable off
 * a Firestore document) — only a one-way hash of it, plus real enabled/revoked state.
 */
class WristbandCredentialSecurityTest {

    @Test
    fun `has no raw tagUid field, only a hash`() {
        val fieldNames = WristbandCredential::class.java.declaredFields.map { it.name }
        assertFalse("must not have a raw tagUid field", fieldNames.contains("tagUid"))
        assertTrue("must have a tagUidHash field instead", fieldNames.contains("tagUidHash"))
    }

    @Test
    fun `id is not derived from the tag value`() {
        val credentialA = WristbandCredential(id = 1L, organizationId = 1L, userId = "u1", tagUidHash = "hash-a", issuedAt = 0L)
        val credentialB = WristbandCredential(id = 2L, organizationId = 1L, userId = "u2", tagUidHash = "hash-a", issuedAt = 0L)
        // Two different credentials could in principle share a hash only in a collision, but the
        // real point of this test is structural: id is an independent Long, never derived from
        // tagUidHash by construction.
        assertEquals(1L, credentialA.id)
        assertEquals(2L, credentialB.id)
    }

    @Test
    fun `carries real enabled-revoked state`() {
        val active = WristbandCredential(id = 1L, organizationId = 1L, userId = "u1", tagUidHash = "hash", issuedAt = 0L)
        assertTrue(active.enabled)
        val revoked = active.copy(enabled = false, revokedAt = 123L)
        assertFalse(revoked.enabled)
        assertEquals(123L, revoked.revokedAt)
    }
}
