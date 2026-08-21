package com.example.climb.edge

/**
 * A still reference frame written to local storage by a `CameraSourceAdapter`
 * (`captureStillReferenceFrame()`, :edge-agent). Holds a file path rather than raw bytes — the
 * same pattern `ClubVideoAsset` uses for video (a storage path, not inline binary data) — so this
 * stays a small, cheap-to-pass value even though the underlying file may be several MB.
 */
data class CapturedFrame(
    val filePath: String,
    val fileSizeBytes: Long,
    val metadata: ReferenceFrameMetadata,
) {
    init {
        require(filePath.isNotBlank()) { "filePath must not be blank" }
        require(fileSizeBytes >= 0) { "fileSizeBytes must not be negative" }
    }
}
