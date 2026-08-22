package com.example.climb.validation

import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.SubScoreResult
import com.example.climb.clubs.AttributionReasonCode
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.PoseAnalysisConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AttributionCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun sampleHolds() = listOf(
        ValidationHoldAnnotation(1, listOf(Point2D(0.1f, 0.1f), Point2D(0.2f, 0.1f), Point2D(0.15f, 0.2f))),
        ValidationHoldAnnotation(2, listOf(Point2D(0.5f, 0.5f), Point2D(0.6f, 0.5f), Point2D(0.55f, 0.6f))),
    )

    private fun samplePoseArtifactCacheKey() = PoseArtifactCacheKey(
        videoFingerprint = "abc123:1000",
        poseExtractorVersion = MANUAL_VALIDATION_POSE_EXTRACTOR_VERSION,
        targetFps = 15,
        poseAnalysisConfigFingerprint = poseAnalysisConfigFingerprint(PoseAnalysisConfiguration(targetFps = 15)),
        artifactSchemaVersion = CURRENT_POSE_ARTIFACT_SCHEMA_VERSION,
    )

    private fun sampleContactAnalysisCacheKey(holds: List<ValidationHoldAnnotation> = sampleHolds()) = ContactAnalysisCacheKey(
        poseArtifactCacheKey = samplePoseArtifactCacheKey(),
        holdGeometryFingerprint = holdGeometryFingerprint(holds),
        holdContactConfigFingerprint = holdContactConfigFingerprint(com.example.climb.analysis.metrics.HoldContactConfig()),
        referenceImageDimensionsFingerprint = referenceImageDimensionsFingerprint(ImageDimensions(1920, 1080)),
        cameraGeometryProfileVersion = 1,
        expectedGeometryProfileVersion = 1,
        artifactSchemaVersion = CURRENT_CONTACT_ANALYSIS_SCHEMA_VERSION,
    )

    private fun sampleRouteDefinitions() = listOf(
        ValidationRouteDefinition(
            routeId = 10L,
            name = "Red Route",
            startHoldIds = setOf(1, 2),
            startPolicy = StartPolicy.TWO_HOLDS_ONE_PER_HAND,
            bodyHoldIds = setOf(3, 4),
            finishHoldIds = setOf(5),
            finishPolicy = FinishPolicy.ONE_HAND_ON_FINISH,
            corridorNormalized = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
        ),
        ValidationRouteDefinition(
            routeId = 11L,
            name = "Blue Route",
            startHoldIds = setOf(6),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        ),
    )

    private fun sampleKey(
        contactAnalysisCacheKey: ContactAnalysisCacheKey = sampleContactAnalysisCacheKey(),
        routeDefinitions: List<ValidationRouteDefinition> = sampleRouteDefinitions(),
    ) = AttributionCacheKey(
        contactAnalysisCacheKey = contactAnalysisCacheKey,
        routeDefinitionsFingerprint = routeDefinitionsFingerprint(routeDefinitions),
        attemptStartTimestampMs = 1_000L,
        routeAttributionScoringConfigFingerprint = routeAttributionScoringConfigFingerprint(RouteAttributionScoringConfig()),
        artifactSchemaVersion = CURRENT_ATTRIBUTION_CACHE_SCHEMA_VERSION,
    )

    /** A non-trivial fixture: 2+ candidates, one VERIFIED winner, mixed null/non-null
     * corridorScore/finishScore. */
    private fun sampleVerifiedResult() = AttributionResult(
        winningRouteVersionId = 10L,
        status = AttributionStatus.VERIFIED,
        reasonCode = null,
        margin = 0.22f,
        subScores = listOf(
            SubScoreResult(
                routeVersionId = 10L,
                startEvidenceStatus = StartEvidenceStatus.START_OBSERVED_MATCH,
                contactCoverageScore = 0.9f,
                corridorScore = 0.8f,
                finishScore = 0.7f,
                foreignContactEventCount = 0,
                foreignContactPenalty = 0f,
                combinedScore = 0.85f,
            ),
            SubScoreResult(
                routeVersionId = 11L,
                startEvidenceStatus = StartEvidenceStatus.START_OBSERVED_MISMATCH,
                contactCoverageScore = 0.4f,
                corridorScore = null,
                finishScore = null,
                foreignContactEventCount = 2,
                foreignContactPenalty = 0.5f,
                combinedScore = 0.1f,
            ),
        ),
    )

    /** A second fixture exercising null reasonCode/margin's OPPOSITE - a non-null reasonCode, a
     * null margin, and an empty subScores list - status UNRESOLVED, reasonCode NO_CANDIDATES. */
    private fun sampleUnresolvedResult() = AttributionResult(
        winningRouteVersionId = null,
        status = AttributionStatus.UNRESOLVED,
        reasonCode = AttributionReasonCode.NO_CANDIDATES,
        margin = null,
        subScores = emptyList(),
    )

    // --- routeDefinitionsFingerprint ---

    @Test
    fun `routeDefinitionsFingerprint is identical regardless of input list order`() {
        val definitions = sampleRouteDefinitions()
        val reversed = definitions.reversed()

        assertEquals(routeDefinitionsFingerprint(definitions), routeDefinitionsFingerprint(reversed))
    }

    @Test
    fun `routeDefinitionsFingerprint differs when a route is added`() {
        val original = sampleRouteDefinitions()
        val extra = original + ValidationRouteDefinition(
            routeId = 12L,
            name = "Green Route",
            startHoldIds = setOf(7),
            startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        )

        assertNotEquals(routeDefinitionsFingerprint(original), routeDefinitionsFingerprint(extra))
    }

    @Test
    fun `routeDefinitionsFingerprint differs when a route is removed`() {
        val original = sampleRouteDefinitions()
        val fewer = original.drop(1)

        assertNotEquals(routeDefinitionsFingerprint(original), routeDefinitionsFingerprint(fewer))
    }

    @Test
    fun `routeDefinitionsFingerprint differs when a route's start holds change`() {
        val original = sampleRouteDefinitions()
        val edited = listOf(original[0].copy(startHoldIds = setOf(1, 2, 99)), original[1])

        assertNotEquals(routeDefinitionsFingerprint(original), routeDefinitionsFingerprint(edited))
    }

    @Test
    fun `routeDefinitionsFingerprint differs when a route's start policy changes`() {
        val original = sampleRouteDefinitions()
        val edited = listOf(original[0].copy(startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND), original[1])

        assertNotEquals(routeDefinitionsFingerprint(original), routeDefinitionsFingerprint(edited))
    }

    @Test
    fun `routeDefinitionsFingerprint differs when a route's finish holds change`() {
        val original = sampleRouteDefinitions()
        val edited = listOf(original[0].copy(finishHoldIds = setOf(5, 6)), original[1])

        assertNotEquals(routeDefinitionsFingerprint(original), routeDefinitionsFingerprint(edited))
    }

    @Test
    fun `routeDefinitionsFingerprint differs when a route's corridor changes`() {
        val original = sampleRouteDefinitions()
        val edited = listOf(original[0].copy(corridorNormalized = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)), original[1])

        assertNotEquals(routeDefinitionsFingerprint(original), routeDefinitionsFingerprint(edited))
    }

    @Test
    fun `routeDefinitionsFingerprint differs between a present corridor and none`() {
        val withCorridor = sampleRouteDefinitions()
        val withoutCorridor = listOf(withCorridor[0].copy(corridorNormalized = null), withCorridor[1])

        assertNotEquals(routeDefinitionsFingerprint(withCorridor), routeDefinitionsFingerprint(withoutCorridor))
    }

    /** Regression test for a real ad hoc-delimiter collision the old `:`/`|`-joined
     * `routeDefinitionsFingerprint` implementation was vulnerable to: a two-route list encodes as
     * `enc(route1) + "|" + enc(route2)`, and [ValidationRouteDefinition.name] is arbitrary
     * developer-entered free text with no restriction on containing `:`/`|` itself - so a single
     * carefully-named route can reproduce that exact same joined string, making the fingerprint
     * unable to tell the two logically-different route definition lists apart (a stale attribution
     * cache entry from the two-route list would then have been wrongly treated as still valid for
     * the one-route list, or vice versa). Proves the two lists below - genuinely different route
     * definitions - now produce different fingerprints. */
    @Test
    fun `routeDefinitionsFingerprint does not collide when a route name contains the internal delimiter characters`() {
        val twoRoutes = listOf(
            ValidationRouteDefinition(routeId = 1L, name = "A", startHoldIds = setOf(1), startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND),
            ValidationRouteDefinition(routeId = 2L, name = "B", startHoldIds = setOf(2), startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND),
        )
        // A single route whose `name` is crafted to reproduce, character for character, what the
        // OLD hand-joined encoding of `twoRoutes` looked like.
        val craftedName = "A:start=1:startPolicy=SINGLE_HOLD_ANY_HAND:body=:finish=:finishPolicy=null:corridor=none|2:B"
        val oneCraftedRoute = listOf(
            ValidationRouteDefinition(routeId = 1L, name = craftedName, startHoldIds = setOf(2), startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND),
        )

        assertNotEquals(routeDefinitionsFingerprint(twoRoutes), routeDefinitionsFingerprint(oneCraftedRoute))
    }

    // --- LocalJsonAttributionCacheStore ---

    @Test
    fun `save then load with the same expected key returns an equal VERIFIED result - exact round trip`() {
        val store = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        val result = sampleVerifiedResult()

        store.save("session-1", AttributionCacheEntry(key, result, createdAtEpochMs = 123_456L))
        val loaded = store.load("session-1", key)

        assertEquals(result, loaded)
    }

    @Test
    fun `save then load with the same expected key returns an equal UNRESOLVED result - null margin, non-null reasonCode, empty subScores`() {
        val store = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        val result = sampleUnresolvedResult()

        store.save("session-unresolved", AttributionCacheEntry(key, result, createdAtEpochMs = 789L))
        val loaded = store.load("session-unresolved", key)

        assertEquals(result, loaded)
    }

    @Test
    fun `load with a different expected key than what was saved returns null`() {
        val store = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        store.save("session-2", AttributionCacheEntry(key, sampleVerifiedResult(), createdAtEpochMs = 1L))

        // Simulates a route-definition edit after the cache entry was written.
        val editedRoutes = sampleRouteDefinitions().drop(1)
        val differentKey = key.copy(routeDefinitionsFingerprint = routeDefinitionsFingerprint(editedRoutes))

        assertNull(store.load("session-2", differentKey))
    }

    @Test
    fun `load on a session id where no file exists returns null rather than crashing`() {
        val store = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("cache"))

        assertNull(store.load("never-saved-session", sampleKey()))
    }

    @Test
    fun `load on a file containing garbage JSON returns null rather than crashing`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonAttributionCacheStore(directory)
        File(directory, "corrupt-session.json").writeText("{ this is not valid json at all ][")

        assertNull(store.load("corrupt-session", sampleKey()))
    }

    @Test
    fun `load on a file whose stored artifactSchemaVersion does not match the current version returns null`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonAttributionCacheStore(directory)
        val key = sampleKey()
        store.save("mismatched-schema-session", AttributionCacheEntry(key, sampleVerifiedResult(), createdAtEpochMs = 1L))

        val file = File(directory, "mismatched-schema-session.json")
        val mutated = file.readText().replace(
            "\"artifactSchemaVersion\":$CURRENT_ATTRIBUTION_CACHE_SCHEMA_VERSION",
            "\"artifactSchemaVersion\":999",
        )
        file.writeText(mutated)

        assertNull(store.load("mismatched-schema-session", key))
    }

    @Test
    fun `save never leaves a visible json tmp file behind after a successful save`() {
        val directory = temporaryFolder.newFolder("cache")
        val store = LocalJsonAttributionCacheStore(directory)

        store.save("tmp-check-session", AttributionCacheEntry(sampleKey(), sampleVerifiedResult(), createdAtEpochMs = 1L))

        val tmpFiles = directory.listFiles { file -> file.name.endsWith(".json.tmp") }.orEmpty()
        assertEquals(0, tmpFiles.size)
    }

    // --- hasAnyEntryFor ---

    @Test
    fun `hasAnyEntryFor is false when nothing has ever been saved for this session`() {
        val store = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("cache"))

        assertEquals(false, store.hasAnyEntryFor("never-saved-session"))
    }

    @Test
    fun `hasAnyEntryFor is true once something is saved for this session, even under a different key`() {
        val store = LocalJsonAttributionCacheStore(temporaryFolder.newFolder("cache"))
        val key = sampleKey()
        store.save("has-entry-session", AttributionCacheEntry(key, sampleVerifiedResult(), createdAtEpochMs = 1L))

        assertEquals(true, store.hasAnyEntryFor("has-entry-session"))
        val differentKey = key.copy(routeDefinitionsFingerprint = routeDefinitionsFingerprint(sampleRouteDefinitions().drop(1)))
        assertNull(store.load("has-entry-session", differentKey))
    }
}
