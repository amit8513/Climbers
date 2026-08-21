package com.example.climb.ui.clubs.routeregistration

import com.example.climb.clubs.ReferenceSource
import com.example.climb.clubs.RouteRegistrationStatus
import com.example.climb.clubs.RouteVersionEntity
import com.example.climb.clubs.RouteVisionProfileEntity
import com.example.climb.clubs.WallCalibrationEntity
import com.example.climb.colordetection.FixedCameraRouteRegistrationConfig
import com.example.climb.colordetection.toHoldGeometryJson

/**
 * Turns one completed [RouteRegistrationDraftState] into the three draft entities
 * ([RouteRegistrationDraftResult]) that make up a wall-camera route registration. Every id here
 * comes from [localIdAllocator] — small, locally-unique, NEVER a real `ClubRepository.nextId()`
 * value — since Phase 2A never persists to Firestore (see `RouteRegistrationDraftStore`'s doc
 * comment). [referenceSource] on the produced [WallCalibrationEntity] always reflects where
 * [RouteRegistrationDraftState.capturedFrame] actually came from (Phase 2A: always
 * [ReferenceSource.TEST_FIXTURE] via [RouteRegistrationFixtures]) — never hardcoded to look more
 * legitimate than it is. [WallCalibrationEntity.hardwareValidated] is always `false` here; nothing
 * in this builder ever sets it `true`.
 */
object RouteRegistrationDraftBuilder {

    fun build(
        state: RouteRegistrationDraftState,
        setterUserId: String,
        localIdAllocator: () -> Long,
        nowEpochMs: Long,
    ): RouteRegistrationDraftResult? {
        val wall = state.wall ?: return null
        val frame = state.capturedFrame ?: return null

        val routeId = localIdAllocator()
        val routeVersionId = localIdAllocator()
        val wallCalibrationId = localIdAllocator()
        val visionProfileId = localIdAllocator()

        val wallCalibration = WallCalibrationEntity(
            id = wallCalibrationId,
            organizationId = state.organizationId,
            wallId = wall.id,
            referenceImageUrl = frame.filePath,
            referenceWidthPx = frame.metadata.widthPx,
            referenceHeightPx = frame.metadata.heightPx,
            wallRoiNormalized = state.wallRoiNormalized,
            // No real alignment-fingerprint algorithm has run against a fixture frame - "draft-
            // unvalidated" is an honest placeholder, never a fabricated real fingerprint.
            alignmentFingerprint = "draft-unvalidated",
            calibratedBy = setterUserId,
            createdAt = nowEpochMs,
            configVersion = FixedCameraRouteRegistrationConfig().version,
            referenceSource = ReferenceSource.TEST_FIXTURE,
            cameraGeometryProfileVersion = frame.metadata.requestedGeometryProfileVersion,
            hardwareValidated = false,
        )

        val visionProfile = RouteVisionProfileEntity(
            id = visionProfileId,
            organizationId = state.organizationId,
            wallId = wall.id,
            wallCalibrationId = wallCalibrationId,
            routeId = routeId,
            routeVersionId = routeVersionId,
            routeColorHex = state.candidateColorHex ?: 0L,
            // Real per-route color-model calibration (a real TargetColorModel derived from this
            // wall's own reference frame) is later work - the draft stage has no calibrated model
            // to serialize yet.
            calibratedColorModelJson = "",
            holdGeometryJson = state.holds.toHoldGeometryJson(),
            holdCount = state.holds.size,
            visionProfileFormatVersion = 1,
            staffConfirmed = false,
            createdAt = nowEpochMs,
            createdBy = setterUserId,
        )

        val routeVersion = RouteVersionEntity(
            id = routeVersionId,
            organizationId = state.organizationId,
            routeId = routeId,
            setterUserId = setterUserId,
            versionNumber = 1,
            colorHex = state.candidateColorHex,
            createdAt = nowEpochMs,
            venueId = wall.venueId,
            zoneId = wall.zoneId,
            wallId = wall.id,
            grade = state.grade,
            gradeSystem = state.gradeSystem,
            publicNumberOrName = state.publicNumberOrName,
            setAt = nowEpochMs,
            wallCalibrationId = wallCalibrationId,
            visionProfileId = visionProfileId,
            startPolicy = state.startPolicy,
            finishPolicy = state.finishPolicy,
            registrationStatus = RouteRegistrationStatus.DRAFT,
        )

        return RouteRegistrationDraftResult(wallCalibration, visionProfile, routeVersion)
    }
}
