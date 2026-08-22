package com.example.climb.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [StageProvenance]/[ValidationPipelineProvenance] carry almost no logic of their own - these
 * tests just pin down that they behave as plain value types (construction, equality) and that
 * [StageProvenance.invalidationReason] defaults to null, matching an ordinary first-run
 * RECOMPUTED (as opposed to a RECOMPUTED-due-to-invalidation, which always sets it explicitly).
 */
class ValidationPipelineProvenanceTest {

    @Test
    fun `StageProvenance invalidationReason defaults to null for a plain first-run RECOMPUTED`() {
        val stage = StageProvenance(outcome = CacheOutcome.RECOMPUTED)

        assertEquals(CacheOutcome.RECOMPUTED, stage.outcome)
        assertNull(stage.invalidationReason)
    }

    @Test
    fun `StageProvenance carries a non-null invalidationReason for a real cache invalidation`() {
        val stage = StageProvenance(
            outcome = CacheOutcome.RECOMPUTED,
            invalidationReason = "route definitions changed",
        )

        assertEquals(CacheOutcome.RECOMPUTED, stage.outcome)
        assertEquals("route definitions changed", stage.invalidationReason)
    }

    @Test
    fun `two StageProvenance instances with the same fields are equal`() {
        val a = StageProvenance(outcome = CacheOutcome.CACHE_HIT)
        val b = StageProvenance(outcome = CacheOutcome.CACHE_HIT)

        assertEquals(a, b)
    }

    @Test
    fun `ValidationPipelineProvenance allows null contact and attribution stages`() {
        val provenance = ValidationPipelineProvenance(
            pose = StageProvenance(outcome = CacheOutcome.RECOMPUTED),
            contact = null,
            attribution = null,
        )

        assertEquals(CacheOutcome.RECOMPUTED, provenance.pose.outcome)
        assertNull(provenance.contact)
        assertNull(provenance.attribution)
    }

    @Test
    fun `two ValidationPipelineProvenance instances with the same fields are equal`() {
        val a = ValidationPipelineProvenance(
            pose = StageProvenance(outcome = CacheOutcome.CACHE_HIT),
            contact = StageProvenance(outcome = CacheOutcome.RECOMPUTED, invalidationReason = "hold geometry changed"),
            attribution = StageProvenance(outcome = CacheOutcome.CACHE_HIT),
        )
        val b = ValidationPipelineProvenance(
            pose = StageProvenance(outcome = CacheOutcome.CACHE_HIT),
            contact = StageProvenance(outcome = CacheOutcome.RECOMPUTED, invalidationReason = "hold geometry changed"),
            attribution = StageProvenance(outcome = CacheOutcome.CACHE_HIT),
        )

        assertEquals(a, b)
    }
}
