package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * High-frequency, potentially-offline-created capture entities must use a client-generatable
 * String id (UUID/ULID), never the staff-driven `nextId()` Long counter — a regression guard
 * against ever reverting one of these back to a global counter id. Plain Java reflection (not
 * kotlin-reflect, which this module doesn't otherwise need) — a Kotlin data class property
 * compiles to a private field of the same name.
 */
class CaptureEntityIdSchemeTest {

    private fun assertStringId(kClass: Class<*>) {
        val idField = kClass.getDeclaredField("id")
        assertEquals("${kClass.simpleName}.id must be String, not a nextId()-style Long", String::class.java, idField.type)
    }

    @Test
    fun `WallCaptureSession id is a String`() = assertStringId(WallCaptureSession::class.java)

    @Test
    fun `ClubVideoAsset id is a String`() = assertStringId(ClubVideoAsset::class.java)

    @Test
    fun `PoseArtifactEntity id is a String`() = assertStringId(PoseArtifactEntity::class.java)

    @Test
    fun `RouteAttributionResultEntity id is a String`() = assertStringId(RouteAttributionResultEntity::class.java)

    @Test
    fun `MemberCaptureInboxItem id is a String`() = assertStringId(MemberCaptureInboxItem::class.java)

    @Test
    fun `staff-driven registry entities still use Long nextId-style ids, by contrast`() {
        val idField = WallEntity::class.java.getDeclaredField("id")
        assertEquals(Long::class.javaPrimitiveType, idField.type)
    }
}
