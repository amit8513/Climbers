package com.example.climb.ui.clubs.routeregistration

import com.example.climb.clubs.HoldRole
import com.example.climb.clubs.WallEntity
import com.example.climb.colordetection.Point2D
import com.example.climb.colordetection.ReviewedHold
import com.example.climb.data.RouteColor
import com.example.climb.edge.CameraGeometryProfile
import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.ReferenceFrameMetadata

/**
 * Canned stand-ins for everything Phase 2A has no real backend/hardware for yet: available walls,
 * a wall's already-active route colors (for [com.example.climb.clubs.RouteColorConflictChecker]
 * to run against), and a "captured" reference frame + its detected holds. This is the `:app`-side
 * equivalent of `:edge-agent`'s `FakeCameraSourceAdapter` — `:app` cannot depend on `:edge-agent`
 * (see NEXT_STEPS.md's module dependency graph), so this package provides its own fixture
 * mechanism built from the same `:shared-domain` [CapturedFrame]/[ReferenceFrameMetadata]/
 * [CameraGeometryProfile] contracts.
 *
 * Every wall/color/hold value below is a hardcoded stand-in, not a real query — replacing this
 * with real `ClubRepository`-backed queries is separate, later work once a real backend for
 * walls/wall-calibrations/vision-profiles exists.
 */
object RouteRegistrationFixtures {

    fun availableWalls(organizationId: Long): List<WallEntity> = listOf(
        WallEntity(id = 9001L, organizationId = organizationId, venueId = 1L, zoneId = 1L, name = "Wall A - Overhang", createdAt = 0L),
        WallEntity(id = 9002L, organizationId = organizationId, venueId = 1L, zoneId = 2L, name = "Wall B - Slab", createdAt = 0L),
    )

    /** Existing ACTIVE route colors already claiming each wall — a stand-in for a real query
     * (e.g. "every active RouteVersion's colorHex where wallId == this wall") until one exists. */
    fun activeColorHexesForWall(wallId: Long): List<Long> = when (wallId) {
        9001L -> listOf(RouteColor.RED.hex, RouteColor.BLUE.hex)
        else -> emptyList()
    }

    /** Stands in for "the staff app told the Edge Capture Agent to capture a reference frame now,
     * and it did" (plan doc §3/§13) — here, just a canned [CapturedFrame] tagged for a specific
     * wall, built from the default (back-camera-only, v1) [CameraGeometryProfile]. */
    fun requestReferenceFrame(organizationId: Long, wall: WallEntity): CapturedFrame {
        val profile = CameraGeometryProfile()
        return CapturedFrame(
            filePath = "fixture:///wall-${wall.id}-reference.jpg",
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
                organizationId = organizationId.toString(),
                wallId = wall.id.toString(),
                cameraDeviceId = "fixture-device",
            ),
        )
    }

    /** A plausible vertical hold layout — what a real detector would have found, standing in
     * until a captured fixture is actually run through [com.example.climb.colordetection.HoldComponentDetector]. */
    fun detectedHoldsFixture(): List<ReviewedHold> = listOf(
        ReviewedHold(id = 1, centroidNormalized = Point2D(0.50f, 0.88f), role = HoldRole.BODY),
        ReviewedHold(id = 2, centroidNormalized = Point2D(0.42f, 0.72f), role = HoldRole.BODY),
        ReviewedHold(id = 3, centroidNormalized = Point2D(0.58f, 0.56f), role = HoldRole.BODY),
        ReviewedHold(id = 4, centroidNormalized = Point2D(0.46f, 0.40f), role = HoldRole.BODY),
        ReviewedHold(id = 5, centroidNormalized = Point2D(0.52f, 0.24f), role = HoldRole.BODY),
        ReviewedHold(id = 6, centroidNormalized = Point2D(0.50f, 0.10f), role = HoldRole.BODY),
    )
}
