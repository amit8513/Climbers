package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType

data class DisengagedLegSegment(val side: Side, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

private data class LegJoints(val hip: PoseLandmarkType, val knee: PoseLandmarkType, val ankle: PoseLandmarkType)

private fun legJoints(side: Side): LegJoints = when (side) {
    Side.LEFT -> LegJoints(PoseLandmarkType.LEFT_HIP, PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.LEFT_ANKLE)
    Side.RIGHT -> LegJoints(PoseLandmarkType.RIGHT_HIP, PoseLandmarkType.RIGHT_KNEE, PoseLandmarkType.RIGHT_ANKLE)
}

private fun kneeAngleOrNull(frame: PoseFrame, joints: LegJoints): Float? {
    val hip = frame.landmark(joints.hip) ?: return null
    val knee = frame.landmark(joints.knee) ?: return null
    val ankle = frame.landmark(joints.ankle) ?: return null
    return kneeAngleDegrees(hip, knee, ankle)
}

/**
 * Pose landmarks alone can't tell whether a foot is actually pressed against the wall or a hold
 * — there's no hold data and no true depth sensing here. What this detects instead is a proxy
 * that matches the same real-world situation: one leg staying noticeably straighter/more extended
 * than the other for a sustained stretch, which is the typical signature of a leg hanging free
 * (unweighted) rather than pushing into a foothold. This also fires on an intentional flag (a
 * straight leg extended for balance/counterbalance), which is a normal, fine technique — so the
 * event is always worded as a neutral observation, never a flaw, and callers should not treat it
 * as a confirmed "leg wasn't on the wall."
 */
fun detectDisengagedLeg(frames: List<PoseFrame>, config: MetricsConfiguration): List<DisengagedLegSegment> {
    val segments = mutableListOf<DisengagedLegSegment>()
    for (side in Side.entries) {
        val joints = legJoints(side)
        val otherJoints = legJoints(if (side == Side.LEFT) Side.RIGHT else Side.LEFT)
        var segmentStart: Long? = null
        var lastTimestamp: Long? = null

        fun flush() {
            val start = segmentStart
            val end = lastTimestamp
            if (start != null && end != null && end - start >= config.minDisengagedLegDurationMs) {
                segments += DisengagedLegSegment(side, start, end)
            }
            segmentStart = null
            lastTimestamp = null
        }

        for (frame in frames) {
            val thisAngle = if (frame.isReliable) kneeAngleOrNull(frame, joints) else null
            val otherAngle = if (frame.isReliable) kneeAngleOrNull(frame, otherJoints) else null
            val isAsymmetricallyStraight = thisAngle != null && otherAngle != null &&
                thisAngle >= config.disengagedLegStraightAngleDegrees &&
                (thisAngle - otherAngle) >= config.disengagedLegAngleDifferenceDegrees
            if (isAsymmetricallyStraight) {
                if (segmentStart == null) segmentStart = frame.timestampMs
                lastTimestamp = frame.timestampMs
            } else {
                flush()
            }
        }
        flush()
    }
    return segments.sortedBy { it.startMs }
}
