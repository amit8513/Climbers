package com.example.climb.validation

import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationRouteDefinitionTest {

    private fun fullyPopulated(): ValidationRouteDefinition = ValidationRouteDefinition(
        routeId = 42L,
        name = "Red Overhang",
        startHoldIds = setOf(5, 1, 3),
        startPolicy = StartPolicy.TWO_HOLDS_ONE_PER_HAND,
        bodyHoldIds = setOf(9, 2, 6),
        finishHoldIds = setOf(20, 18),
        finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH,
        corridorNormalized = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.8f, bottom = 0.9f),
    )

    private fun minimalStartOnly(): ValidationRouteDefinition = ValidationRouteDefinition(
        routeId = 7L,
        name = "Blue Slab",
        startHoldIds = setOf(1),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
    )

    @Test
    fun `toRouteCandidate copies every field correctly when fully populated`() {
        val definition = fullyPopulated()
        val candidate = definition.toRouteCandidate()

        assertEquals(definition.routeId, candidate.routeVersionId)
        assertEquals(definition.startHoldIds, candidate.startHoldIds)
        assertEquals(definition.startPolicy, candidate.startPolicy)
        assertEquals(definition.bodyHoldIds, candidate.bodyHoldIds)
        assertEquals(definition.finishHoldIds, candidate.finishHoldIds)
        assertEquals(definition.finishPolicy, candidate.finishPolicy)
        assertEquals(definition.corridorNormalized, candidate.corridorNormalized)
    }

    @Test
    fun `toRouteCandidate maps absent optional fields to empty finishHoldIds, null finishPolicy, and null corridor`() {
        val definition = minimalStartOnly()
        val candidate = definition.toRouteCandidate()

        assertEquals(definition.routeId, candidate.routeVersionId)
        assertEquals(definition.startHoldIds, candidate.startHoldIds)
        assertEquals(definition.startPolicy, candidate.startPolicy)
        assertTrue(candidate.bodyHoldIds.isEmpty())
        assertTrue(candidate.finishHoldIds.isEmpty())
        assertNull(candidate.finishPolicy)
        assertNull(candidate.corridorNormalized)
    }

    @Test
    fun `JSON round-trip reproduces an equal object for a fully-populated definition`() {
        val definition = fullyPopulated()
        val roundTripped = definition.toJsonObject().toValidationRouteDefinition()
        assertEquals(definition, roundTripped)
    }

    @Test
    fun `JSON round-trip reproduces an equal object for a minimal start-only definition`() {
        val definition = minimalStartOnly()
        val roundTripped = definition.toJsonObject().toValidationRouteDefinition()
        assertEquals(definition, roundTripped)
    }

    @Test
    fun `toJsonObject serializes hold id sets in sorted ascending order regardless of Set construction order`() {
        val definition = ValidationRouteDefinition(
            routeId = 1L,
            name = "Determinism Check",
            startHoldIds = setOf(5, 1, 3),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
            bodyHoldIds = setOf(20, 4, 12),
            finishHoldIds = setOf(30, 8, 19),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
        )

        val json = definition.toJsonObject()
        assertEquals("[1,3,5]", json.getJSONArray("startHoldIds").toString())
        assertEquals("[4,12,20]", json.getJSONArray("bodyHoldIds").toString())
        assertEquals("[8,19,30]", json.getJSONArray("finishHoldIds").toString())
    }

    @Test
    fun `toJsonObject toString is identical across repeated calls on the same object`() {
        val definition = ValidationRouteDefinition(
            routeId = 1L,
            name = "Determinism Check",
            startHoldIds = setOf(5, 1, 3),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        )

        val first = definition.toJsonObject().toString()
        val second = definition.toJsonObject().toString()
        assertEquals(first, second)
    }

    @Test
    fun `JSON round-trip preserves a null finishPolicy and null corridorNormalized`() {
        val definition = minimalStartOnly()
        val json = definition.toJsonObject()

        assertEquals(JSONObject.NULL, json.get("finishPolicy"))
        assertEquals(JSONObject.NULL, json.get("corridorNormalized"))

        val roundTripped = json.toValidationRouteDefinition()
        assertNull(roundTripped.finishPolicy)
        assertNull(roundTripped.corridorNormalized)
    }
}
