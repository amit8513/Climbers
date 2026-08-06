package com.example.climb.analysis

enum class ClimbEventType {
    LONG_PAUSE,
    SUSTAINED_LOCKOFF,
    POSSIBLE_FOOT_ADJUSTMENT,
    POSSIBLE_FOOT_SLIP,
    POSSIBLE_DISENGAGED_LEG,
    LOW_CONFIDENCE_RANGE,
    EFFICIENT_SEQUENCE,
    EXCESSIVE_BODY_REPOSITIONING,
    LARGE_DYNAMIC_MOVE,
    CLIMB_START,
    CLIMB_END,
    HIGH_STEP,
    POSSIBLE_STABILITY_LOSS,
    RECOVERY,
    POSSIBLE_FALL,
    FINISH_STABILIZATION,
    POSSIBLE_MISSED_REACH,
}

data class ClimbEvent(
    val id: String,
    val type: ClimbEventType,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val peakTimestampMs: Long,
    val confidence: Float,
    /** 1 (minor) .. 3 (most notable) — how prominently the timeline should render this event. */
    val severity: Int,
    val metricValues: Map<String, Float> = emptyMap(),
    val userVisibleTitle: String,
    val userVisibleDescription: String,
)
