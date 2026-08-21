package com.example.climb.analysis.contact

import com.example.climb.colordetection.Point2D

/** Which of the three tracking-gap zones a limb was in as of its most recent processed frame —
 * see `HoldContactDetector`'s gap-handling doc comment. [NONE] covers both "never gapped" and "a
 * normal, successfully-resolved frame." */
enum class GapState { NONE, SHORT, DECAYING, RESET }

/**
 * One limb's full contact state, carried frame-to-frame by `HoldContactDetector`. At most one of
 * [establishedHoldId] is ever set at a time — this is a plain nullable field, never a set/list, so
 * "a limb never has two established holds simultaneously" is a structural guarantee, not a
 * runtime check. [establishedHoldId] can end up pointing at a *different* non-null hold than it
 * did entering a given `processFrame` call — but only when the new hold's own candidate dwell
 * (measured from when it first became the nearest qualifying candidate, gap time excluded) is
 * within `HoldContactConfig.contactTransitionOverlapMs`; a candidate that has been sitting past
 * that bound while a different hold was established gets its dwell clock reset rather than being
 * allowed to establish instantly the moment the old hold happens to release. See
 * `HoldContactDetector.attemptPromotion`'s doc comment for the exact mechanics.
 */
data class LimbContactState(
    val limb: Limb,
    val candidateHoldId: Int? = null,
    val candidateSinceMs: Long? = null,
    val establishedHoldId: Int? = null,
    val establishedConfidence: Float = 0f,
    val establishedSinceMs: Long? = null,
    /** The most recent hold this limb was established on before its current state — set on
     * release, reset, and transition alike, purely for sequence continuity (never re-read by the
     * detector itself to make a decision). */
    val previousHoldId: Int? = null,
    /** Bounded to `HoldContactConfig.topKNearbyHolds`, nearest-first. */
    val topKNearbyHoldIds: List<Int> = emptyList(),
    val gapState: GapState = GapState.NONE,
    /** Capture-independent bookkeeping for gap/implausible-jump detection — the timestamp and
     * `WallReferenceSpace`-transformed position of the last frame this limb was actually resolved
     * in (not merely processed). `null` until the limb is resolved for the first time. */
    val lastSeenAtMs: Long? = null,
    val lastSeenReferencePoint: Point2D? = null,
    /** [establishedConfidence]'s value as of [lastSeenAtMs] — the fixed anchor a subsequent
     * tracking gap's confidence decay is computed from, so decay is a pure function of elapsed gap
     * time (frame-rate independent) rather than compounding across however many gap frames happen
     * to get sampled in between. Never mutated by gap handling itself — only ever refreshed when
     * the limb is genuinely resolved again. */
    val confidenceAtLastSeen: Float = 0f,
) {
    companion object {
        fun initial(limb: Limb): LimbContactState = LimbContactState(limb = limb)
    }
}
