package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WristbandCredentialHashing] must be a keyed HMAC-SHA256, not a bare unsalted hash: same
 * input+key is deterministic, but the key materially changes the output (so an attacker without
 * the server secret can't brute-force/rainbow-table the limited-entropy tag UID space), and
 * distinct inputs under the same key don't trivially collide.
 */
class WristbandCredentialHashingTest {

    private val keyA = "server-secret-key-a".toByteArray(Charsets.UTF_8)
    private val keyB = "server-secret-key-b".toByteArray(Charsets.UTF_8)

    @Test
    fun `same input and key produce the same output (deterministic)`() {
        val first = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D0", keyA)
        val second = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D0", keyA)
        assertEquals(first, second)
    }

    @Test
    fun `same input with two different keys produces different output`() {
        val withKeyA = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D0", keyA)
        val withKeyB = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D0", keyB)
        assertNotEquals(withKeyA, withKeyB)
    }

    @Test
    fun `two different inputs with the same key produce different output`() {
        val first = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D0", keyA)
        val second = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D1", keyA)
        assertNotEquals(first, second)
    }

    @Test
    fun `output is lowercase hex of the expected SHA-256 length`() {
        val result = WristbandCredentialHashing.hmacSha256Hex("04A3B2C1D0", keyA)
        assertEquals(64, result.length)
        assertTrue(result.all { it in "0123456789abcdef" })
    }
}
