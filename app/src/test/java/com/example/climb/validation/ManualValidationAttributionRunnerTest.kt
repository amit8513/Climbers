package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.analysis.contact.Limb
import com.example.climb.attribution.RouteAttributionEngine
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves [ManualValidationAttributionRunner] is a pure, thin wrapper around Phase 4A's
 * `RouteAttributionEngine` - see that object's own doc comment for why it must never become a
 * second scoring implementation or a second pose-extraction path.
 */
class ManualValidationAttributionRunnerTest {

    private fun square(originX: Float, originY: Float, size: Float): List<Point2D> = listOf(
        Point2D(originX, originY),
        Point2D(originX + size, originY),
        Point2D(originX + size, originY + size),
        Point2D(originX, originY + size),
    )

    private val holdOne = ValidationHoldAnnotation(holdId = 1, contourNormalized = square(0.0f, 0.0f, 0.1f))
    private val holdTwo = ValidationHoldAnnotation(holdId = 2, contourNormalized = square(0.2f, 0.2f, 0.1f))
    private val holdThree = ValidationHoldAnnotation(holdId = 3, contourNormalized = square(0.4f, 0.4f, 0.1f))
    private val holdFour = ValidationHoldAnnotation(holdId = 4, contourNormalized = square(0.6f, 0.6f, 0.1f))

    private val routeOne = ValidationRouteDefinition(
        routeId = 100L,
        name = "route-one",
        startHoldIds = setOf(1),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        bodyHoldIds = setOf(2),
    )

    private val routeTwo = ValidationRouteDefinition(
        routeId = 200L,
        name = "route-two",
        startHoldIds = setOf(3),
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        bodyHoldIds = setOf(4),
    )

    private val handBuiltTimeline = HoldContactTimeline(
        events = listOf(
            HoldContactEvent(
                limb = Limb.LEFT_HAND,
                holdId = 1,
                type = ContactEventType.ESTABLISHED,
                timestampMs = 100L,
                confidence = 0.9f,
                evidenceQuality = EvidenceQuality.STRONG,
            ),
            HoldContactEvent(
                limb = Limb.RIGHT_HAND,
                holdId = 2,
                type = ContactEventType.ESTABLISHED,
                timestampMs = 500L,
                confidence = 0.85f,
                evidenceQuality = EvidenceQuality.STRONG,
            ),
        ),
    )

    @Test
    fun `reuses the exact same timeline passed to it, never a second pose-derived timeline`() {
        val routeDefinitions = listOf(routeOne, routeTwo)
        val holds = listOf(holdOne, holdTwo, holdThree, holdFour)
        val attemptStartTimestampMs = 0L

        val viaRunner = ManualValidationAttributionRunner.run(
            routeDefinitions = routeDefinitions,
            holds = holds,
            timeline = handBuiltTimeline,
            attemptStartTimestampMs = attemptStartTimestampMs,
        )

        // Mapped by hand, the exact same way the runner itself maps them - see
        // ValidationRouteDefinition.toRouteCandidate() and this test's own hold-shape mapping.
        val viaDirectEngineCall = RouteAttributionEngine.attribute(
            candidates = routeDefinitions.map { it.toRouteCandidate() },
            holds = holds.map { HoldShape(it.holdId, it.contourNormalized) },
            timeline = handBuiltTimeline,
            attemptStartTimestampMs = attemptStartTimestampMs,
        )

        assertEquals(viaDirectEngineCall, viaRunner)
    }

    @Test
    fun `has no member referencing PoseEstimator, VideoSource, or PoseAnalysisConfiguration`() {
        val forbiddenTypeNameFragments = listOf("PoseEstimator", "VideoSource", "PoseAnalysisConfiguration")

        val allTypeNames = ManualValidationAttributionRunner::class.java.declaredMethods.flatMap { method ->
            method.parameterTypes.map { it.name } + method.returnType.name
        }

        assertTrue(
            "ManualValidationAttributionRunner's member types: $allTypeNames",
            allTypeNames.none { typeName -> forbiddenTypeNameFragments.any { typeName.contains(it) } },
        )
    }

    @Test
    fun `unavailable corridor and finish remains unavailable end to end`() {
        val result = ManualValidationAttributionRunner.run(
            routeDefinitions = listOf(routeOne),
            holds = listOf(holdOne, holdTwo),
            timeline = handBuiltTimeline,
            attemptStartTimestampMs = 0L,
        )

        assertEquals(1, result.subScores.size)
        val subScore = result.subScores.single()
        assertNull(subScore.corridorScore)
        assertNull(subScore.finishScore)
    }

    @Test
    fun `deterministic across repeated calls`() {
        val routeDefinitions = listOf(routeOne, routeTwo)
        val holds = listOf(holdOne, holdTwo, holdThree, holdFour)
        val attemptStartTimestampMs = 0L

        val first = ManualValidationAttributionRunner.run(routeDefinitions, holds, handBuiltTimeline, attemptStartTimestampMs)
        val second = ManualValidationAttributionRunner.run(routeDefinitions, holds, handBuiltTimeline, attemptStartTimestampMs)
        val third = ManualValidationAttributionRunner.run(routeDefinitions, holds, handBuiltTimeline, attemptStartTimestampMs)

        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `empty routeDefinitions produces the same UNRESOLVED, NO_CANDIDATES result the engine itself would`() {
        val result = ManualValidationAttributionRunner.run(
            routeDefinitions = emptyList(),
            holds = listOf(holdOne, holdTwo),
            timeline = handBuiltTimeline,
            attemptStartTimestampMs = 0L,
        )

        assertEquals(AttributionStatus.UNRESOLVED, result.status)
        assertEquals(AttributionReasonCode.NO_CANDIDATES, result.reasonCode)
        assertNull(result.winningRouteVersionId)
        assertNull(result.margin)
        assertTrue(result.subScores.isEmpty())
    }
}
