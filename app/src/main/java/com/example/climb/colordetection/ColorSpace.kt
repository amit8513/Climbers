package com.example.climb.colordetection

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min

/** CIE L*a*b* color, D65 illuminant. [l] in [0, 100]; [a]/[b] roughly [-128, 127] but unbounded. */
data class LabColor(val l: Double, val a: Double, val b: Double)

/** HSV color. [h] in degrees [0, 360); [s]/[v] in [0, 1]. */
data class HsvColor(val h: Float, val s: Float, val v: Float)

/** Plain 8-bit-per-channel RGB, no alpha. */
data class RgbColor(val r: Int, val g: Int, val b: Int) {
    init {
        require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB channels must be 0..255, got ($r, $g, $b)" }
    }

    companion object {
        /** Extracts RGB from an ARGB hex Long like [com.example.climb.data.RouteColor.hex]. */
        fun fromArgbHex(hex: Long): RgbColor = RgbColor(
            r = ((hex shr 16) and 0xFF).toInt(),
            g = ((hex shr 8) and 0xFF).toInt(),
            b = (hex and 0xFF).toInt(),
        )
    }
}

/**
 * Pure-Kotlin color-space conversions — deliberately NOT using `android.graphics.Color`. This
 * project's unit tests run as plain JVM tests with no Robolectric/`testOptions.unitTests`
 * mocking config (checked `app/build.gradle.kts` — there is no such block), so any real call into
 * `android.graphics.Color` would throw "Method ... not mocked" here. Keeping this module pure
 * Kotlin/JVM math means it's trivially unit-testable and has zero Android dependency.
 */
object ColorSpace {

    /** sRGB (D65) → CIE L*a*b*, via the standard RGB→linear→XYZ→Lab path. */
    fun rgbToLab(rgb: RgbColor): LabColor {
        // sRGB channels 0..255 -> linear 0..1 (inverse gamma / "companding").
        fun toLinear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }

        val r = toLinear(rgb.r)
        val g = toLinear(rgb.g)
        val b = toLinear(rgb.b)

        // Linear sRGB -> XYZ (sRGB/D65 matrix).
        val x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041

        // D65 reference white.
        val xn = 0.95047
        val yn = 1.0
        val zn = 1.08883

        fun f(t: Double): Double {
            val delta = 6.0 / 29.0
            return if (t > delta * delta * delta) cbrt(t) else t / (3 * delta * delta) + 4.0 / 29.0
        }

        val fx = f(x / xn)
        val fy = f(y / yn)
        val fz = f(z / zn)

        val l = 116 * fy - 16
        val a = 500 * (fx - fy)
        val bLab = 200 * (fy - fz)
        return LabColor(l, a, bLab)
    }

    /** sRGB → HSV. Standard hexcone conversion; hue always normalized to [0, 360). */
    fun rgbToHsv(rgb: RgbColor): HsvColor {
        val r = rgb.r / 255f
        val g = rgb.g / 255f
        val b = rgb.b / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        val v = maxC
        val s = if (maxC == 0f) 0f else delta / maxC

        var h = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta).mod(6f))
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (h < 0f) h += 360f
        if (h >= 360f) h -= 360f
        return HsvColor(h, s, v)
    }
}
