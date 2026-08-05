package com.example.climb.analysis

import com.example.climb.analysis.metrics.AnalysisComputation
import com.example.climb.analysis.metrics.MetricsConfiguration
import com.example.climb.analysis.metrics.Side
import com.example.climb.analysis.metrics.TimedVelocity
import com.example.climb.analysis.metrics.computeHipVelocities
import com.example.climb.analysis.metrics.hipCenter
import com.example.climb.analysis.metrics.smoothVelocities
import com.example.climb.pose.PoseFrame

private fun clusterByTime(timestamps: List<Long>, gapMs: Long): List<List<Long>> {
    if (timestamps.isEmpty()) return emptyList()
    val sorted = timestamps.sorted()
    val clusters = mutableListOf(mutableListOf(sorted.first()))
    for (i in 1 until sorted.size) {
        if (sorted[i] - sorted[i - 1] <= gapMs) clusters.last().add(sorted[i]) else clusters += mutableListOf(sorted[i])
    }
    return clusters
}

private fun mergeRanges(ranges: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.first }
    val merged = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val last = merged.last()
        val curr = sorted[i]
        if (curr.first <= last.second) {
            merged[merged.size - 1] = last.first to maxOf(last.second, curr.second)
        } else {
            merged += curr
        }
    }
    return merged
}

private fun lowConfidenceRanges(frames: List<PoseFrame>, config: MetricsConfiguration): List<Pair<Long, Long>> {
    val ranges = mutableListOf<Pair<Long, Long>>()
    var start: Long? = null
    var last: Long? = null
    fun flush() {
        if (start != null && last != null && last!! - start!! >= config.lowConfidenceMinRangeMs) ranges += start!! to last!!
        start = null
        last = null
    }
    for (frame in frames) {
        if (!frame.isReliable) {
            if (start == null) start = frame.timestampMs
            last = frame.timestampMs
        } else {
            flush()
        }
    }
    flush()
    return ranges
}

private fun largeDynamicMoves(velocities: List<TimedVelocity>, config: MetricsConfiguration): List<Long> {
    val peaks = mutableListOf<Long>()
    var lastPeakMs = Long.MIN_VALUE
    for (i in velocities.indices) {
        val v = velocities[i]
        if (v.normalizedVelocity < config.dynamicMoveVelocityThreshold) continue
        val isLocalMax = (i == 0 || velocities[i - 1].normalizedVelocity <= v.normalizedVelocity) &&
            (i == velocities.lastIndex || velocities[i + 1].normalizedVelocity <= v.normalizedVelocity)
        if (isLocalMax && v.timestampMs - lastPeakMs > 500L) {
            peaks += v.timestampMs
            lastPeakMs = v.timestampMs
        }
    }
    return peaks
}

/** Counts hip vertical-direction reversals in a sliding window — a lot of back-and-forth in a
 * short span reads as indecisive repositioning rather than committed movement. */
private fun excessiveRepositioningWindows(frames: List<PoseFrame>, config: MetricsConfiguration): List<Pair<Long, Long>> {
    val hipYs = frames.mapNotNull { frame -> frame.hipCenter()?.let { frame.timestampMs to it.y } }
    if (hipYs.size < 3) return emptyList()
    val directions = mutableListOf<Pair<Long, Int>>()
    for (i in 1 until hipYs.size) {
        val delta = hipYs[i].second - hipYs[i - 1].second
        val sign = when {
            delta > 0.001f -> 1
            delta < -0.001f -> -1
            else -> 0
        }
        if (sign != 0) directions += hipYs[i].first to sign
    }
    val windows = mutableListOf<Pair<Long, Long>>()
    var windowStart = 0
    for (i in directions.indices) {
        while (directions[i].first - directions[windowStart].first > config.repositioningWindowMs) windowStart++
        var reversals = 0
        for (j in windowStart + 1..i) {
            if (directions[j].second != directions[j - 1].second) reversals++
        }
        if (reversals >= config.repositioningReversalCount) {
            windows += directions[windowStart].first to directions[i].first
        }
    }
    return mergeRanges(windows)
}

/** The longest stretch of the climb not covered by a pause or a lock-off — a simple proxy for
 * "smooth continuous movement," not a rigorous technique judgment. */
