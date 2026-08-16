package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldComponentDetectorTest {

    private val gray = RgbColor(128, 128, 128) // low-saturation background: not a candidate for any chromatic RouteColor
    private val red = RgbColor.fromArgbHex(RouteColor.RED.hex)
    private val orange = RgbColor.fromArgbHex(RouteColor.ORANGE.hex)

    /**
     * Mandatory image-level RED-vs-ORANGE regression: a red hold-sized region and a separately
     * placed orange hold-sized region in the same frame, detected with a RED target model. Only
     * the red region may survive as a candidate hold.
     */
    @Test
    fun `detecting with a RED target model finds the red region and rejects the orange region`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(20, 20, 50, 50, red) // 30x30 red square
        buffer.fillRect(120, 120, 150, 150, orange) // 30x30 orange square, far away

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))

        assertEquals(1, holds.size)
        val hold = holds.single()
        // Centroid should land inside the red square (20..49 in both axes), nowhere near the orange one.
        assertTrue(hold.centroid.x in 20.0..49.0)
        assertTrue(hold.centroid.y in 20.0..49.0)
        assertEquals(900, hold.area) // 30x30, no gray pixels leaked in
    }

    @Test
    fun `detecting with an ORANGE target model finds the orange region and rejects the red region`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(20, 20, 50, 50, red)
        buffer.fillRect(120, 120, 150, 150, orange)

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.ORANGE))

        assertEquals(1, holds.size)
        val hold = holds.single()
        assertTrue(hold.centroid.x in 120.0..149.0)
        assertTrue(hold.centroid.y in 120.0..149.0)
    }

    @Test
    fun `minimum-size rejection keeps a real hold-sized region and drops noise specks`() {
        val buffer = PixelBuffer.filled(200, 200, gray) // 40,000px frame; min area = 0.0008 * 40000 = 32px
        buffer.fillRect(80, 80, 105, 105, red) // 25x25 = 625px, well above the cutoff

        // Scatter isolated 1-3px red noise specks far from the real region, each well under 32px.
        buffer.setPixel(5, 5, red)
        buffer.fillRect(10, 150, 12, 151, red) // 2x1 = 2px
        buffer.fillRect(170, 10, 173, 12, red) // 3x2 = 6px
        buffer.setPixel(190, 190, red)

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))

        assertEquals(1, holds.size)
        assertEquals(625, holds.single().area)
    }

    @Test
    fun `isolated single-pixel chalk noise inside a hold does not fragment it`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(50, 50, 70, 70, red) // 20x20 = 400px

        // A handful of scattered, non-adjacent interior pixels desaturated by "chalk" — isolated
        // points, not a connected line, so the surrounding red pixels can still route around them.
        buffer.setPixel(55, 55, gray)
        buffer.setPixel(60, 62, gray)
        buffer.setPixel(65, 58, gray)

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))

        assertEquals("isolated chalk specks should not split one hold into multiple components", 1, holds.size)
        assertEquals(400 - 3, holds.single().area)
    }

    /**
     * Real, documented limitation (not swept under the rug): a *connected* line of non-candidate
     * pixels running fully across a hold's width — e.g. a chalk smear or specular glare band — cuts
     * strict 4-connected labeling clean in two. Isolated chalk points (previous test) survive fine
     * because red pixels can still route around a single point; a full-width bridge, they can't.
     * This is exactly the gap boundary-refinement / morphological-closing work is meant to close —
     * flagging it now rather than pretending Phase 3's pure color-distance seeding already handles it.
     */
    @Test
    fun `a full-width non-candidate band across a hold does fragment it under strict labeling`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(50, 50, 70, 70, red) // 20x20 = 400px
        buffer.fillRect(50, 59, 70, 61, gray) // a 2px-tall gray band spanning the full width, cutting the square in half

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))

        assertEquals(
            "documented limitation: a full-width cutting band fragments one physical hold into two components",
            2,
            holds.size,
        )
    }

    @Test
    fun `an empty frame with no matching pixels returns no holds`() {
        val buffer = PixelBuffer.filled(50, 50, gray)
        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))
        assertTrue(holds.isEmpty())
    }

    @Test
    fun `achromatic BLACK target model finds a dark region using luminance, not hue`() {
        val black = RgbColor.fromArgbHex(RouteColor.BLACK.hex)
        val buffer = PixelBuffer.filled(100, 100, gray)
        buffer.fillRect(10, 10, 30, 30, black) // 20x20 = 400px

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.BLACK))

        assertEquals(1, holds.size)
        assertEquals(400, holds.single().area)
    }

    @Test
    fun `confidence is clamped between 0 and 1 for every detected hold`() {
        val buffer = PixelBuffer.filled(200, 200, gray)
        buffer.fillRect(20, 20, 50, 50, red)

        val holds = HoldComponentDetector.detectCandidates(buffer, RouteColorProfiles.defaultFor(RouteColor.RED))
        assertTrue(holds.isNotEmpty())
        holds.forEach { assertTrue(it.confidence in 0.0..1.0) }
    }
}
