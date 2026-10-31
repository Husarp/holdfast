package com.husarp.holdfast.data

import java.time.*

data class ActivityStats(val events: List<NotificationEvent>, val held: Int, val opened: Int, val daily: List<Pair<LocalDate, Int>>)

fun activityStats(history: List<NotificationEvent>, opens: List<CopyOpen>, days: Int, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ActivityStats {
    require(days in listOf(7, 30))
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val first = today.minusDays(days - 1L)
    val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val events = history.filter { it.capturedAt in start..now }
    val counts = events.groupingBy { Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() }.eachCount()
    return ActivityStats(events, events.count { it.heldAsCopy }, opens.count { it.openedAt in start..now },
        (0 until days).map { first.plusDays(it.toLong()).let { date -> date to (counts[date] ?: 0) } })
}
