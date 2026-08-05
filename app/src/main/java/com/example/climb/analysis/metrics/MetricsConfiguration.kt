package com.example.climb.analysis.metrics

/**
 * Every metrics/event threshold lives here — not scattered as magic numbers through the
 * detector functions. These are tuned estimates, not biomechanical constants; see the
 * per-field docs for where a number is a genuine guess versus a value taken from the product
 * spec verbatim.
 */
data class MetricsConfiguration(
    /** Below this normalized hip velocity (body-heights/sec), the climber is considered still. */
    val stillVelocityThreshold: Float = 0.15f,
    val minPauseDurationMs: Long = 1_500L,
    val longPauseDurationMs: Long = 4_000L,

    /** Elbow angle at or below this is a "deep" lock-off. */
    val deepLockoffAngleDegrees: Float = 110f,
    /** Elbow angle at or above this counts as a relatively straight arm. */
    val straightArmAngleDegrees: Float = 150f,
    val minSustainedLockoffMs: Long = 700L,

    /** How long a foot must sit still to count as "placed" before a later move counts as an adjustment. */
    val footSettleDurationMs: Long = 500L,
    /** A foot move within this window of settling, past the displacement threshold, is a "possible adjustment". */
    val footAdjustmentWindowMs: Long = 1_500L,
    /** Displacement in normalized frame units (0-1) required to count as a real re-placement, not jitter. */
    val footAdjustmentDisplacementThreshold: Float = 0.04f,

    /** Normalized downward velocity spike, plus loss of a stable position, that flags a possible slip. */
    val footSlipVelocityThreshold: Float = 0.5f,
    /** The foot must have been settled for at least this long right before the downward spike —
     * without this, normal footwork (which also produces downward velocity spikes while
     * stepping) gets misread as a slip. */
    val footSlipPriorStabilityMs: Long = 300L,

    /** Frames below this average confidence are excluded from metrics entirely, not just flagged. */
    val minReliableFrameConfidence: Float = 0.5f,

    /** A local hip-velocity peak above this is flagged as a large dynamic move. */
    val dynamicMoveVelocityThreshold: Float = 1.0f,
    /** This many hip-direction reversals inside [repositioningWindowMs] flags excessive repositioning. */
    val repositioningReversalCount: Int = 3,
    val repositioningWindowMs: Long = 2_000L,
    /** A contiguous unreliable-frame stretch shorter than this isn't worth surfacing as its own event. */
    val lowConfidenceMinRangeMs: Long = 500L,

    /** A leg at or above this knee angle (degrees) counts as "extended/straight" for the
     * disengaged-leg check below. */
    val disengagedLegStraightAngleDegrees: Float = 155f,
    /** How much straighter (degrees) one leg must be than the other, at the same moment, to
     * flag it as possibly disengaged rather than just a normal stance asymmetry. */
    val disengagedLegAngleDifferenceDegrees: Float = 25f,
    /** The asymmetry must hold for at least this long to count — a brief pass-through between
     * holds isn't a dangling leg. */
    val minDisengagedLegDurationMs: Long = 1_200L,
)
