package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldContactTimelineJsonTest {

    private val timeline = HoldContactTimeline(
        listOf(
            HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.ESTABLISHED, 300L, 0.9f, EvidenceQuality.STRONG, null),
            HoldContactEvent(Limb.LEFT_HAND, 1, ContactEventType.RELEASED, 900L, 0.4f, EvidenceQuality.UNCERTAIN, ReleaseReason.DISTANCE_HYSTERESIS),
        ),
    )

    @Test
    fun `serializing the same timeline twice produces byte-identical JSON`() {
        assertEquals(timeline.toJson(), timeline.toJson())
    }

    @Test
    fun `a populated timeline round-trips exactly`() {
        val roundTripped = timeline.toJson().toHoldContactTimeline()
        assertEquals(timeline, roundTripped)
    }

    @Test
    fun `an empty timeline round-trips to an empty timeline`() {
        val empty = HoldContactTimeline()
        assertEquals(empty, empty.toJson().toHoldContactTimeline())
    }

    @Test
    fun `an ESTABLISHED event with a null releaseReason round-trips as null, not a crash`() {
        val roundTripped = timeline.toJson().toHoldContactTimeline()
        assertEquals(null, roundTripped.events[0].releaseReason)
        assertEquals(ReleaseReason.DISTANCE_HYSTERESIS, roundTripped.events[1].releaseReason)
    }

    @Test
    fun `blank input decodes to an empty timeline`() {
        assertEquals(HoldContactTimeline(), "".toHoldContactTimeline())
    }

    @Test
    fun `corrupt input decodes to an empty timeline rather than throwing`() {
        assertEquals(HoldContactTimeline(), "{not valid json".toHoldContactTimeline())
    }
}
