package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.CategoryScore
import com.example.climb.analysis.scoring.PerformanceCategory
import com.example.climb.coaching.CoachingSource
import com.example.climb.coaching.CoachingTip
import org.json.JSONArray
import org.json.JSONObject
import kotlin.jvm.JvmName

fun ClimbMetrics.toJson(): String = JSONObject().apply {
    put("totalDurationMs", totalDurationMs)
    put("activeMovementMs", activeMovementMs)
    put("pauseTimeMs", pauseTimeMs)
    put("pauseCount", pauseCount)
    put("longestPauseMs", longestPauseMs)
    put("leftLockoffMs", leftLockoffMs)
    put("rightLockoffMs", rightLockoffMs)
    put("totalLockoffMs", totalLockoffMs)
    put("longestLockoffMs", longestLockoffMs)
    put("possibleFootAdjustments", possibleFootAdjustments)
    put("possibleFootSlips", possibleFootSlips)
    put("possibleDisengagedLegSegments", possibleDisengagedLegSegments)
    put("straightArmPercentage", straightArmPercentage.toDouble())
    put("estimatedMovementEfficiency", estimatedMovementEfficiency)
    put("reliableFramePercentage", reliableFramePercentage.toDouble())
    put("climbStartMs", climbStartMs)
    put("climbEndMs", climbEndMs)
    put("highStepCount", highStepCount)
    put("possibleStabilityLossCount", possibleStabilityLossCount)
    put("possibleFallCandidateCount", possibleFallCandidateCount)
    put("hasFinishStabilization", hasFinishStabilization)
    put("possibleMissedReachCount", possibleMissedReachCount)
}.toString()

fun String.toClimbMetrics(): ClimbMetrics? {
    if (isBlank()) return null
    val o = JSONObject(this)
    return ClimbMetrics(
        totalDurationMs = o.getLong("totalDurationMs"),
        activeMovementMs = o.getLong("activeMovementMs"),
        pauseTimeMs = o.getLong("pauseTimeMs"),
        pauseCount = o.getInt("pauseCount"),
        longestPauseMs = o.getLong("longestPauseMs"),
        leftLockoffMs = o.getLong("leftLockoffMs"),
        rightLockoffMs = o.getLong("rightLockoffMs"),
        totalLockoffMs = o.getLong("totalLockoffMs"),
        longestLockoffMs = o.getLong("longestLockoffMs"),
        possibleFootAdjustments = o.getInt("possibleFootAdjustments"),
        possibleFootSlips = o.getInt("possibleFootSlips"),
        possibleDisengagedLegSegments = o.optInt("possibleDisengagedLegSegments", 0),
        straightArmPercentage = o.getDouble("straightArmPercentage").toFloat(),
        estimatedMovementEfficiency = o.getInt("estimatedMovementEfficiency"),
        reliableFramePercentage = o.getDouble("reliableFramePercentage").toFloat(),
        climbStartMs = o.getLong("climbStartMs"),
        climbEndMs = o.getLong("climbEndMs"),
        highStepCount = o.optInt("highStepCount", 0),
        possibleStabilityLossCount = o.optInt("possibleStabilityLossCount", 0),
        possibleFallCandidateCount = o.optInt("possibleFallCandidateCount", 0),
        hasFinishStabilization = o.optBoolean("hasFinishStabilization", false),
        possibleMissedReachCount = o.optInt("possibleMissedReachCount", 0),
    )
}

@JvmName("climbEventsToJson")
fun List<ClimbEvent>.toJson(): String {
    val array = JSONArray()
    for (event in this) {
        array.put(
            JSONObject().apply {
                put("id", event.id)
                put("type", event.type.name)
                put("startTimestampMs", event.startTimestampMs)
                put("endTimestampMs", event.endTimestampMs)
                put("peakTimestampMs", event.peakTimestampMs)
                put("confidence", event.confidence.toDouble())
                put("severity", event.severity)
                put("title", event.userVisibleTitle)
                put("description", event.userVisibleDescription)
            },
        )
    }
    return array.toString()
}

