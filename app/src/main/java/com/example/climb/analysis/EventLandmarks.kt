package com.example.climb.analysis

import com.example.climb.pose.PoseLandmarkType

/**
 * Which body landmarks a given event type is "about," so tapping a strength/issue/timeline row
 * tied to that event can highlight the relevant joints on the skeleton overlay instead of just
 * seeking the video.
 */
fun relatedLandmarksFor(type: ClimbEventType): Set<PoseLandmarkType> = when (type) {
    ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT, ClimbEventType.POSSIBLE_FOOT_SLIP,
    ClimbEventType.POSSIBLE_DISENGAGED_LEG, ClimbEventType.HIGH_STEP, ClimbEventType.LEG_DRIVE_CANDIDATE,
    -> setOf(
        PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.RIGHT_KNEE,
        PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE,
        PoseLandmarkType.LEFT_FOOT_INDEX, PoseLandmarkType.RIGHT_FOOT_INDEX,
    )
    ClimbEventType.SUSTAINED_LOCKOFF -> setOf(
        PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER,
        PoseLandmarkType.LEFT_ELBOW, PoseLandmarkType.RIGHT_ELBOW,
        PoseLandmarkType.LEFT_WRIST, PoseLandmarkType.RIGHT_WRIST,
    )
    ClimbEventType.POSSIBLE_MISSED_REACH -> setOf(PoseLandmarkType.LEFT_WRIST, PoseLandmarkType.RIGHT_WRIST)
    ClimbEventType.LARGE_DYNAMIC_MOVE, ClimbEventType.POSSIBLE_STABILITY_LOSS, ClimbEventType.POSSIBLE_FALL,
    ClimbEventType.RECOVERY, ClimbEventType.FINISH_STABILIZATION, ClimbEventType.EXCESSIVE_BODY_REPOSITIONING,
    -> setOf(PoseLandmarkType.LEFT_HIP, PoseLandmarkType.RIGHT_HIP)
    ClimbEventType.LONG_PAUSE, ClimbEventType.CLIMB_START, ClimbEventType.CLIMB_END,
    ClimbEventType.EFFICIENT_SEQUENCE, ClimbEventType.LOW_CONFIDENCE_RANGE,
    -> emptySet()
}

val FOOT_LEG_LANDMARKS: Set<PoseLandmarkType> = setOf(
    PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE,
    PoseLandmarkType.LEFT_FOOT_INDEX, PoseLandmarkType.RIGHT_FOOT_INDEX,
)

val ARM_LANDMARKS: Set<PoseLandmarkType> = setOf(
    PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_ELBOW, PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.LEFT_WRIST, PoseLandmarkType.RIGHT_WRIST,
)
