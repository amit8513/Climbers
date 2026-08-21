package com.example.climb.analysis.contact

enum class ContactEventType { ESTABLISHED, RELEASED }

/** How much to trust one [HoldContactEvent] — never folded into [ContactEventType], same
 * independent-fields discipline this codebase already holds `AttemptSource`/`AttributionStatus`/
 * `ResultAuthority` to. */
enum class EvidenceQuality {
    /** The proxy resolved from its primary landmark set, at or above
     * `HoldContactConfig.contactMinFrameConfidence`. */
    STRONG,
    /** The proxy resolved via its fallback landmark (wrist, or foot-index) rather than the
     * primary set — still trustworthy, just a lower-fidelity point. */
    FALLBACK,
    /** The proxy's confidence was below `HoldContactConfig.contactMinFrameConfidence` (whichever
     * landmark set produced it), or this event was forced by a tracking-gap reset/implausible-jump
     * reset with no fresh evidence at all. */
    UNCERTAIN,
}

/** Why a RELEASED event fired — `null` for every ESTABLISHED event. Added in Phase 3B so a
 * consumer (the manual-video validation report) can distinguish "let go on purpose" from
 * "tracking failed" without re-deriving the detector's own decision. */
enum class ReleaseReason {
    /** Ordinary distance hysteresis — the limb moved beyond `contactReleaseDistanceThreshold`. */
    DISTANCE_HYSTERESIS,
    /** A tracking gap reached `contactTrackingGapResetMs`. */
    LONG_GAP_RESET,
    /** The resolved proxy moved further than plausible in the elapsed time. */
    IMPLAUSIBLE_JUMP,
    /** This hold was released as the "A" side of a bounded A→B transition — the paired
     * ESTABLISHED event for the new hold carries the same timestamp. */
    TRANSITIONED_TO_ANOTHER_HOLD,
}

/**
 * One state transition in a [HoldContactTimeline] — deliberately only emitted on actual
 * ESTABLISHED/RELEASED transitions, never once per frame, so Phase 4 can count *unique* contact
 * events directly from a timeline's length rather than de-duplicating raw per-frame samples
 * itself. [confidence] is the detector's confidence at the moment of the transition (not
 * necessarily 1.0 even for ESTABLISHED, since a low-but-passing-threshold frame can trigger one).
 */
data class HoldContactEvent(
    val limb: Limb,
    val holdId: Int,
    val type: ContactEventType,
    val timestampMs: Long,
    val confidence: Float,
    val evidenceQuality: EvidenceQuality,
    val releaseReason: ReleaseReason? = null,
)
