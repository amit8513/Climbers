package com.example.climb.edge

class CameraCaptureConfigTest {

    @org.junit.Test
    fun `defaults delegate to the default geometry profile`() {
        val config = CameraCaptureConfig()
        assert(config.targetWidthPx == 1920)
        assert(config.targetHeightPx == 1080)
        assert(config.targetRotationDegrees == 0)
        assert(!config.mirrored)
        assert(config.version == 1)
    }

    @org.junit.Test
    fun `properties reflect a non-default geometry profile rather than inventing their own values`() {
        val profile = CameraGeometryProfile(
            requestedWidthPx = 1280,
            requestedHeightPx = 720,
            requestedRotationDegrees = 90,
            version = 2,
        )
        val config = CameraCaptureConfig(geometryProfile = profile)

        assert(config.targetWidthPx == 1280)
        assert(config.targetHeightPx == 720)
        assert(config.targetRotationDegrees == 90)
        assert(config.version == 2)
    }
}
