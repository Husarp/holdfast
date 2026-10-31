package com.husarp.holdfast.data

import java.time.*

val buttonChoices = listOf("dismiss", "snooze:60", "until:18:00", "tomorrow:08:00", "openKeep", "done", "muteType", "muteToday", "copyText", "reply", "original")
fun buttonLabel(button: String): String = when {
    button.startsWith("original:") -> "Original action ${(button.substringAfter(':').toIntOrNull() ?: 0) + 1}"
    button.startsWith("snooze:") -> "Snooze ${button.substringAfter(':')}m"
    button.startsWith("until:") -> "Snooze until ${button.substringAfter(':')}"
    button.startsWith("tomorrow:") -> "Tomorrow ${button.substringAfter(':')}"
    else -> mapOf("dismiss" to "Dismiss", "openKeep" to "Open and keep", "done" to "Done", "muteType" to "Mute this type", "muteToday" to "Mute app for today", "copyText" to "Copy text", "reply" to "Reply", "original" to "Original's buttons")[button] ?: button
}
fun validButton(button: String): Boolean = when {
    button.startsWith("snooze:") -> (button.substringAfter(':').toIntOrNull() ?: 0) in 1..525600
    (button.startsWith("until:") || button.startsWith("tomorrow:")) -> try { LocalTime.parse(button.substringAfter(':')); true } catch (_: Exception) { false }
    else -> button in buttonChoices
}
fun fillButtonSlots(config: String, reply: Boolean, originals: Int): List<String> = config.split('|').filter { validButton(it) }.flatMap {
    when (it) { "reply" -> if (reply) listOf(it) else emptyList(); "original" -> (0 until originals).map { index -> "original:$index" }; else -> listOf(it) }
}.take(3)
fun snoozeUntil(now: Long, time: String, zone: ZoneId = ZoneId.systemDefault()): Long {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    var target = local.toLocalDate().atTime(LocalTime.parse(time)).atZone(zone)
    if (!target.toInstant().isAfter(local.toInstant())) target = local.toLocalDate().plusDays(1).atTime(LocalTime.parse(time)).atZone(zone)
    return target.toInstant().toEpochMilli()
}
fun snoozeTomorrow(now: Long, time: String, zone: ZoneId = ZoneId.systemDefault()): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
fun tomorrow(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
