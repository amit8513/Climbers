package com.example.climb.edge

import org.junit.Assert.assertThrows
import org.junit.Test

class UploadResultTest {

    @Test
    fun `success requires a remoteReference and no errorMessage`() {
        UploadResult(success = true, remoteReference = "/local/uploaded/a.jpg", errorMessage = null)
    }

    @Test
    fun `failure requires an errorMessage and no remoteReference`() {
        UploadResult(success = false, remoteReference = null, errorMessage = "boom")
    }

    @Test
    fun `rejects success with no remoteReference`() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadResult(success = true, remoteReference = null, errorMessage = null)
        }
    }

    @Test
    fun `rejects success with an errorMessage`() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadResult(success = true, remoteReference = "/local/uploaded/a.jpg", errorMessage = "boom")
        }
    }

    @Test
    fun `rejects failure with a remoteReference`() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadResult(success = false, remoteReference = "/local/uploaded/a.jpg", errorMessage = "boom")
        }
    }

    @Test
    fun `rejects failure with no errorMessage`() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadResult(success = false, remoteReference = null, errorMessage = null)
        }
    }
}
