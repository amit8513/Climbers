package com.example.climb.analysis.contact

import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.colordetection.CaptureToReferenceTransform
import com.example.climb.colordetection.Point2D

/**
 * Stateful, frame-by-frame limb-to-hold contact detector — the one implementation reused by both
 * the member app and the future `:edge-agent` (see this file's package doc / `ContactPoseFrame`'s
 * doc comment for why this lives in `:shared-domain`, never duplicated app-side). Feed it frames
 * in timestamp order via [processFrame]; read the accumulated result via [timeline].
 *
 * ## Coordinate spaces
 * [ContactPoseFrame] landmarks are in **capture-frame** coordinates — this detector NEVER assumes
 * those equal [holds]' `WallReferenceSpace`-normalized contour coordinates. Every resolved limb
 * proxy is explicitly passed through the caller-supplied [CaptureToReferenceTransform] (see
 * `processFrame`) before any distance/contact math runs. For the initial POC,
 * `CameraAlignmentChecker` (Phase 3, not part of this detector) only ever produces the identity
 * transform — but this detector itself never special-cases identity; it always applies whatever
 * transform it's given.
 *
 * ## Gap handling (`HoldContactConfig.contactShortGapMaxMs` / `contactTrackingGapResetMs`)
 * When a limb is untracked (no landmarks resolved at all — [LimbProxyResolver] returns `null`),
 * three zones apply based on elapsed time since the limb was last actually resolved:
 * - `< contactShortGapMaxMs`: retained unchanged (a blip — MediaPipe missed one frame).
 * - `[contactShortGapMaxMs, contactTrackingGapResetMs)`: retained, but confidence decays linearly
 *   across this window (from the fixed [LimbContactState.confidenceAtLastSeen] anchor, never the
 *   already-decayed rolling value — see [stepGap] — so the result is the same regardless of how
 *   many gap frames happen to get sampled in between), reaching zero right as the window ends.
 * - `>= contactTrackingGapResetMs`: hard reset — established/candidate cleared,
 *   [LimbContactState.previousHoldId] preserved, a RELEASED event emitted if a hold was
 *   established.
 *
 * A gap's dead time is also excluded from *candidate* dwell — [stepResolved] pushes
 * [LimbContactState.candidateSinceMs] forward by exactly the gap span the moment tracking resumes,
 * so a limb can never be promoted straight to ESTABLISHED using dwell time that includes a
 * blackout where its actual position was unknown.
 *
 * ## Low-confidence-but-resolved frames
 * A resolved-but-below-[HoldContactConfig.contactMinFrameConfidence] frame never destroys
 * established state outright — it degrades [LimbContactState.establishedConfidence]
 * proportionally (see [degradeConfidence]) and is simply not permitted to *establish a new*
 * contact this frame; existing distance/hysteresis rules still govern release independently.
 *
 * ## Implausible jumps
 * Checked before anything else on a resolved frame: if the transformed proxy point moved further
 * than [HoldContactConfig.maxPlausibleNormalizedDisplacementPerMs] × elapsed-ms since the last
 * resolved frame, this frame is treated as a tracking failure, not real motion — immediate hard
 * reset, same shape as a long-gap reset, no gradual decay.
 *
 * ## Bounded A→B transition
 * A limb with an established hold A whose nearest qualifying candidate is a *different* hold B
 * may have its established pointer move directly to B, without an externally-observable null in
 * between, only if B's own dwell requirement is satisfied within
 * [HoldContactConfig.contactTransitionOverlapMs] of B first becoming a candidate — [LimbContactState]'s
 * doc comment. This bound is enforced identically whether A is still established at the moment B's
 * dwell completes (the explicit two-event RELEASED+ESTABLISHED path) or A happens to release via
 * ordinary distance hysteresis on that exact same frame (in which case B would otherwise take the
 * unbounded "fresh establish" path) — see [attemptPromotion].
 */