fun String.toClimbEvents(): List<ClimbEvent> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).mapNotNull { i ->
        val o = array.getJSONObject(i)
        val type = runCatching { ClimbEventType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
        ClimbEvent(
            id = o.getString("id"),
            type = type,
            startTimestampMs = o.getLong("startTimestampMs"),
            endTimestampMs = o.getLong("endTimestampMs"),
            peakTimestampMs = o.getLong("peakTimestampMs"),
            confidence = o.getDouble("confidence").toFloat(),
            severity = o.getInt("severity"),
            userVisibleTitle = o.getString("title"),
            userVisibleDescription = o.getString("description"),
        )
    }
}

@JvmName("coachingTipsToJson")
fun List<CoachingTip>.toJson(): String {
    val array = JSONArray()
    for (tip in this) {
        array.put(
            JSONObject().apply {
                put("id", tip.id)
                put("category", tip.category)
                put("title", tip.title)
                put("explanation", tip.explanation)
                put("drill", tip.drill)
                put("timestampMs", tip.timestampMs)
                put("confidence", tip.confidence.toDouble())
                put("priority", tip.priority)
                put("evidence", tip.evidence)
                put("source", tip.source.name)
            },
        )
    }
    return array.toString()
}

@JvmName("climbPhasesToJson")
fun List<ClimbPhase>.toJson(): String {
    val array = JSONArray()
    for (phase in this) {
        array.put(
            JSONObject().apply {
                put("type", phase.type.name)
                put("startMs", phase.startMs)
                put("endMs", phase.endMs)
                put("confidence", phase.confidence.toDouble())
                put("supportingSignals", phase.supportingSignals)
            },
        )
    }
    return array.toString()
}

fun String.toClimbPhases(): List<ClimbPhase> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).mapNotNull { i ->
        val o = array.getJSONObject(i)
        val type = runCatching { ClimbPhaseType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
        ClimbPhase(
            type = type,
            startMs = o.getLong("startMs"),
            endMs = o.getLong("endMs"),
            confidence = o.getDouble("confidence").toFloat(),
            supportingSignals = o.getString("supportingSignals"),
        )
    }
}

@JvmName("categoryScoresToJson")
fun List<CategoryScore>.toJson(): String {
    val array = JSONArray()
    for (categoryScore in this) {
        array.put(
            JSONObject().apply {
                put("category", categoryScore.category.name)
                put("score", categoryScore.score)
                put("confidence", categoryScore.confidence.toDouble())
                put("contributingMetrics", JSONArray(categoryScore.contributingMetrics))
                put("positiveFactors", JSONArray(categoryScore.positiveFactors))
                put("negativeFactors", JSONArray(categoryScore.negativeFactors))
                put("unavailableFactors", JSONArray(categoryScore.unavailableFactors))
                put("explanation", categoryScore.explanation)
            },
        )
    }
    return array.toString()
}

fun String.toCategoryScores(): List<CategoryScore> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).mapNotNull { i ->
        val o = array.getJSONObject(i)
        val category = runCatching { PerformanceCategory.valueOf(o.getString("category")) }.getOrNull() ?: return@mapNotNull null
        fun stringList(key: String) = o.getJSONArray(key).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        CategoryScore(
            category = category,
            score = o.getInt("score"),
            confidence = o.getDouble("confidence").toFloat(),
            contributingMetrics = stringList("contributingMetrics"),
            positiveFactors = stringList("positiveFactors"),
            negativeFactors = stringList("negativeFactors"),
            unavailableFactors = stringList("unavailableFactors"),
            explanation = o.getString("explanation"),
        )
    }
}

fun String.toCoachingTips(): List<CoachingTip> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        CoachingTip(
            id = o.getString("id"),
            category = o.getString("category"),
            title = o.getString("title"),
            explanation = o.getString("explanation"),
            drill = if (o.isNull("drill")) null else o.getString("drill"),
            timestampMs = if (o.has("timestampMs") && !o.isNull("timestampMs")) o.getLong("timestampMs") else null,
            confidence = o.getDouble("confidence").toFloat(),
            priority = o.getInt("priority"),
            evidence = o.getString("evidence"),
            source = runCatching { CoachingSource.valueOf(o.getString("source")) }.getOrDefault(CoachingSource.DETERMINISTIC),
        )
    }
}
