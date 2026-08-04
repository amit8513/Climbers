package com.example.climb.ui.progress

import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.util.startOfDay
import com.example.climb.util.startOfWeek

const val PYRAMID_WINDOW_DAYS = 90
const val WEEKS_IN_PROGRESSION = 8
const val WEEKS_IN_HEATMAP = 10
private const val DAY_MILLIS = 86_400_000L
private const val WEEK_MILLIS = 7 * DAY_MILLIS

data class HeadlineStats(
    val peakGrade: Int?,
    val sendRatePercent: Int?,
    val sessions: Int,
)

data class GradeSendRate(
    val grade: Int,
    val sends: Int,
    val attempts: Int,
) {
    val percent: Int get() = if (attempts == 0) 0 else (sends * 100) / attempts
}

fun headlineStats(climbs: List<ClimbEntity>): HeadlineStats {
    val sends = climbs.filter { it.outcome == ClimbOutcome.SENT }
    return HeadlineStats(
        peakGrade = sends.mapNotNull { it.vGrade }.maxOrNull(),
        sendRatePercent = if (climbs.isEmpty()) null else (sends.size * 100) / climbs.size,
        sessions = climbs.map { startOfDay(it.createdAt) }.distinct().size,
    )
}

/**
 * Sends per grade over the last [PYRAMID_WINDOW_DAYS] days only. A lifetime pyramid keeps
 * accumulating and stops reflecting current form, which is the whole point of the shape.
 */
fun gradePyramid(climbs: List<ClimbEntity>, now: Long): List<Pair<Int, Int>> {
    val cutoff = startOfDay(now) - (PYRAMID_WINDOW_DAYS - 1) * DAY_MILLIS
    return climbs
        .filter { it.outcome == ClimbOutcome.SENT && it.vGrade != null && it.createdAt >= cutoff }
        .groupBy { it.vGrade!! }
        .map { (grade, list) -> grade to list.size }
        .sortedByDescending { it.first }
}

/**
 * Share of attempts at each grade that ended in a send — the clearest signal of which grade
 * you're actually consolidating versus still projecting.
 */
fun sendRateByGrade(climbs: List<ClimbEntity>): List<GradeSendRate> =
    climbs.filter { it.vGrade != null }
        .groupBy { it.vGrade!! }
        .map { (grade, list) ->
            GradeSendRate(
                grade = grade,
                sends = list.count { it.outcome == ClimbOutcome.SENT },
                attempts = list.size,
            )
        }
        .sortedByDescending { it.grade }

/** Hardest grade sent in each of the last [WEEKS_IN_PROGRESSION] weeks; null for blank weeks. */
fun gradeProgression(climbs: List<ClimbEntity>, now: Long): List<Int?> {
    val currentWeekStart = startOfWeek(now)
    val peaks = arrayOfNulls<Int>(WEEKS_IN_PROGRESSION)
    for (climb in climbs) {
        if (climb.outcome != ClimbOutcome.SENT) continue
        val grade = climb.vGrade ?: continue
        val weeksAgo = ((currentWeekStart - startOfWeek(climb.createdAt)) / WEEK_MILLIS).toInt()
        if (weeksAgo !in 0 until WEEKS_IN_PROGRESSION) continue
        val index = WEEKS_IN_PROGRESSION - 1 - weeksAgo
        if (peaks[index] == null || grade > peaks[index]!!) {
            peaks[index] = grade
        }
    }
    return peaks.toList()
}

fun consistencyGrid(climbs: List<ClimbEntity>, now: Long): List<List<Int>> {
    val currentWeekStart = startOfWeek(now)
    val grid = Array(WEEKS_IN_HEATMAP) { IntArray(7) }
    for (climb in climbs) {
        val weeksAgo = ((currentWeekStart - startOfWeek(climb.createdAt)) / WEEK_MILLIS).toInt()
        if (weeksAgo !in 0 until WEEKS_IN_HEATMAP) continue
        val dayIndex = ((startOfDay(climb.createdAt) - startOfWeek(climb.createdAt)) / DAY_MILLIS).toInt()
        if (dayIndex in 0..6) {
            grid[WEEKS_IN_HEATMAP - 1 - weeksAgo][dayIndex]++
        }
    }
    return grid.map { it.toList() }
}
