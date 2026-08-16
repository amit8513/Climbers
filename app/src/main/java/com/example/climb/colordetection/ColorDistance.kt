package com.example.climb.colordetection

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Circular hue distance in degrees — the shortest way around the 360° wheel, always in [0, 180].
 * E.g. hue 359° vs hue 2° is a 3° distance, not 357°. Mirrors the same wraparound fix this
 * project's original full-frame hue-isolation shader (`min(hueDiff, 1.0 - hueDiff)`, since
 * replaced by real per-object detection) used, as a real, independently-tested pure-Kotlin
 * utility. */
fun circularHueDistance(hueDegreesA: Float, hueDegreesB: Float): Float {
    val diff = abs(hueDegreesA - hueDegreesB) % 360f
    return if (diff > 180f) 360f - diff else diff
}

/** A Lab-space perceptual color-distance metric. Lower = more similar; 0 = identical. */
fun interface ColorDistanceMetric {
    fun distance(a: LabColor, b: LabColor): Double
}

/** Plain Euclidean distance in L*a*b* space — fast, but not perceptually uniform (famously
 * over/under-weights some hue regions, which is exactly the kind of thing that can let a
 * borderline orange sneak in "close enough" to red). Kept as a cheap fallback option. */
object Cie76DistanceMetric : ColorDistanceMetric {
    override fun distance(a: LabColor, b: LabColor): Double {
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return sqrt(dl * dl + da * da + db * db)
    }
}

/**
 * CIEDE2000 perceptual color distance — the primary metric for this module. Implements the
 * formula from Sharma, Wu & Dalal, "The CIEDE2000 Color-Difference Formula: Implementation Notes,
 * Supplementary Test Data, and Mathematical Observations" (Color Research & Application, 2005),
 * with the standard default weighting factors kL = kC = kH = 1. This is meaningfully more
 * expensive than [Cie76DistanceMetric] (several trig calls per comparison), which is acceptable
 * here since detection runs post-hoc on a reviewed video, not live during recording.
 */
object Ciede2000DistanceMetric : ColorDistanceMetric {
    private fun deg2rad(deg: Double) = deg * PI / 180.0
    private fun rad2deg(rad: Double) = rad * 180.0 / PI

    override fun distance(a: LabColor, b: LabColor): Double {
        val (l1, a1, b1) = a
        val (l2, a2, b2) = b

        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = (c1 + c2) / 2.0

        val cBar7 = cBar.pow(7)
        val g = 0.5 * (1 - sqrt(cBar7 / (cBar7 + 25.0.pow(7))))

        val a1Prime = a1 * (1 + g)
        val a2Prime = a2 * (1 + g)

        val c1Prime = sqrt(a1Prime * a1Prime + b1 * b1)
        val c2Prime = sqrt(a2Prime * a2Prime + b2 * b2)

        fun huePrime(aPrime: Double, bChannel: Double): Double {
            if (aPrime == 0.0 && bChannel == 0.0) return 0.0
            var deg = rad2deg(atan2(bChannel, aPrime))
            if (deg < 0) deg += 360.0
            return deg
        }

        val h1Prime = huePrime(a1Prime, b1)
        val h2Prime = huePrime(a2Prime, b2)

        val deltaLPrime = l2 - l1
        val deltaCPrime = c2Prime - c1Prime

        val deltahPrime: Double = when {
            c1Prime * c2Prime == 0.0 -> 0.0
            abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
            h2Prime - h1Prime > 180.0 -> h2Prime - h1Prime - 360.0
            else -> h2Prime - h1Prime + 360.0
        }
        val deltaHPrime = 2 * sqrt(c1Prime * c2Prime) * sin(deg2rad(deltahPrime) / 2.0)

        val lBarPrime = (l1 + l2) / 2.0
        val cBarPrime = (c1Prime + c2Prime) / 2.0

        val hBarPrime: Double = when {
            c1Prime * c2Prime == 0.0 -> h1Prime + h2Prime
            abs(h1Prime - h2Prime) <= 180.0 -> (h1Prime + h2Prime) / 2.0
            h1Prime + h2Prime < 360.0 -> (h1Prime + h2Prime + 360.0) / 2.0
            else -> (h1Prime + h2Prime - 360.0) / 2.0
        }

        val t = 1 -
            0.17 * cos(deg2rad(hBarPrime - 30.0)) +
            0.24 * cos(deg2rad(2 * hBarPrime)) +
            0.32 * cos(deg2rad(3 * hBarPrime + 6.0)) -
            0.20 * cos(deg2rad(4 * hBarPrime - 63.0))

        val deltaTheta = 30.0 * exp(-((hBarPrime - 275.0) / 25.0).pow(2))
        val cBarPrime7 = cBarPrime.pow(7)
        val rc = 2 * sqrt(cBarPrime7 / (cBarPrime7 + 25.0.pow(7)))

        val sl = 1 + (0.015 * (lBarPrime - 50).pow(2)) / sqrt(20 + (lBarPrime - 50).pow(2))
        val sc = 1 + 0.045 * cBarPrime
        val sh = 1 + 0.015 * cBarPrime * t

        val rt = -sin(deg2rad(2 * deltaTheta)) * rc

        val kl = 1.0
        val kc = 1.0
        val kh = 1.0

        val termL = deltaLPrime / (kl * sl)
        val termC = deltaCPrime / (kc * sc)
        val termH = deltaHPrime / (kh * sh)

        return sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH)
    }
}
