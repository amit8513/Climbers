package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualValidationReportBuilderTest {

    private fun diagnostics(timestampMs: Long, isReliable: Boolean, gapStatesByLimb: Map<Limb, GapState>) =
        ManualValidationFrameDiagnostics(timestampMs, isReliable, gapStatesByLimb)

    private fun allLimbsGap(state: GapState): Map<Limb, GapState> = Limb.entries.associateWith { state }

    @Test
    fun `no ground truth supplied produces a null comparison, never a fabricated accuracy claim`() {
        val report = ManualValidationReportBuilder.build(
            frameDiagnostics = listOf(diagnostics(0L, true, allLimbsGap(GapState.NONE))),
            timeline = HoldContactTimeline(),
            groundTruthContacts = emptyList(),
        )

        assertNull(report.groundTruthComparison)
    }

    @Test
    fun `basic counts are computed correctly from the timeline`() {
        val timeline = HoldContactTimeline(
            listOf(
                HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 300L, 0.9f, EvidenceQuality.STRONG),
                HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.RELEASED, 900L, 0.4f, EvidenceQuality.UNCERTAIN, ReleaseReason.DISTANCE_HYSTERESIS),
                HoldContactEvent(Limb.RIGHT_FOOT, 2, ContactEventType.ESTABLISHED, 500L, 0.8f, EvidenceQuality.STRONG),
                HoldContactEvent(Limb.RIGHT_FOOT, 2, ContactEventType.RELEASED, 1200L, 0f, EvidenceQuality.UNCERTAIN, ReleaseReason.LONG_GAP_RESET),
            ),
        )
        val diags = (0..10).map { diagnostics(it * 100L, true, allLimbsGap(GapState.NONE)) }

        val report = ManualValidationReportBuilder.build(diags, timeline)

        assertEquals(2, report.establishedEventCount)
        assertEquals(1, report.contactsPerLimb[Limb.LEFT_HAND])
        assertEquals(1, report.contactsPerLimb[Limb.RIGHT_FOOT])
        assertEquals(0, report.contactsPerLimb[Limb.LEFT_FOOT])
        assertEquals(setOf(1, 2), report.holdIdsTouched)
        assertEquals(1, report.longGapResetCount)
        assertEquals(0, report.implausibleJumpResetCount)
        assertEquals(100f, report.poseConfidenceCoveragePercent, 1e-4f)
    }

    @Test
    fun `short gaps are counted as distinct started stretches, not raw frame counts`() {
        // Two separate short-gap stretches for LEFT_HAND: frames 1-3 (3 frames, one stretch),
        // then back to NONE, then frames 5-6 (2 frames, a second stretch).
        val diags = listOf(
            diagnostics(0L, true, mapOf(Limb.LEFT_HAND to GapState.NONE)),
            diagnostics(50L, true, mapOf(Limb.LEFT_HAND to GapState.SHORT)),
            diagnostics(100L, true, mapOf(Limb.LEFT_HAND to GapState.SHORT)),
            diagnostics(150L, true, mapOf(Limb.LEFT_HAND to GapState.SHORT)),
            diagnostics(200L, true, mapOf(Limb.LEFT_HAND to GapState.NONE)),
            diagnostics(250L, true, mapOf(Limb.LEFT_HAND to GapState.SHORT)),
            diagnostics(300L, true, mapOf(Limb.LEFT_HAND to GapState.SHORT)),
        )

        val report = ManualValidationReportBuilder.build(diags, HoldContactTimeline())

        assertEquals(2, report.shortGapCount)
    }

    @Test
    fun `low-confidence periods are counted as distinct reliable-to-unreliable transitions`() {
        val diags = listOf(
            diagnostics(0L, true, allLimbsGap(GapState.NONE)),
            diagnostics(50L, false, allLimbsGap(GapState.NONE)),
            diagnostics(100L, false, allLimbsGap(GapState.NONE)),
            diagnostics(150L, true, allLimbsGap(GapState.NONE)),
            diagnostics(200L, false, allLimbsGap(GapState.NONE)),
        )

        val report = ManualValidationReportBuilder.build(diags, HoldContactTimeline())

        assertEquals(2, report.lowConfidencePeriodCount)
    }

    @Test
    fun `ground truth comparison correctly counts true, missed, and false contacts`() {
        val timeline = HoldContactTimeline(
            listOf(
                HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 1000L, 0.9f, EvidenceQuality.STRONG),
                HoldContactEvent(Limb.RIGHT_FOOT, 5, ContactEventType.ESTABLISHED, 2000L, 0.9f, EvidenceQuality.STRONG), // false contact - no matching ground truth
            ),
        )
        val groundTruth = listOf(
            GroundTruthContactAnnotation(Limb.LEFT_HAND, 1, approxTimestampMs = 1050L), // matches, 50ms error
            GroundTruthContactAnnotation(Limb.LEFT_FOOT, 3, approxTimestampMs = 3000L), // missed - nothing detected
        )
        val diags = listOf(diagnostics(0L, true, allLimbsGap(GapState.NONE)))

        val report = ManualValidationReportBuilder.build(diags, timeline, groundTruth)

        val comparison = requireNotNull(report.groundTruthComparison)
        assertEquals(1, comparison.trueDetectedContacts)
        assertEquals(1, comparison.missedContacts)
        assertEquals(1, comparison.falseContacts)
        assertEquals(50L, comparison.approximateContactTimingErrorMs)
    }

    @Test
    fun `a ground-truth contact outside the timing tolerance is not falsely matched`() {
        val timeline = HoldContactTimeline(
            listOf(HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 1000L, 0.9f, EvidenceQuality.STRONG)),
        )
        val groundTruth = listOf(GroundTruthContactAnnotation(Limb.LEFT_HAND, 1, approxTimestampMs = 5000L))
        val diags = listOf(diagnostics(0L, true, allLimbsGap(GapState.NONE)))

        val report = ManualValidationReportBuilder.build(diags, timeline, groundTruth, timingToleranceMs = 500L)

        val comparison = requireNotNull(report.groundTruthComparison)
        assertEquals(0, comparison.trueDetectedContacts)
        assertEquals(1, comparison.missedContacts)
        assertEquals(1, comparison.falseContacts)
        assertNull(comparison.approximateContactTimingErrorMs)
    }
}
