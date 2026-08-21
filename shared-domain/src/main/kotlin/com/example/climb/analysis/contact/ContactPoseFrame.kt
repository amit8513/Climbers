package com.example.climb.analysis.contact

import com.example.climb.colordetection.Point2D

/**
 * The 14 BlazePose landmarks `HoldContactDetector` actually needs, named identically to the
 * relevant subset of `:app`'s `com.example.climb.pose.PoseLandmarkType` — so a future `:app`-side
 * adapter converting a real `com.example.climb.pose.PoseFrame` into a [ContactPoseFrame] is a
 * trivial 1:1 field mapping, never a re-implementation of any detection logic. This is the "narrow
 * portable pose-frame contract" that lets `:edge-agent` (which cannot depend on `:app`) drive the
 * exact same [com.example.climb.analysis.contact.HoldContactDetector] the member app's own future
 * adapter would — the algorithm lives once, here, in `:shared-domain`.
 */
enum class ContactLandmarkType {
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_INDEX, RIGHT_INDEX,
    LEFT_PINKY, RIGHT_PINKY,
    LEFT_THUMB, RIGHT_THUMB,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
}

/** One tracked landmark, in the capture frame's own (pre-transform) normalized [0,1] coordinate
 * space — never assumed to already be in `WallReferenceSpace` coordinates; see
 * `HoldContactDetector.processFrame`'s `transform` parameter. [confidence] blends whatever
 * upstream visibility/presence signals the real pipeline has into one number — how, is the
 * adapter's job, not this narrow contract's. */
data class ContactLandmark(val position: Point2D, val confidence: Float)

/** One instant's worth of the 14 [ContactLandmarkType] points, keyed by which ones were actually
 * resolved this frame — a landmark simply absent from [landmarks] (not merely low-confidence) is
 * how "MediaPipe didn't report this point at all this frame" is represented. */
data class ContactPoseFrame(
    val timestampMs: Long,
    val landmarks: Map<ContactLandmarkType, ContactLandmark> = emptyMap(),
) {
    fun landmark(type: ContactLandmarkType): ContactLandmark? = landmarks[type]
}
