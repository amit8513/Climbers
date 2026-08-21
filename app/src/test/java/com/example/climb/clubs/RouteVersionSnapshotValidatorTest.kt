package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteVersionSnapshotValidatorTest {

    /** A fully-populated wall-camera route version — every field required once wallId is set is
     * present. Individual tests null out one field at a time from this baseline. */
    private fun completeWallRouteVersion() = RouteVersionEntity(
        id = 1L,
        organizationId = 10L,
        routeId = 100L,
        setterUserId = "setter-1",
        versionNumber = 1,
        colorHex = 0xFF0000L,
        createdAt = 1_000L,
        venueId = 20L,
        zoneId = 30L,
        wallId = 40L,
        grade = 5,
        gradeSystem = "V-Scale",
        publicNumberOrName = "42",
        setAt = 2_000L,
        retiredAt = null,
        wallCalibrationId = 50L,
        visionProfileId = 60L,
        startPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND,
        finishPolicy = FinishPolicy.TOP_OUT_ZONE,
        registrationStatus = RouteRegistrationStatus.ACTIVE,
    )

    /** A legacy, metadata-only route version created before the wall/attribution schema existed —
     * wallId is null and every other new field is null too. */
    private fun legacyRouteVersion() = RouteVersionEntity(
        id = 2L,
        organizationId = 10L,
        routeId = 101L,
        setterUserId = "setter-2",
        versionNumber = 1,
        colorHex = null,
        createdAt = 500L,
        venueId = null,
        zoneId = null,
        wallId = null,
        grade = null,
        gradeSystem = null,
        publicNumberOrName = null,
        setAt = null,
        retiredAt = null,
        wallCalibrationId = null,
        visionProfileId = null,
        startPolicy = null,
        finishPolicy = null,
        registrationStatus = RouteRegistrationStatus.ACTIVE,
    )

    @Test
    fun `legacy route version with wallId null and all new fields null is valid`() {
        val result = RouteVersionSnapshotValidator.validate(legacyRouteVersion())

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.missingFields)
    }

    @Test
    fun `wall route version with every required field null is invalid and lists them all`() {
        val routeVersion = completeWallRouteVersion().copy(
            venueId = null,
            zoneId = null,
            colorHex = null,
            grade = null,
            gradeSystem = null,
            publicNumberOrName = null,
            setAt = null,
            wallCalibrationId = null,
            visionProfileId = null,
            startPolicy = null,
            finishPolicy = null,
        )

        val result = RouteVersionSnapshotValidator.validate(routeVersion)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                "venueId",
                "zoneId",
                "colorHex",
                "grade",
                "gradeSystem",
                "publicNumberOrName",
                "setAt",
                "wallCalibrationId",
                "visionProfileId",
                "startPolicy",
                "finishPolicy",
            ),
            result.missingFields,
        )
    }

    @Test
    fun `wall route version with every required field populated is valid`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion())

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.missingFields)
    }

    @Test
    fun `wall route version missing only venueId reports only venueId`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(venueId = null))

        assertFalse(result.isValid)
        assertEquals(listOf("venueId"), result.missingFields)
    }

    @Test
    fun `wall route version missing only zoneId reports only zoneId`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(zoneId = null))

        assertFalse(result.isValid)
        assertEquals(listOf("zoneId"), result.missingFields)
    }

    @Test
    fun `wall route version missing only colorHex reports only colorHex`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(colorHex = null))

        assertFalse(result.isValid)
        assertEquals(listOf("colorHex"), result.missingFields)
    }

    @Test
    fun `wall route version missing only grade reports only grade`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(grade = null))

        assertFalse(result.isValid)
        assertEquals(listOf("grade"), result.missingFields)
    }

    @Test
    fun `wall route version missing only gradeSystem reports only gradeSystem`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(gradeSystem = null))

        assertFalse(result.isValid)
        assertEquals(listOf("gradeSystem"), result.missingFields)
    }

    @Test
    fun `wall route version missing only publicNumberOrName reports only publicNumberOrName`() {
        val result =
            RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(publicNumberOrName = null))

        assertFalse(result.isValid)
        assertEquals(listOf("publicNumberOrName"), result.missingFields)
    }

    @Test
    fun `wall route version missing only setAt reports only setAt`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(setAt = null))

        assertFalse(result.isValid)
        assertEquals(listOf("setAt"), result.missingFields)
    }

    @Test
    fun `wall route version missing only wallCalibrationId reports only wallCalibrationId`() {
        val result =
            RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(wallCalibrationId = null))

        assertFalse(result.isValid)
        assertEquals(listOf("wallCalibrationId"), result.missingFields)
    }

    @Test
    fun `wall route version missing only visionProfileId reports only visionProfileId`() {
        val result =
            RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(visionProfileId = null))

        assertFalse(result.isValid)
        assertEquals(listOf("visionProfileId"), result.missingFields)
    }

    @Test
    fun `wall route version missing only startPolicy reports only startPolicy`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(startPolicy = null))

        assertFalse(result.isValid)
        assertEquals(listOf("startPolicy"), result.missingFields)
    }

    @Test
    fun `wall route version missing only finishPolicy reports only finishPolicy`() {
        val result = RouteVersionSnapshotValidator.validate(completeWallRouteVersion().copy(finishPolicy = null))

        assertFalse(result.isValid)
        assertEquals(listOf("finishPolicy"), result.missingFields)
    }

    @Test
    fun `retiredAt is never required even when every other field is populated`() {
        val routeVersion = completeWallRouteVersion().copy(retiredAt = 3_000L)

        val result = RouteVersionSnapshotValidator.validate(routeVersion)

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.missingFields)
    }

    // --- validateDraft() ---

    @Test
    fun `a fully populated draft with no publicNumberOrName is valid`() {
        val draft = completeWallRouteVersion().copy(publicNumberOrName = null)

        val result = RouteVersionSnapshotValidator.validateDraft(draft)

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.missingFields)
    }

    @Test
    fun `a fully populated draft with a publicNumberOrName is also valid`() {
        val result = RouteVersionSnapshotValidator.validateDraft(completeWallRouteVersion())

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.missingFields)
    }

    @Test
    fun `validateDraft never lists publicNumberOrName as missing, even when every other field is also null`() {
        val bareDraft = completeWallRouteVersion().copy(
            venueId = null,
            zoneId = null,
            colorHex = null,
            grade = null,
            gradeSystem = null,
            publicNumberOrName = null,
            setAt = null,
            wallCalibrationId = null,
            visionProfileId = null,
            startPolicy = null,
            finishPolicy = null,
        )

        val result = RouteVersionSnapshotValidator.validateDraft(bareDraft)

        assertFalse(result.isValid)
        assertFalse(result.missingFields.contains("publicNumberOrName"))
        assertEquals(
            listOf("venueId", "zoneId", "colorHex", "grade", "gradeSystem", "setAt", "wallCalibrationId", "visionProfileId", "startPolicy", "finishPolicy"),
            result.missingFields,
        )
    }

    @Test
    fun `validateDraft requires wallId, unlike validate which exempts null wallId as legacy`() {
        val result = RouteVersionSnapshotValidator.validateDraft(legacyRouteVersion())

        assertFalse(result.isValid)
        assertTrue(result.missingFields.contains("wallId"))
    }

    @Test
    fun `validateDraft missing only setAt reports only setAt`() {
        val result = RouteVersionSnapshotValidator.validateDraft(completeWallRouteVersion().copy(setAt = null))

        assertFalse(result.isValid)
        assertEquals(listOf("setAt"), result.missingFields)
    }
}
