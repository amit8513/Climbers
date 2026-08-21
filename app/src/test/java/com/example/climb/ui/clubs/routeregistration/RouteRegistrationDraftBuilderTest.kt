package com.example.climb.ui.clubs.routeregistration

import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.HoldRole
import com.example.climb.clubs.ReferenceSource
import com.example.climb.clubs.RouteColorConflictChecker
import com.example.climb.clubs.RouteRegistrationStatus
import com.example.climb.clubs.RouteVersionSnapshotValidator
import com.example.climb.clubs.StartPolicy
import com.example.climb.clubs.WallCalibrationActivationGuard
import com.example.climb.clubs.WallEntity
import com.example.climb.colordetection.Point2D
import com.example.climb.colordetection.ReviewedHold
import com.example.climb.data.RouteColor
import com.example.climb.edge.CameraGeometryProfile
import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.ReferenceFrameMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRegistrationDraftBuilderTest {

    // id matches RouteRegistrationFixtures.activeColorHexesForWall's one wall with existing
    // active colors (RED, BLUE) - needed for the conflict-detection tests below.
    private val wall = WallEntity(id = 9001L, organizationId = 10L, venueId = 100L, zoneId = 200L, name = "Wall A", createdAt = 0L)

    private fun fixtureFrame(): CapturedFrame {
        val profile = CameraGeometryProfile()
        return CapturedFrame(
            filePath = "fixture:///wall-1-reference.jpg",
            fileSizeBytes = 0L,
            metadata = ReferenceFrameMetadata(
                requestedGeometryProfileVersion = profile.version,
                requestedWidthPx = profile.requestedWidthPx,
                requestedHeightPx = profile.requestedHeightPx,
                widthPx = profile.requestedWidthPx,
                heightPx = profile.requestedHeightPx,
                rotationDegrees = profile.requestedRotationDegrees,
                mirrored = profile.mirrorExpected,
                actualCropRect = profile.cropRect,
                capturedAtEpochMs = 0L,
                organizationId = "10",
                wallId = "9001",
                cameraDeviceId = "fixture-device",
            ),
        )
    }

    private fun completeDraftState() = RouteRegistrationDraftState(
        organizationId = 10L,
        wall = wall,
        capturedFrame = fixtureFrame(),
        candidateColorHex = RouteColor.GREEN.hex,
        grade = 5,
        publicNumberOrName = null,
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        finishPolicy = FinishPolicy.TOP_OUT_ZONE,
        holds = listOf(
            ReviewedHold(1, Point2D(0.5f, 0.9f), HoldRole.START),
            ReviewedHold(2, Point2D(0.5f, 0.1f), HoldRole.FINISH),
        ),
    )

    @Test
    fun `builder returns null when there is no wall yet`() {
        val result = RouteRegistrationDraftBuilder.build(
            state = RouteRegistrationDraftState(organizationId = 10L),
            setterUserId = "staff-1",
            localIdAllocator = idAllocator(),
            nowEpochMs = 1_000L,
        )

        assertNull(result)
    }

    @Test
    fun `builder returns null when there is no reference frame yet`() {
        val result = RouteRegistrationDraftBuilder.build(
            state = RouteRegistrationDraftState(organizationId = 10L, wall = wall),
            setterUserId = "staff-1",
            localIdAllocator = idAllocator(),
            nowEpochMs = 1_000L,
        )

        assertNull(result)
    }

    @Test
    fun `a fully populated draft, even with no publicNumberOrName, validates as a savable draft`() {
        val result = RouteRegistrationDraftBuilder.build(completeDraftState(), "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        val snapshot = RouteVersionSnapshotValidator.validateDraft(result.routeVersion)
        assertTrue(snapshot.isValid)
        assertEquals(emptyList<String>(), snapshot.missingFields)
        assertNull(result.routeVersion.publicNumberOrName)
    }

    @Test
    fun `an explicit publicNumberOrName also produces a valid draft`() {
        val result = RouteRegistrationDraftBuilder.build(
            completeDraftState().copy(publicNumberOrName = "Route 7"),
            "staff-1",
            idAllocator(),
            1_000L,
        )
        requireNotNull(result)

        assertEquals("Route 7", result.routeVersion.publicNumberOrName)
        assertTrue(RouteVersionSnapshotValidator.validateDraft(result.routeVersion).isValid)
    }

    @Test
    fun `an incomplete draft (missing grade) is correctly reported invalid`() {
        val result = RouteRegistrationDraftBuilder.build(completeDraftState().copy(grade = null), "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        val snapshot = RouteVersionSnapshotValidator.validateDraft(result.routeVersion)
        assertFalse(snapshot.isValid)
        assertEquals(listOf("grade"), snapshot.missingFields)
    }

    @Test
    fun `the produced RouteVersion is always DRAFT, never ACTIVE`() {
        val result = RouteRegistrationDraftBuilder.build(completeDraftState(), "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        assertEquals(RouteRegistrationStatus.DRAFT, result.routeVersion.registrationStatus)
    }

    @Test
    fun `the produced WallCalibration always carries TEST_FIXTURE and is never hardware-validated`() {
        val result = RouteRegistrationDraftBuilder.build(completeDraftState(), "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        assertEquals(ReferenceSource.TEST_FIXTURE, result.wallCalibration.referenceSource)
        assertFalse(result.wallCalibration.hardwareValidated)
    }

    @Test
    fun `a draft's WallCalibration can never pass the activation guard`() {
        val result = RouteRegistrationDraftBuilder.build(completeDraftState(), "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        val eligibility = WallCalibrationActivationGuard.checkEligibility(
            result.wallCalibration,
            expectedGeometryProfileVersion = result.wallCalibration.cameraGeometryProfileVersion,
        )

        assertFalse(eligibility.isEligible)
        assertTrue(eligibility.blockingReasons.any { it.contains("TEST_FIXTURE") })
    }

    @Test
    fun `a color that conflicts with an already-active route on the same wall is detected`() {
        val conflictingState = completeDraftState().copy(candidateColorHex = RouteColor.RED.hex)
        val result = RouteRegistrationDraftBuilder.build(conflictingState, "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        val conflict = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = requireNotNull(result.routeVersion.colorHex),
            activeColorHexesOnSameWall = RouteRegistrationFixtures.activeColorHexesForWall(wall.id),
        )

        assertTrue(conflict.hasConflict)
    }

    @Test
    fun `a non-conflicting color on the same wall is not blocked`() {
        val result = RouteRegistrationDraftBuilder.build(completeDraftState(), "staff-1", idAllocator(), 1_000L)
        requireNotNull(result)

        val conflict = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = requireNotNull(result.routeVersion.colorHex),
            activeColorHexesOnSameWall = RouteRegistrationFixtures.activeColorHexesForWall(wall.id),
        )

        assertFalse(conflict.hasConflict)
    }

    @Test
    fun `existing personal-flow route creation is unaffected - it never constructs a RouteVersionEntity object at all`() {
        // ClubRepository.createRoute's personal-route path writes a raw Firestore map directly
        // (see its own source) and never touches RouteVersionEntity's constructor - so removing
        // that constructor's default (Phase 3A correction) cannot affect it. The real legacy-
        // compatibility behavior ("an absent registrationStatus field means ACTIVE") now lives
        // solely in routeVersionFromMap - see RouteVersionMappingTest for that coverage.
        //
        // This test's only job is to fail to compile if RouteVersionEntity's registrationStatus
        // parameter ever regains a default - it deliberately specifies it explicitly here.
        val explicitConstruction = com.example.climb.clubs.RouteVersionEntity(
            id = 1L,
            organizationId = 1L,
            routeId = 1L,
            setterUserId = "u",
            versionNumber = 1,
            createdAt = 1_000L,
            registrationStatus = RouteRegistrationStatus.ACTIVE,
        )

        assertEquals(RouteRegistrationStatus.ACTIVE, explicitConstruction.registrationStatus)
    }

    private fun idAllocator(): () -> Long {
        var counter = 0L
        return { counter -= 1; counter }
    }
}
