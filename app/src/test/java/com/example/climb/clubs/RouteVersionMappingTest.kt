package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises [routeVersionFromMap]/[RouteVersionEntity.toFirestoreMap] directly with hand-built
 * maps — [com.google.firebase.firestore.DocumentSnapshot] itself isn't constructible/fakeable in a
 * plain JVM unit test, so these pure functions (extracted from the `DocumentSnapshot` extension
 * specifically for this purpose) are what's actually testable.
 */
class RouteVersionMappingTest {

    @Test
    fun `an old route-version document (pre-Phase-1 shape) deserializes with every new field null`() {
        // Exactly what a route created before the gym-camera schema work existed looks like: only
        // the five original fields present.
        val oldShapeData = mapOf(
            "organizationId" to 1L,
            "routeId" to 2L,
            "setterUserId" to "staff-uid",
            "versionNumber" to 1L,
            "colorHex" to 0xFF0000L,
            "createdAt" to 1000L,
        )

        val entity = routeVersionFromMap(id = 99L, data = oldShapeData)

        requireNotNull(entity)
        assertEquals(99L, entity.id)
        assertEquals(1L, entity.organizationId)
        assertEquals(2L, entity.routeId)
        assertEquals("staff-uid", entity.setterUserId)
        assertEquals(1, entity.versionNumber)
        assertEquals(0xFF0000L, entity.colorHex)
        assertEquals(1000L, entity.createdAt)
        // Every field added by the gym-camera schema work must default to null for a document
        // that never wrote any of them.
        assertNull(entity.venueId)
        assertNull(entity.zoneId)
        assertNull(entity.wallId)
        assertNull(entity.grade)
        assertNull(entity.gradeSystem)
        assertNull(entity.publicNumberOrName)
        assertNull(entity.setAt)
        assertNull(entity.retiredAt)
        assertNull(entity.wallCalibrationId)
        assertNull(entity.visionProfileId)
        assertNull(entity.startPolicy)
        assertNull(entity.finishPolicy)
        // A real, pre-existing document with no registrationStatus field at all is a route that
        // was always active/offered - the deserialization-time legacy-compatibility default
        // (Phase 3A correction: this is now the ONLY place ACTIVE is ever defaulted to; the data
        // class itself has no default, see RouteVersionEntity.registrationStatus's doc comment).
        assertEquals(RouteRegistrationStatus.ACTIVE, entity.registrationStatus)
    }

    @Test
    fun `a document with an explicit DRAFT registrationStatus deserializes as DRAFT, not the legacy default`() {
        val draftShapeData = mapOf(
            "organizationId" to 1L,
            "routeId" to 2L,
            "setterUserId" to "staff-uid",
            "versionNumber" to 1L,
            "createdAt" to 1000L,
            "registrationStatus" to "DRAFT",
        )

        val entity = routeVersionFromMap(id = 99L, data = draftShapeData)

        assertEquals(RouteRegistrationStatus.DRAFT, entity?.registrationStatus)
    }

    @Test
    fun `an unparseable registrationStatus value falls back to the legacy ACTIVE default rather than crashing`() {
        val corruptShapeData = mapOf(
            "organizationId" to 1L,
            "routeId" to 2L,
            "setterUserId" to "staff-uid",
            "versionNumber" to 1L,
            "createdAt" to 1000L,
            "registrationStatus" to "NOT_A_REAL_VALUE",
        )

        val entity = routeVersionFromMap(id = 99L, data = corruptShapeData)

        assertEquals(RouteRegistrationStatus.ACTIVE, entity?.registrationStatus)
    }

    @Test
    fun `a document missing required fields deserializes to null rather than crashing`() {
        assertNull(routeVersionFromMap(id = 1L, data = emptyMap()))
        assertNull(routeVersionFromMap(id = 1L, data = mapOf("organizationId" to 1L))) // missing routeId etc.
    }

    @Test
    fun `a fully populated new RouteVersion round-trips through toFirestoreMap and back unchanged`() {
        val original = RouteVersionEntity(
            id = 42L,
            organizationId = 1L,
            routeId = 2L,
            setterUserId = "staff-uid",
            versionNumber = 3,
            colorHex = 0x00FF00L,
            createdAt = 1000L,
            venueId = 10L,
            zoneId = 20L,
            wallId = 30L,
            grade = 7,
            gradeSystem = "V_SCALE",
            publicNumberOrName = "Route 12",
            setAt = 2000L,
            retiredAt = null,
            wallCalibrationId = 40L,
            visionProfileId = 50L,
            startPolicy = StartPolicy.TWO_HOLDS_ONE_PER_HAND,
            finishPolicy = FinishPolicy.TWO_HANDS_ON_FINISH,
            registrationStatus = RouteRegistrationStatus.DRAFT,
        )

        val roundTripped = routeVersionFromMap(id = original.id, data = original.toFirestoreMap())

        assertEquals(original, roundTripped)
    }

    @Test
    fun `setAt is never populated from createdAt during round-trip`() {
        val original = RouteVersionEntity(
            id = 1L,
            organizationId = 1L,
            routeId = 1L,
            setterUserId = "u",
            versionNumber = 1,
            createdAt = 1000L,
            setAt = null,
            registrationStatus = RouteRegistrationStatus.ACTIVE,
        )
        val map = original.toFirestoreMap()
        assertNull("setAt must not be silently derived from createdAt in the serialized form", map["setAt"])

        val roundTripped = routeVersionFromMap(id = 1L, data = map)
        assertNull(roundTripped?.setAt)
        assertEquals(1000L, roundTripped?.createdAt)
    }
}
