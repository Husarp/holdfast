package com.husarp.holdfast.data

import java.time.*

fun pauseDeadline(choice: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (choice) {
    "1 hour" -> now + 3_600_000L
    "Tomorrow at 08:00" -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    else -> Long.MAX_VALUE
}
fun effectiveDelay(settings: Settings, options: AppOptions) = options.returnDelayMinutes ?: settings.returnDelayMinutes
fun effectiveExpiry(settings: Settings, options: AppOptions) = if (options.overrideExpiry) options.expireAfterMinutes else settings.expireAfterMinutes
fun appSortRank(pinned: Boolean, recent: Boolean) = if (pinned) 0 else if (recent) 1 else 2
fun relativeAge(posted: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - posted).coerceAtLeast(0) / 60_000)
    return when { minutes < 1 -> "Just now"; minutes < 60 -> "$minutes min ago"; minutes < 1440 -> "${minutes / 60} h ago"; else -> "${minutes / 1440} d ago" }
}
