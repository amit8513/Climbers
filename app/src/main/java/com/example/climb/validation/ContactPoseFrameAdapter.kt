package com.example.climb.validation

import com.example.climb.analysis.contact.ContactLandmark
import com.example.climb.analysis.contact.ContactLandmarkType
import com.example.climb.analysis.contact.ContactPoseFrame
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType

/** The exact 1:1 field mapping `ContactPoseFrame`'s own doc comment anticipated — every
 * [ContactLandmarkType] is named identically to the [PoseLandmarkType] it corresponds to. */
private val LANDMARK_TYPE_MAP: Map<ContactLandmarkType, PoseLandmarkType> = mapOf(
    ContactLandmarkType.LEFT_WRIST to PoseLandmarkType.LEFT_WRIST,
    ContactLandmarkType.RIGHT_WRIST to PoseLandmarkType.RIGHT_WRIST,
    ContactLandmarkType.LEFT_INDEX to PoseLandmarkType.LEFT_INDEX,
    ContactLandmarkType.RIGHT_INDEX to PoseLandmarkType.RIGHT_INDEX,
    ContactLandmarkType.LEFT_PINKY to PoseLandmarkType.LEFT_PINKY,
    ContactLandmarkType.RIGHT_PINKY to PoseLandmarkType.RIGHT_PINKY,
    ContactLandmarkType.LEFT_THUMB to PoseLandmarkType.LEFT_THUMB,
    ContactLandmarkType.RIGHT_THUMB to PoseLandmarkType.RIGHT_THUMB,
    ContactLandmarkType.LEFT_ANKLE to PoseLandmarkType.LEFT_ANKLE,
    ContactLandmarkType.RIGHT_ANKLE to PoseLandmarkType.RIGHT_ANKLE,
    ContactLandmarkType.LEFT_HEEL to PoseLandmarkType.LEFT_HEEL,
    ContactLandmarkType.RIGHT_HEEL to PoseLandmarkType.RIGHT_HEEL,
    ContactLandmarkType.LEFT_FOOT_INDEX to PoseLandmarkType.LEFT_FOOT_INDEX,
    ContactLandmarkType.RIGHT_FOOT_INDEX to PoseLandmarkType.RIGHT_FOOT_INDEX,
)

/**
 * The app-side adapter `ContactPoseFrame`'s own doc comment anticipated ("a trivial 1:1 field
 * mapping") — converts one real, MediaPipe-derived [PoseFrame] into the narrow, portable
 * `ContactPoseFrame` contract `HoldContactDetector` consumes. Pure data mapping only, no contact
 * logic of any kind lives here. [ContactLandmark.confidence] blends MediaPipe's two distinct
 * signals (visibility: in-frame at all; presence: not occluded) by taking the weaker of the two —
 * either one failing is a real reason not to trust the point.
 */
fun PoseFrame.toContactPoseFrame(): ContactPoseFrame {
    val landmarks = LANDMARK_TYPE_MAP.mapNotNull { (contactType, poseType) ->
        val source = landmark(poseType) ?: return@mapNotNull null
        contactType to ContactLandmark(
            position = Point2D(source.normalizedX, source.normalizedY),
            confidence = minOf(source.visibility, source.presence),
        )
    }.toMap()
    return ContactPoseFrame(timestampMs = timestampMs, landmarks = landmarks)
}