class HoldContactDetector(
    private val holds: List<HoldShape>,
    private val config: HoldContactConfig = HoldContactConfig(),
) {
    private var states: Map<Limb, LimbContactState> = Limb.entries.associateWith { LimbContactState.initial(it) }
    private var accumulatedTimeline: HoldContactTimeline = HoldContactTimeline()

    val timeline: HoldContactTimeline get() = accumulatedTimeline

    fun stateOf(limb: Limb): LimbContactState = states.getValue(limb)

    /** Processes one frame for every limb and returns just the events produced *this* call (also
     * appended to [timeline]). [transform] is applied explicitly to every resolved proxy before
     * any contact geometry runs — see this class's doc comment. */
    fun processFrame(frame: ContactPoseFrame, transform: CaptureToReferenceTransform): List<HoldContactEvent> {
        val frameEvents = mutableListOf<HoldContactEvent>()
        val nextStates = LinkedHashMap<Limb, LimbContactState>()

        for (limb in Limb.entries) {
            val state = states.getValue(limb)
            val resolved = LimbProxyResolver.resolve(limb, frame)

            nextStates[limb] = if (resolved == null) {
                stepGap(state, frame.timestampMs, frameEvents)
            } else {
                val referencePoint = transform.apply(resolved.point)
                if (isImplausibleJump(state, referencePoint, frame.timestampMs)) {
                    stepImplausibleJump(state, referencePoint, frame.timestampMs, frameEvents)
                } else {
                    stepResolved(state, referencePoint, resolved, frame.timestampMs, frameEvents)
                }
            }
        }

        states = nextStates
        accumulatedTimeline = accumulatedTimeline.withEvents(frameEvents)
        return frameEvents
    }

    private fun isImplausibleJump(state: LimbContactState, point: Point2D, timestampMs: Long): Boolean {
        val lastPoint = state.lastSeenReferencePoint ?: return false
        val lastAtMs = state.lastSeenAtMs ?: return false
        val elapsedMs = timestampMs - lastAtMs
        if (elapsedMs <= 0) return false
        val displacement = point.distanceTo(lastPoint)
        val maxPlausible = config.maxPlausibleNormalizedDisplacementPerMs * elapsedMs
        return displacement > maxPlausible
    }

    private fun stepImplausibleJump(
        state: LimbContactState,
        point: Point2D,
        timestampMs: Long,
        events: MutableList<HoldContactEvent>,
    ): LimbContactState {
        state.establishedHoldId?.let { holdId ->
            events += HoldContactEvent(state.limb, holdId, ContactEventType.RELEASED, timestampMs, state.establishedConfidence, EvidenceQuality.UNCERTAIN, ReleaseReason.IMPLAUSIBLE_JUMP)
        }
        return state.copy(
            previousHoldId = state.establishedHoldId ?: state.previousHoldId,
            establishedHoldId = null,
            establishedConfidence = 0f,
            establishedSinceMs = null,
            candidateHoldId = null,
            candidateSinceMs = null,
            topKNearbyHoldIds = emptyList(),
            gapState = GapState.NONE,
            lastSeenAtMs = timestampMs,
            lastSeenReferencePoint = point,
            confidenceAtLastSeen = 0f,
        )
    }

    private fun stepGap(state: LimbContactState, timestampMs: Long, events: MutableList<HoldContactEvent>): LimbContactState {
        val lastSeenAtMs = state.lastSeenAtMs ?: return state.copy(gapState = GapState.NONE)
        val gapDurationMs = timestampMs - lastSeenAtMs

        return when {
            gapDurationMs >= config.contactTrackingGapResetMs -> {
                state.establishedHoldId?.let { holdId ->
                    events += HoldContactEvent(state.limb, holdId, ContactEventType.RELEASED, timestampMs, state.establishedConfidence, EvidenceQuality.UNCERTAIN, ReleaseReason.LONG_GAP_RESET)
                }
                state.copy(
                    previousHoldId = state.establishedHoldId ?: state.previousHoldId,
                    establishedHoldId = null,
                    establishedConfidence = 0f,
                    establishedSinceMs = null,
                    candidateHoldId = null,
                    candidateSinceMs = null,
                    topKNearbyHoldIds = emptyList(),
                    gapState = GapState.RESET,
                )
            }
            gapDurationMs >= config.contactShortGapMaxMs -> {
                val decayWindow = (config.contactTrackingGapResetMs - config.contactShortGapMaxMs).toFloat()
                val decayFraction = ((gapDurationMs - config.contactShortGapMaxMs).toFloat() / decayWindow).coerceIn(0f, 1f)
                // Decays from the FIXED confidenceAtLastSeen anchor every call, never from the
                // (possibly already-decayed) establishedConfidence - otherwise repeated gap-frame
                // polls would compound the decay and make the result sampling-density-dependent.
                state.copy(
                    establishedConfidence = state.confidenceAtLastSeen * (1f - decayFraction),
                    gapState = GapState.DECAYING,
                )
            }
            else -> state.copy(gapState = GapState.SHORT)
        }
    }

    private fun stepResolved(
        state: LimbContactState,
        point: Point2D,
        resolved: ResolvedLimbProxy,
        timestampMs: Long,
        events: MutableList<HoldContactEvent>,
    ): LimbContactState {
        val distances = holds.map { it.holdId to HoldGeometryMath.distanceToHold(point, it) }.sortedBy { it.second }
        val nearby = distances.filter { it.second <= config.contactApproachDistanceThreshold }
            .take(config.topKNearbyHolds)
            .map { it.first }

        // The hold established BEFORE this frame's own release/hysteresis check runs - needed so
        // attemptPromotion can tell "a genuinely fresh establish" apart from "the old hold just
        // released this same frame", even though by the time attemptPromotion runs
        // state.establishedHoldId may already be null either way. See attemptPromotion's doc
        // comment.
        val establishedHoldIdAtFrameStart = state.establishedHoldId

        // A tracking gap's dead time must never silently count as continuously-observed candidate
        // dwell - if this frame is the first resolved frame after one or more gap frames, push the
        // dwell anchor forward by exactly the gap span that just ended, once, here. (gapState is
        // GapState.NONE on a normal back-to-back resolved frame, so this is a no-op then.)
        val gapDurationJustEnded = if (state.gapState != GapState.NONE) {
            state.lastSeenAtMs?.let { timestampMs - it } ?: 0L
        } else {
            0L
        }
        val candidateSinceAdjusted = state.candidateSinceMs?.plus(gapDurationJustEnded)

        var next = state.copy(
            gapState = GapState.NONE,
            lastSeenAtMs = timestampMs,
            lastSeenReferencePoint = point,
            topKNearbyHoldIds = nearby,
            candidateSinceMs = candidateSinceAdjusted,
        )

        next = applyEstablishedDistance(next, distances, resolved, timestampMs, events)
        next = updateCandidate(next, distances)
        next = attemptPromotion(next, resolved, timestampMs, events, establishedHoldIdAtFrameStart)
        next = next.copy(confidenceAtLastSeen = next.establishedConfidence)

        return next
    }

    /** Governs release/hysteresis/confidence-decay for whichever hold is CURRENTLY established
     * (if any), based purely on this frame's distance to that specific hold — never touches
     * candidate tracking for other holds. */
    private fun applyEstablishedDistance(
        state: LimbContactState,
        distances: List<Pair<Int, Float>>,
        resolved: ResolvedLimbProxy,
        timestampMs: Long,
        events: MutableList<HoldContactEvent>,
    ): LimbContactState {
        val establishedHoldId = state.establishedHoldId ?: return state
        val establishedDistance = distances.firstOrNull { it.first == establishedHoldId }?.second ?: Float.MAX_VALUE

        if (establishedDistance > config.contactReleaseDistanceThreshold) {
            events += HoldContactEvent(state.limb, establishedHoldId, ContactEventType.RELEASED, timestampMs, state.establishedConfidence, evidenceQuality(resolved), ReleaseReason.DISTANCE_HYSTERESIS)
            return state.copy(previousHoldId = establishedHoldId, establishedHoldId = null, establishedConfidence = 0f, establishedSinceMs = null)
        }

        val degraded = degradeConfidence(state.establishedConfidence, resolved.confidence)
        return state.copy(establishedConfidence = degraded)
    }

    /** A single low-confidence-but-resolved frame degrades rolling confidence proportionally
     * rather than destroying it outright; a healthy frame nudges confidence back up toward the
     * fresh reading. Never returns exactly 0 from a single low-confidence frame unless the rolling
     * confidence was already at/near 0. */
    private fun degradeConfidence(rollingConfidence: Float, frameConfidence: Float): Float =
        if (frameConfidence < config.contactMinFrameConfidence) {
            rollingConfidence * (frameConfidence / config.contactMinFrameConfidence).coerceIn(0f, 1f)
        } else {
            (rollingConfidence + frameConfidence) / 2f
        }

    /** Tracks dwell for the nearest hold within candidate distance that ISN'T already the
     * established hold — a fresh hold restarts the dwell timer; the same hold continues
     * accumulating it. */
    private fun updateCandidate(state: LimbContactState, distances: List<Pair<Int, Float>>): LimbContactState {
        val candidateTarget = distances.firstOrNull {
            it.second <= config.contactCandidateDistanceThreshold && it.first != state.establishedHoldId
        }
        return when {
            candidateTarget == null -> state.copy(candidateHoldId = null, candidateSinceMs = null)
            state.candidateHoldId == candidateTarget.first -> state
            // A fresh (or newly-different) candidate - candidateSinceMs is deliberately left null
            // here and stamped by attemptPromotion, which alone knows the frame timestamp.
            else -> state.copy(candidateHoldId = candidateTarget.first, candidateSinceMs = null)
        }
    }

    /**
     * [establishedHoldIdAtFrameStart] is the limb's established hold as it stood BEFORE this same
     * frame's [applyEstablishedDistance] ran — not necessarily equal to `state.establishedHoldId`
     * here, since that call may have already released it via ordinary distance hysteresis this
     * very frame. This is what lets the [HoldContactConfig.contactTransitionOverlapMs] bound apply
     * uniformly regardless of *how* the old hold stopped being established: whether it's still
     * established right now (the explicit two-event transition below) or it just got released this
     * same frame (the "fresh establish" branch), a candidate that switched holds must still have
     * started its own dwell within the overlap window of *this* moment to be allowed to establish
     * immediately — otherwise it's not a real "smooth transition," just a stale candidate getting
     * lucky timing, and its dwell clock is reset to start fresh from now instead.
     */
    private fun attemptPromotion(
        state: LimbContactState,
        resolved: ResolvedLimbProxy,
        timestampMs: Long,
        events: MutableList<HoldContactEvent>,
        establishedHoldIdAtFrameStart: Int?,
    ): LimbContactState {
        // candidateSinceMs is only ever null immediately after updateCandidate just picked a new
        // target this very frame - stamp it now, exactly once, at the frame that actually observed it.
        var next = if (state.candidateHoldId != null && state.candidateSinceMs == null) {
            state.copy(candidateSinceMs = timestampMs)
        } else {
            state
        }

        val candidateHoldId = next.candidateHoldId ?: return next
        val candidateSinceMs = next.candidateSinceMs ?: return next
        if (resolved.confidence < config.contactMinFrameConfidence) return next

        val dwellMs = timestampMs - candidateSinceMs
        if (dwellMs < config.contactEstablishedDwellMs) return next

        if (next.establishedHoldId == null) {
            val switchedFromADifferentHold = establishedHoldIdAtFrameStart != null && establishedHoldIdAtFrameStart != candidateHoldId
            if (switchedFromADifferentHold && dwellMs > config.contactTransitionOverlapMs) {
                // The old hold just released this same frame, but this candidate's dwell clock is
                // too stale to count as a legitimate same-instant transition - give it a fresh
                // dwell window starting now rather than an instant, evidence-thin establish.
                return next.copy(candidateSinceMs = timestampMs)
            }
            events += HoldContactEvent(next.limb, candidateHoldId, ContactEventType.ESTABLISHED, timestampMs, resolved.confidence, evidenceQuality(resolved))
            return next.copy(
                previousHoldId = establishedHoldIdAtFrameStart ?: next.previousHoldId,
                establishedHoldId = candidateHoldId,
                establishedConfidence = resolved.confidence,
                establishedSinceMs = timestampMs,
                candidateHoldId = null,
                candidateSinceMs = null,
            )
        }

        if (next.establishedHoldId != candidateHoldId && dwellMs <= config.contactTransitionOverlapMs) {
            val oldHoldId = next.establishedHoldId!!
            events += HoldContactEvent(next.limb, oldHoldId, ContactEventType.RELEASED, timestampMs, next.establishedConfidence, evidenceQuality(resolved), ReleaseReason.TRANSITIONED_TO_ANOTHER_HOLD)
            events += HoldContactEvent(next.limb, candidateHoldId, ContactEventType.ESTABLISHED, timestampMs, resolved.confidence, evidenceQuality(resolved))
            return next.copy(
                previousHoldId = oldHoldId,
                establishedHoldId = candidateHoldId,
                establishedConfidence = resolved.confidence,
                establishedSinceMs = timestampMs,
                candidateHoldId = null,
                candidateSinceMs = null,
            )
        }

        return next
    }

    private fun evidenceQuality(resolved: ResolvedLimbProxy): EvidenceQuality = when {
        resolved.confidence < config.contactMinFrameConfidence -> EvidenceQuality.UNCERTAIN
        resolved.usedFallback -> EvidenceQuality.FALLBACK
        else -> EvidenceQuality.STRONG
    }
}