private fun efficientSequence(computation: AnalysisComputation): Pair<Long, Long>? {
    val metrics = computation.metrics
    val excluded = mergeRanges(
        computation.pauses.map { it.startMs to it.endMs } + computation.lockoffs.map { it.startMs to it.endMs },
    ).sortedBy { it.first }

    var cursor = metrics.climbStartMs
    var best: Pair<Long, Long>? = null
    for (range in excluded) {
        if (range.first > cursor) {
            val candidate = cursor to minOf(range.first, metrics.climbEndMs)
            if (candidate.second > candidate.first && (best == null || candidate.second - candidate.first > best.second - best.first)) best = candidate
        }
        cursor = maxOf(cursor, range.second)
    }
    if (cursor < metrics.climbEndMs) {
        val candidate = cursor to metrics.climbEndMs
        if (best == null || candidate.second - candidate.first > best.second - best.first) best = candidate
    }
    return best?.takeIf { it.second - it.first >= 2_000L }
}

/** Turns raw detections into the timestamped, user-facing [ClimbEvent] list shown on the
 * timeline. Each detector already avoids overlaps within its own type, so no further
 * cross-type merge pass is needed here. */
fun buildEvents(frames: List<PoseFrame>, computation: AnalysisComputation, config: MetricsConfiguration = MetricsConfiguration()): List<ClimbEvent> {
    val metrics = computation.metrics
    val events = mutableListOf<ClimbEvent>()

    events += ClimbEvent(
        id = "climb_start",
        type = ClimbEventType.CLIMB_START,
        startTimestampMs = metrics.climbStartMs,
        endTimestampMs = metrics.climbStartMs,
        peakTimestampMs = metrics.climbStartMs,
        confidence = 1f,
        severity = 1,
        userVisibleTitle = "Climb starts",
        userVisibleDescription = "First sustained movement detected at ${formatTimestampMs(metrics.climbStartMs)}.",
    )
    events += ClimbEvent(
        id = "climb_end",
        type = ClimbEventType.CLIMB_END,
        startTimestampMs = metrics.climbEndMs,
        endTimestampMs = metrics.climbEndMs,
        peakTimestampMs = metrics.climbEndMs,
        confidence = 1f,
        severity = 1,
        userVisibleTitle = "Climb ends",
        userVisibleDescription = "Last significant movement detected at ${formatTimestampMs(metrics.climbEndMs)}.",
    )

    computation.pauses.filter { it.durationMs >= config.longPauseDurationMs }.forEachIndexed { index, pause ->
        events += ClimbEvent(
            id = "long_pause_$index",
            type = ClimbEventType.LONG_PAUSE,
            startTimestampMs = pause.startMs,
            endTimestampMs = pause.endMs,
            peakTimestampMs = pause.startMs + pause.durationMs / 2,
            confidence = 0.8f,
            severity = if (pause.durationMs >= config.longPauseDurationMs * 2) 3 else 2,
            metricValues = mapOf("durationMs" to pause.durationMs.toFloat()),
            userVisibleTitle = "Long pause",
            userVisibleDescription = "Paused for ${"%.1f".format(pause.durationMs / 1000f)}s around ${formatTimestampMs(pause.startMs)}.",
        )
    }

    computation.lockoffs.forEachIndexed { index, lockoff ->
        val side = if (lockoff.side == Side.LEFT) "left" else "right"
        events += ClimbEvent(
            id = "lockoff_${lockoff.side}_$index",
            type = ClimbEventType.SUSTAINED_LOCKOFF,
            startTimestampMs = lockoff.startMs,
            endTimestampMs = lockoff.endMs,
            peakTimestampMs = lockoff.startMs + lockoff.durationMs / 2,
            confidence = 0.7f,
            severity = if (lockoff.durationMs >= config.minSustainedLockoffMs * 2) 2 else 1,
            metricValues = mapOf("durationMs" to lockoff.durationMs.toFloat()),
            userVisibleTitle = "${side.replaceFirstChar { it.uppercase() }}-arm lock-off",
            userVisibleDescription = "Held a bent-arm position on your $side arm for ${"%.1f".format(lockoff.durationMs / 1000f)}s.",
        )
    }

    clusterByTime(computation.footAdjustments.map { it.timestampMs }, gapMs = 1_000L).forEachIndexed { index, cluster ->
        events += ClimbEvent(
            id = "foot_adjustment_$index",
            type = ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT,
            startTimestampMs = cluster.first(),
            endTimestampMs = cluster.last(),
            peakTimestampMs = cluster.first(),
            confidence = 0.5f,
            severity = if (cluster.size > 1) 2 else 1,
            metricValues = mapOf("count" to cluster.size.toFloat()),
            userVisibleTitle = if (cluster.size > 1) "Possible foot adjustments" else "Possible foot adjustment",
            userVisibleDescription = "Foot repositioning around ${formatTimestampMs(cluster.first())}.",
        )
    }

    clusterByTime(computation.footSlips, gapMs = 400L).forEachIndexed { index, cluster ->
        events += ClimbEvent(
            id = "foot_slip_$index",
            type = ClimbEventType.POSSIBLE_FOOT_SLIP,
            startTimestampMs = cluster.first(),
            endTimestampMs = cluster.last(),
            peakTimestampMs = cluster.first(),
            confidence = 0.4f,
            severity = 2,
            userVisibleTitle = "Possible foot slip",
            userVisibleDescription = "A sudden foot movement near ${formatTimestampMs(cluster.first())} — may be a slip.",
        )
    }

    computation.disengagedLegs.forEachIndexed { index, segment ->
        val side = if (segment.side == Side.LEFT) "left" else "right"
        events += ClimbEvent(
            id = "disengaged_leg_${segment.side}_$index",
            type = ClimbEventType.POSSIBLE_DISENGAGED_LEG,
            startTimestampMs = segment.startMs,
            endTimestampMs = segment.endMs,
            peakTimestampMs = segment.startMs + segment.durationMs / 2,
            confidence = 0.4f,
            severity = 1,
            metricValues = mapOf("durationMs" to segment.durationMs.toFloat()),
            userVisibleTitle = "${side.replaceFirstChar { it.uppercase() }} leg extended, not weighted",
            userVisibleDescription = "Your $side leg stayed noticeably straighter than your other leg for ${"%.1f".format(segment.durationMs / 1000f)}s around ${formatTimestampMs(segment.startMs)} — pose tracking can't confirm whether it was on a hold, off the wall, or held out for balance (e.g. a flag).",
        )
    }

    lowConfidenceRanges(frames, config).forEachIndexed { index, (start, end) ->
        events += ClimbEvent(
            id = "low_confidence_$index",
            type = ClimbEventType.LOW_CONFIDENCE_RANGE,
            startTimestampMs = start,
            endTimestampMs = end,
            peakTimestampMs = start,
            confidence = 1f,
            severity = 1,
            userVisibleTitle = "Lower tracking confidence",
            userVisibleDescription = "Some body landmarks weren't tracked reliably between ${formatTimestampMs(start)} and ${formatTimestampMs(end)}.",
        )
    }

    val velocities = smoothVelocities(computeHipVelocities(frames))
    largeDynamicMoves(velocities, config).forEachIndexed { index, timestamp ->
        events += ClimbEvent(
            id = "dynamic_move_$index",
            type = ClimbEventType.LARGE_DYNAMIC_MOVE,
            startTimestampMs = timestamp,
            endTimestampMs = timestamp,
            peakTimestampMs = timestamp,
            confidence = 0.6f,
            severity = 2,
            userVisibleTitle = "Dynamic move",
            userVisibleDescription = "A fast, powerful movement around ${formatTimestampMs(timestamp)}.",
        )
    }

    excessiveRepositioningWindows(frames, config).forEachIndexed { index, (start, end) ->
        events += ClimbEvent(
            id = "repositioning_$index",
            type = ClimbEventType.EXCESSIVE_BODY_REPOSITIONING,
            startTimestampMs = start,
            endTimestampMs = end,
            peakTimestampMs = start,
            confidence = 0.5f,
            severity = 2,
            userVisibleTitle = "Repeated repositioning",
            userVisibleDescription = "Several quick direction changes between ${formatTimestampMs(start)} and ${formatTimestampMs(end)}.",
        )
    }

    efficientSequence(computation)?.let { (start, end) ->
        events += ClimbEvent(
            id = "efficient_sequence",
            type = ClimbEventType.EFFICIENT_SEQUENCE,
            startTimestampMs = start,
            endTimestampMs = end,
            peakTimestampMs = start,
            confidence = 0.6f,
            severity = 1,
            userVisibleTitle = "Efficient sequence",
            userVisibleDescription = "Smooth, continuous movement between ${formatTimestampMs(start)} and ${formatTimestampMs(end)}.",
        )
    }

    return events.sortedBy { it.startTimestampMs }
}
