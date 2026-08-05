package com.example.climb.leaderboard.period

import com.example.climb.leaderboard.model.LeaderboardPeriod
import com.example.climb.leaderboard.model.PeriodStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class PeriodFilter(val label: String) {
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    LAST_4_WEEKS("Last 4 Weeks"),
}

/**
 * Weekly periods start Monday 00:00 in a given [ZoneId] — computed on demand from `ZonedDateTime`
 * arithmetic rather than persisted rows, since there's no backend to own that table yet (see
 * LEADERBOARD.md). Using `ZonedDateTime` throughout means DST transitions are handled correctly
 * for free: a "week" is always Monday-to-Monday in wall-clock time, never a fixed millisecond
 * span, so a week that crosses a DST change still starts and ends at local midnight.
 */
object LeaderboardPeriodProvider {

    fun periodFor(filter: PeriodFilter, zoneId: ZoneId = ZoneId.systemDefault(), now: ZonedDateTime = ZonedDateTime.now(zoneId)): LeaderboardPeriod {
        val thisWeekStart = mondayStartOf(now, zoneId)
        return when (filter) {
            PeriodFilter.THIS_WEEK -> weekPeriod(thisWeekStart, zoneId, now, filter.label)
            PeriodFilter.LAST_WEEK -> weekPeriod(thisWeekStart.minusWeeks(1), zoneId, now, filter.label)
            PeriodFilter.LAST_4_WEEKS -> rangePeriod(thisWeekStart.minusWeeks(4), thisWeekStart, zoneId, now, filter.label)
        }
    }

    /** The period immediately preceding [filter]'s current one, used to compute rank movement. */
    fun previousComparablePeriod(filter: PeriodFilter, zoneId: ZoneId = ZoneId.systemDefault(), now: ZonedDateTime = ZonedDateTime.now(zoneId)): LeaderboardPeriod {
        val thisWeekStart = mondayStartOf(now, zoneId)
        return when (filter) {
            PeriodFilter.THIS_WEEK -> weekPeriod(thisWeekStart.minusWeeks(1), zoneId, now, "Last Week")
            PeriodFilter.LAST_WEEK -> weekPeriod(thisWeekStart.minusWeeks(2), zoneId, now, "2 Weeks Ago")
            PeriodFilter.LAST_4_WEEKS -> rangePeriod(thisWeekStart.minusWeeks(8), thisWeekStart.minusWeeks(4), zoneId, now, "Previous 4 Weeks")
        }
    }

    fun displayDateRange(period: LeaderboardPeriod, zoneId: ZoneId = ZoneId.of(period.timezone)): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val start = Instant.ofEpochMilli(period.startAt).atZone(zoneId)
        val inclusiveEnd = Instant.ofEpochMilli(period.endAt).atZone(zoneId).minusDays(1)
        return "${start.format(formatter)} - ${inclusiveEnd.format(formatter)}"
    }

    private fun mondayStartOf(now: ZonedDateTime, zoneId: ZoneId): ZonedDateTime =
        now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(zoneId)

    private fun weekPeriod(weekStart: ZonedDateTime, zoneId: ZoneId, now: ZonedDateTime, label: String): LeaderboardPeriod {
        val weekEnd = weekStart.plusWeeks(1)
        return LeaderboardPeriod(
            id = isoWeekId(weekStart),
            startAt = weekStart.toInstant().toEpochMilli(),
            endAt = weekEnd.toInstant().toEpochMilli(),
            timezone = zoneId.id,
            label = label,
            status = statusFor(weekEnd, now),
            createdAt = weekStart.toInstant().toEpochMilli(),
        )
    }

    private fun rangePeriod(start: ZonedDateTime, end: ZonedDateTime, zoneId: ZoneId, now: ZonedDateTime, label: String): LeaderboardPeriod {
        return LeaderboardPeriod(
            id = "${isoWeekId(start)}_to_${isoWeekId(end.minusWeeks(1))}",
            startAt = start.toInstant().toEpochMilli(),
            endAt = end.toInstant().toEpochMilli(),
            timezone = zoneId.id,
            label = label,
            status = statusFor(end, now),
            createdAt = start.toInstant().toEpochMilli(),
        )
    }

    private fun statusFor(periodEnd: ZonedDateTime, now: ZonedDateTime): PeriodStatus = when {
        periodEnd.isAfter(now) -> PeriodStatus.ACTIVE
        periodEnd.plusMinutes(5).isAfter(now) -> PeriodStatus.CALCULATING
        else -> PeriodStatus.COMPLETE
    }

    private fun isoWeekId(weekStart: ZonedDateTime): String {
        val year = weekStart.get(WeekFields.ISO.weekBasedYear())
        val week = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())
        return String.format(Locale.US, "%d-W%02d", year, week)
    }
}
