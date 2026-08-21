package com.example.climb.analysis.contact

/**
 * The full, ordered, deterministic sequence of [HoldContactEvent]s a [HoldContactDetector] has
 * produced across every frame processed so far. Ordering is stable: events are appended in the
 * exact order `HoldContactDetector.processFrame` produced them, which is itself deterministic
 * given the same (frames, transform, holds, config) input regardless of how densely those frames
 * are sampled — see `HoldContactDetectorFrameRateIndependenceTest` (frame-rate independence is a
 * property of timestamp-driven dwell/gap timing, not frame count, so it falls out of the
 * implementation rather than needing special-casing here).
 */
data class HoldContactTimeline(val events: List<HoldContactEvent> = emptyList()) {

    fun withEvents(newEvents: List<HoldContactEvent>): HoldContactTimeline = copy(events = events + newEvents)

    /** What Phase 4 actually needs: how many distinct times a limb newly gripped/stepped onto a
     * hold — never raw frame counts. */
    fun establishedEventCount(): Int = events.count { it.type == ContactEventType.ESTABLISHED }

    fun establishedEventCount(limb: Limb, holdId: Int): Int =
        events.count { it.type == ContactEventType.ESTABLISHED && it.limb == limb && it.holdId == holdId }

    fun eventsFor(limb: Limb): List<HoldContactEvent> = events.filter { it.limb == limb }
}
