package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [RouteColorHex]'s channel extraction against known values from `:app`'s
 * `com.example.climb.data.RouteColor` — the enum this packed-Long convention is deliberately
 * identical to (see [RouteVisionProfileEntity.routeColorHex]'s doc comment). Values below are
 * copied from `RouteColor` and their expected channels hand-computed from the hex literal, not
 * guessed:
 * - RED = 0xFFE53935 -> alpha=0xFF=255, red=0xE5=229, green=0x39=57, blue=0x35=53
 * - ORANGE = 0xFFFB8C00 -> alpha=0xFF=255, red=0xFB=251, green=0x8C=140, blue=0x00=0
 * - BLUE = 0xFF1E88E5 -> alpha=0xFF=255, red=0x1E=30, green=0x88=136, blue=0xE5=229
 */
class RouteColorHexTest {

    @Test
    fun `extracts channels from RouteColor RED`() {
        val argb = 0xFFE53935L
        assertEquals(255, RouteColorHex.alpha(argb))
        assertEquals(229, RouteColorHex.red(argb))
        assertEquals(57, RouteColorHex.green(argb))
        assertEquals(53, RouteColorHex.blue(argb))
    }

    @Test
    fun `extracts channels from RouteColor ORANGE`() {
        val argb = 0xFFFB8C00L
        assertEquals(255, RouteColorHex.alpha(argb))
        assertEquals(251, RouteColorHex.red(argb))
        assertEquals(140, RouteColorHex.green(argb))
        assertEquals(0, RouteColorHex.blue(argb))
    }

    @Test
    fun `extracts channels from RouteColor BLUE`() {
        val argb = 0xFF1E88E5L
        assertEquals(255, RouteColorHex.alpha(argb))
        assertEquals(30, RouteColorHex.red(argb))
        assertEquals(136, RouteColorHex.green(argb))
        assertEquals(229, RouteColorHex.blue(argb))
    }

    @Test
    fun `isFullyOpaque is true when alpha is 0xFF`() {
        val argb = 0xFFE53935L
        assertTrue(RouteColorHex.isFullyOpaque(argb))
    }

    @Test
    fun `isFullyOpaque is false when alpha is not 0xFF`() {
        // Constructed non-opaque value: alpha=0x80, red=0xE5, green=0x39, blue=0x35.
        val argb = 0x80E53935L
        assertEquals(128, RouteColorHex.alpha(argb))
        assertFalse(RouteColorHex.isFullyOpaque(argb))
    }
}
