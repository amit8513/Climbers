package com.example.climb.edge

/**
 * Outcome of handing a [CapturedFrame] to whatever "upload" means today. [remoteReference] is
 * populated only on success (a real Cloud Storage path once one exists; a local copy's path for
 * now — see `LocalCopyUploader` in :edge-agent), [errorMessage] only on failure — never both null
 * or both non-null.
 */
data class UploadResult(
    val success: Boolean,
    val remoteReference: String?,
    val errorMessage: String?,
) {
    init {
        require(success == (remoteReference != null)) {
            "remoteReference must be non-null if and only if success is true"
        }
        require(success != (errorMessage != null)) {
            "errorMessage must be non-null if and only if success is false"
        }
    }
}

/**
 * What happens to a [CapturedFrame] after capture. No real network transport/Cloud Storage
 * integration exists yet — see `LocalCopyUploader` in :edge-agent, the only implementation for
 * this phase.
 */
interface ReferenceFrameUploader {
    suspend fun upload(frame: CapturedFrame): UploadResult
}
