package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import kotlin.math.abs

/** Only produced when [ManualValidationSession.groundTruthContacts] is non-empty — a report never
 * claims a detection/miss/false-positive count against ground truth that was never labeled. */
data class GroundTruthComparison(
    val trueDetectedContacts: Int,
    val missedContacts: Int,
    val falseContacts: Int,
    /** Mean absolute timestamp difference between each matched ground-truth annotation and its
     * detected event, in ms — `null` if nothing matched at all. Approximate: ground-truth
     * timestamps are themselves a human's eyeballed estimate, never frame-exact. */
    val approximateContactTimingErrorMs: Long?,
)

data class ManualValidationReport(
    val poseFrameCount: Int,
    val poseConfidenceCoveragePercent: Float,
    val establishedEventCount: Int,
    val contactsPerLimb: Map<Limb, Int>,
    val holdIdsTouched: Set<Int>,
    val timeline: HoldContactTimeline,
    val shortGapCount: Int,
    val longGapResetCount: Int,
    val implausibleJumpResetCount: Int,
    val lowConfidencePeriodCount: Int,
    /** `null` means "no ground truth was supplied" — see [GroundTruthComparison]'s doc comment. */
    val groundTruthComparison: GroundTruthComparison?,
)

object ManualValidationReportBuilder {

    /** Ground-truth timestamps are a human's eyeballed estimate — a detected event within this
     * window of an annotation counts as the same real contact. */
    private const val DEFAULT_TIMING_TOLERANCE_MS = 500L

    fun build(
        frameDiagnostics: List<ManualValidationFrameDiagnostics>,
        timeline: HoldContactTimeline,
        groundTruthContacts: List<GroundTruthContactAnnotation> = emptyList(),
        timingToleranceMs: Long = DEFAULT_TIMING_TOLERANCE_MS,
    ): ManualValidationReport {
        val poseFrameCount = frameDiagnostics.size
        val reliableCount = frameDiagnostics.count { it.isReliable }
        val coverage = if (poseFrameCount > 0) reliableCount.toFloat() / poseFrameCount else 0f

        val establishedEvents = timeline.events.filter { it.type == ContactEventType.ESTABLISHED }
        val contactsPerLimb = Limb.entries.associateWith { limb -> establishedEvents.count { it.limb == limb } }
        val holdIdsTouched = establishedEvents.map { it.holdId }.toSet()

        val releasedEvents = timeline.events.filter { it.type == ContactEventType.RELEASED }
        val longGapResetCount = releasedEvents.count { it.releaseReason == ReleaseReason.LONG_GAP_RESET }
        val implausibleJumpResetCount = releasedEvents.count { it.releaseReason == ReleaseReason.IMPLAUSIBLE_JUMP }

        val shortGapCount = countGapStarts(frameDiagnostics, GapState.SHORT)
        val lowConfidencePeriodCount = countReliabilityDrops(frameDiagnostics)

        val groundTruthComparison = if (groundTruthContacts.isEmpty()) {
            null
        } else {
            compareToGroundTruth(establishedEvents, groundTruthContacts, timingToleranceMs)
        }

        return ManualValidationReport(
            poseFrameCount = poseFrameCount,
            poseConfidenceCoveragePercent = coverage * 100f,
            establishedEventCount = establishedEvents.size,
            contactsPerLimb = contactsPerLimb,
            holdIdsTouched = holdIdsTouched,
            timeline = timeline,
            shortGapCount = shortGapCount,
            longGapResetCount = longGapResetCount,
            implausibleJumpResetCount = implausibleJumpResetCount,
            lowConfidencePeriodCount = lowConfidencePeriodCount,
            groundTruthComparison = groundTruthComparison,
        )
    }

    /** Counts distinct "a gap of exactly this zone began" transitions per limb — never the raw
     * per-frame count, which would just equal however many frames the gap happened to span. */
    private fun countGapStarts(frameDiagnostics: List<ManualValidationFrameDiagnostics>, zone: GapState): Int {
        val previousStateByLimb = mutableMapOf<Limb, GapState>()
        var starts = 0
        for (frame in frameDiagnostics) {
            for ((limb, state) in frame.gapStatesByLimb) {
                val previous = previousStateByLimb[limb] ?: GapState.NONE
                if (state == zone && previous != zone) starts++
                previousStateByLimb[limb] = state
            }
        }
        return starts
    }

    /** Counts distinct reliable→unreliable transitions across the whole video — "how many
     * separate low-confidence stretches", not a raw unreliable-frame tally. */
    private fun countReliabilityDrops(frameDiagnostics: List<ManualValidationFrameDiagnostics>): Int {
        var drops = 0
        var previousReliable = true
        for (frame in frameDiagnostics) {
            if (!frame.isReliable && previousReliable) drops++
            previousReliable = frame.isReliable
        }
        return drops
    }

    /** Greedy nearest-available match: each ground-truth annotation claims the closest
     * still-unclaimed same-(limb,hold) detected event within [timingToleranceMs]. Every detected
     * event left unclaimed afterward counts as a false contact; every ground-truth annotation
     * left unmatched counts as a miss. */
    private fun compareToGroundTruth(
        establishedEvents: List<HoldContactEvent>,
        groundTruthContacts: List<GroundTruthContactAnnotation>,
        timingToleranceMs: Long,
    ): GroundTruthComparison {
        val matchedDetectedIndices = mutableSetOf<Int>()
        val timingErrors = mutableListOf<Long>()
        var trueDetected = 0

        for (groundTruth in groundTruthContacts) {
            val candidates = establishedEvents.withIndex().filter { (index, event) ->
                index !in matchedDetectedIndices &&
                    event.limb == groundTruth.limb &&
                    event.holdId == groundTruth.holdId &&
                    abs(event.timestampMs - groundTruth.approxTimestampMs) <= timingToleranceMs
            }
            val best = candidates.minByOrNull { (_, event) -> abs(event.timestampMs - groundTruth.approxTimestampMs) }
            if (best != null) {
                matchedDetectedIndices += best.index
                timingErrors += abs(best.value.timestampMs - groundTruth.approxTimestampMs)
                trueDetected++
            }
        }

        val missed = groundTruthContacts.size - trueDetected
        val falseContacts = establishedEvents.size - matchedDetectedIndices.size
        val averageError = if (timingErrors.isNotEmpty()) timingErrors.average().toLong() else null

        return GroundTruthComparison(
            trueDetectedContacts = trueDetected,
            missedContacts = missed,
            falseContacts = falseContacts,
            approximateContactTimingErrorMs = averageError,
        )
    }
}
