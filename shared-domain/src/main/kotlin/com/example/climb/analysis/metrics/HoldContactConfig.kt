package com.example.climb.analysis.metrics

/**
 * Every dwell/distance/hysteresis/gap threshold for the (Phase 3) hold-contact detector. Kept
 * independent of `:app`'s `MetricsConfiguration` (which stays app-side — it's the pose-only
 * pipeline's own config, not a shared contract) since it governs a genuinely separate concern
 * (limb-to-hold contact) that doesn't exist in the pose-only pipeline at all.
 *
 * No `HoldContactDetector` algorithm exists yet to consume this (Phase 3 work). All defaults are
 * explicitly unvalidated placeholders pending real-footage tuning.
 */
data class HoldContactConfig(
    /** Max limb-landmark-to-hold-mask distance (normalized frame units, in the same
     * `WallReferenceSpace` both hold geometry and transformed pose landmarks share) for a limb to
     * be considered "approaching" a hold at all. */
    val contactApproachDistanceThreshold: Float = 0.045f,
    /** Tighter distance a limb must cross to move from "approaching" to "candidate" contact. */
    val contactCandidateDistanceThreshold: Float = 0.025f,
    /** How long (ms) a limb must stay within [contactCandidateDistanceThreshold] to promote a
     * "candidate" contact to "established" — the dwell requirement that filters out a fast
     * brush-past from a real grip/step. */
    val contactEstablishedDwellMs: Long = 300L,
    /** Distance beyond which an "established" contact is considered "released" — deliberately
     * looser than [contactCandidateDistanceThreshold] (hysteresis band) so a limb sitting right at
     * one boundary distance doesn't rapidly flicker established/released. */
    val contactReleaseDistanceThreshold: Float = 0.06f,
    /** Below this per-frame confidence (derived from pose landmark visibility/presence and the
     * hold's own detection confidence), a contact frame is treated as low-quality — a *continuous*
     * confidence modifier on the rolling established-contact confidence, never a hard transition
     * that destroys accumulated state after one bad frame. */
    val contactMinFrameConfidence: Float = 0.5f,
    /** A tracking gap shorter than this pauses the dwell timer and retains an established
     * contact's state (with a confidence decay proportional to gap length), rather than
     * destroying it. */
    val contactShortGapMaxMs: Long = 200L,
    /** A tracking gap at or beyond this fully resets a limb's candidate/established state
     * (keeping only its `previousHoldId` pointer for sequence continuity) — same reset-on-gap
     * convention already used by the pose pipeline's own velocity detectors. */
    val contactTrackingGapResetMs: Long = 500L,
    /** Bounded window during which a limb may be tracked moving from one established hold to an
     * adjacent one without a hard release-then-reacquire cycle — the only path by which a limb's
     * "established" pointer can move without going through full release, so one limb is never
     * simultaneously established on two holds outside this governed transition. */
    val contactTransitionOverlapMs: Long = 400L,
    /** Bounded top-K of nearby hold candidates kept per limb for disambiguation/debugging. */
    val topKNearbyHolds: Int = 3,
    /** Max plausible limb-proxy displacement (normalized `WallReferenceSpace` units) per
     * millisecond between two consecutively-resolved frames — `HoldContactDetector` (Phase 3A)
     * compares actual displacement/elapsed-time against this to catch a tracking failure
     * masquerading as real motion (e.g. MediaPipe briefly locking onto the wrong limb) and reset
     * immediately rather than smoothing it in as if it were a real, fast movement. Per this
     * codebase's ROUTE_ATTRIBUTION_PLAN.md (unresolved decision #4), this exact number is not yet
     * grounded in real climbing movement speed — an explicitly unvalidated POC placeholder, same
     * honesty standard as every other threshold in this class. */
    val maxPlausibleNormalizedDisplacementPerMs: Float = 0.004f,
    val version: Int = 1,
) {
    init {
        require(contactCandidateDistanceThreshold < contactApproachDistanceThreshold) {
            "candidate threshold must be tighter than approach threshold"
        }
        require(contactReleaseDistanceThreshold > contactCandidateDistanceThreshold) {
            "release threshold must be looser than candidate threshold, for hysteresis"
        }
        require(contactShortGapMaxMs < contactTrackingGapResetMs) {
            "short-gap threshold must be below the long-gap reset threshold"
        }
        require(topKNearbyHolds > 0) { "topKNearbyHolds must be positive" }
        require(maxPlausibleNormalizedDisplacementPerMs > 0f) { "maxPlausibleNormalizedDisplacementPerMs must be positive" }
    }
}
