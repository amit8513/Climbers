package com.example.climb.pose

/**
 * The 33-point BlazePose body topology used by MediaPipe's Pose Landmarker, in the exact
 * index order MediaPipe returns landmarks in — so a result list can be zipped with
 * `PoseLandmarkType.entries` directly. Kept as an app-owned type so no MediaPipe class ever
 * escapes the [com.example.climb.pose.MediaPipePoseEstimator] boundary.
 */
enum class PoseLandmarkType {
    NOSE,
    LEFT_EYE_INNER, LEFT_EYE, LEFT_EYE_OUTER,
    RIGHT_EYE_INNER, RIGHT_EYE, RIGHT_EYE_OUTER,
    LEFT_EAR, RIGHT_EAR,
    MOUTH_LEFT, MOUTH_RIGHT,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_PINKY, RIGHT_PINKY,
    LEFT_INDEX, RIGHT_INDEX,
    LEFT_THUMB, RIGHT_THUMB,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
}

/**
 * One tracked body point. Coordinates are normalized to [0, 1] against the video's displayed
 * (already rotation-corrected) frame — (0,0) top-left, (1,1) bottom-right.
 *
 * [visibility] is MediaPipe's estimate of whether the point is within the frame at all;
 * [presence] is its estimate of whether the point is actually visible (not occluded). Both
 * matter separately: a foot that's off-camera and a foot that's hidden behind the climber's
 * own leg fail for different reasons.
 */
data class PoseLandmark(
    val type: PoseLandmarkType,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedZ: Float,
    val visibility: Float,
    val presence: Float,
)
