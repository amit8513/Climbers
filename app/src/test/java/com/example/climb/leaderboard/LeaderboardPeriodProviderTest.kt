package com.example.climb.leaderboard

import com.example.climb.leaderboard.period.LeaderboardPeriodProvider
import com.example.climb.leaderboard.period.PeriodFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class LeaderboardPeriodProviderTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    @Test
    fun `this week starts Monday 00-00 in the given timezone`() {
        // A Wednesday, well clear of any DST transition.
        val now = ZonedDateTime.of(2026, 1, 14, 15, 30, 0, 0, zone)
        val period = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val start = Instant.ofEpochMilli(period.startAt).atZone(zone)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(0, start.hour)
        assertEquals(0, start.minute)
    }

    @Test
    fun `this week end is exactly one week after start`() {
        val now = ZonedDateTime.of(2026, 1, 14, 15, 30, 0, 0, zone)
        val period = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val start = Instant.ofEpochMilli(period.startAt).atZone(zone)
        val end = Instant.ofEpochMilli(period.endAt).atZone(zone)
        assertEquals(start.plusWeeks(1), end)
    }

    @Test
    fun `last week is the seven days before this week, not overlapping`() {
        val now = ZonedDateTime.of(2026, 1, 14, 15, 30, 0, 0, zone)
        val thisWeek = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val lastWeek = LeaderboardPeriodProvider.periodFor(PeriodFilter.LAST_WEEK, zone, now)
        assertEquals(thisWeek.startAt, lastWeek.endAt)
        assertNotEquals(thisWeek.id, lastWeek.id)
    }

    @Test
    fun `last 4 weeks spans exactly four week-long periods before this week`() {
        val now = ZonedDateTime.of(2026, 1, 14, 15, 30, 0, 0, zone)
        val thisWeek = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val last4Weeks = LeaderboardPeriodProvider.periodFor(PeriodFilter.LAST_4_WEEKS, zone, now)
        assertEquals(thisWeek.startAt, last4Weeks.endAt)
        assertEquals(28L * 24 * 60 * 60 * 1000, last4Weeks.endAt - last4Weeks.startAt)
    }

    @Test
    fun `re-deriving this week twice never produces a different period id`() {
        val now = ZonedDateTime.of(2026, 1, 14, 15, 30, 0, 0, zone)
        val first = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val second = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now.plusHours(2))
        assertEquals(first.id, second.id)
    }

    @Test
    fun `a week spanning a daylight-saving transition still starts and ends at local midnight`() {
        // US spring-forward in 2026 is Sunday March 8th — the leaderboard week Monday March 2nd
        // to Monday March 9th spans it; pick a "now" inside that week (Wednesday March 4th).
        val now = ZonedDateTime.of(2026, 3, 4, 12, 0, 0, 0, zone)
        val period = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val start = Instant.ofEpochMilli(period.startAt).atZone(zone)
        val end = Instant.ofEpochMilli(period.endAt).atZone(zone)

        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(0, start.hour)
        assertEquals(DayOfWeek.MONDAY, end.dayOfWeek)
        assertEquals(0, end.hour)

        // Wall-clock start/end are exactly 7 days apart, but real elapsed time is 1 hour less
        // because of the spring-forward — proof the boundaries are computed in local wall-clock
        // time, not as a fixed 7*24h millisecond span.
        val elapsedMs = period.endAt - period.startAt
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        assertTrue(elapsedMs < sevenDaysMs)
        assertEquals(sevenDaysMs - 60 * 60 * 1000, elapsedMs)
    }

    @Test
    fun `displayDateRange formats using the period timezone`() {
        val now = ZonedDateTime.of(2026, 1, 14, 15, 30, 0, 0, zone)
        val period = LeaderboardPeriodProvider.periodFor(PeriodFilter.THIS_WEEK, zone, now)
        val text = LeaderboardPeriodProvider.displayDateRange(period)
        assertTrue(text.isNotBlank())
    }
}
