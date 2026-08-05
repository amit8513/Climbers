package com.example.climb.pose

/** Normalized [0,1] extent of the detected body within the frame, for framing/cropping UI later. */
data class BodyBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * One analyzed instant of the video. [isReliable] reflects whether enough of the required
 * landmarks were confidently detected for this frame to be trusted in metrics — callers should
 * exclude unreliable frames from measurements rather than silently including noisy data.
 */
data class PoseFrame(
    val timestampMs: Long,
    val landmarks: List<PoseLandmark>,
    val averageConfidence: Float,
    val isReliable: Boolean,
    val bodyBoundingBox: BodyBoundingBox?,
) {
    fun landmark(type: PoseLandmarkType): PoseLandmark? = landmarks.find { it.type == type }
}
