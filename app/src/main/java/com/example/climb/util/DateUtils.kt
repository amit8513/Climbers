package com.example.climb.util

import java.util.Calendar

private const val DAY_MILLIS = 86_400_000L

fun startOfDay(millis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun daysBetween(now: Long, then: Long): Int =
    ((startOfDay(now) - startOfDay(then)) / DAY_MILLIS).toInt()

/** Start of the Sunday-to-Saturday week containing [millis], independent of device locale. */
fun startOfWeek(millis: Long): Long {
    val dayStart = startOfDay(millis)
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = dayStart
    val daysSinceSunday = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    return dayStart - daysSinceSunday * DAY_MILLIS
}
