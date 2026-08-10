package com.example.climb.pose

import kotlin.math.abs

/**
 * One Euro Filter (Casiez, Roussel & Vogel, 2012) applied independently per coordinate, per
 * landmark, across a whole pose sequence. A plain fixed low-pass filter (e.g. a constant-alpha
 * EMA) would denoise slow, static positioning but also blur exactly the fast dynamic
 * moves/deadpoints this analysis most needs to keep sharp — One Euro's cutoff frequency rises
 * with how fast the signal is currently moving, so it smooths jitter during stillness while
 * barely touching genuine rapid movement.
 */
private class OneEuroFilter(
    private val minCutoffHz: Float = 1.0f,
    private val beta: Float = 0.3f,
    private val derivativeCutoffHz: Float = 1.0f,
) {
    private var initialized = false
    private var previousValue = 0f
    private var previousDerivative = 0f
    private var previousTimestampSeconds = 0f

    fun filter(value: Float, timestampSeconds: Float): Float {
        if (!initialized) {
            initialized = true
            previousValue = value
            previousDerivative = 0f
            previousTimestampSeconds = timestampSeconds
            return value
        }

        val dt = (timestampSeconds - previousTimestampSeconds).coerceAtLeast(1e-6f)

        val derivative = (value - previousValue) / dt
        val derivativeAlpha = smoothingFactor(derivativeCutoffHz, dt)
        val smoothedDerivative = derivativeAlpha * derivative + (1 - derivativeAlpha) * previousDerivative

        val adaptiveCutoff = minCutoffHz + beta * abs(smoothedDerivative)
        val alpha = smoothingFactor(adaptiveCutoff, dt)
        val smoothedValue = alpha * value + (1 - alpha) * previousValue

        previousValue = smoothedValue
        previousDerivative = smoothedDerivative
        previousTimestampSeconds = timestampSeconds
        return smoothedValue
    }

    private fun smoothingFactor(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoffHz)
        return 1f / (1f + tau / dt)
    }
}

private data class LandmarkFilters(val x: OneEuroFilter, val y: OneEuroFilter, val z: OneEuroFilter)

/**
 * Smooths a pose sequence landmark-by-landmark, coordinate-by-coordinate, before any metric or
 * event detector sees it. Unreliable frames pass through untouched and reset that landmark's
 * filters — blending a real tracked position with a wrong/occluded one would just spread the
 * error around rather than remove it. A gap larger than [maxGapMs] between reliable frames also
 * resets every filter, matching the same don't-blend-across-gaps rule already used by
 * [com.example.climb.analysis.metrics.smoothVelocities].
 */
fun smoothPoseSequence(frames: List<PoseFrame>, maxGapMs: Long = 500L): List<PoseFrame> {
    if (frames.isEmpty()) return frames

    val filtersByLandmark = HashMap<PoseLandmarkType, LandmarkFilters>()
    var lastReliableTimestampMs: Long? = null

    return frames.map { frame ->
        if (!frame.isReliable) {
            // Clear immediately rather than just forgetting lastReliableTimestampMs: leaving that
            // null would make the *next* reliable frame's gap check below default to "no gap"
            // (null?.let{} ?: false), so filters would keep blending across the unreliable frame
            // instead of resetting — exactly the wrong/occluded-position blending this is meant
            // to avoid.
            filtersByLandmark.clear()
            lastReliableTimestampMs = null
            return@map frame
        }

        val gapTooLarge = lastReliableTimestampMs?.let { frame.timestampMs - it > maxGapMs } ?: false
        if (gapTooLarge) filtersByLandmark.clear()
        lastReliableTimestampMs = frame.timestampMs

        val timestampSeconds = frame.timestampMs / 1000f
        val smoothedLandmarks = frame.landmarks.map { landmark ->
            val filters = filtersByLandmark.getOrPut(landmark.type) {
                LandmarkFilters(OneEuroFilter(), OneEuroFilter(), OneEuroFilter())
            }
            landmark.copy(
                normalizedX = filters.x.filter(landmark.normalizedX, timestampSeconds),
                normalizedY = filters.y.filter(landmark.normalizedY, timestampSeconds),
                normalizedZ = filters.z.filter(landmark.normalizedZ, timestampSeconds),
            )
        }
        frame.copy(landmarks = smoothedLandmarks)
    }
}
