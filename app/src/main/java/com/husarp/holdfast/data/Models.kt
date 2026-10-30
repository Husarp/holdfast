package com.husarp.holdfast.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NotificationState { ACTIVE_ORIGINAL, COPY_VISIBLE, COPY_WAITING, DONE, EXPIRED }

@Entity(primaryKeys = ["packageName", "profileId", "userSerial"])
data class PinnedApp(val packageName: String, val enabled: Boolean = true, val profileId: Long = 1, val userSerial: Long = 0)

@Entity(indices = [Index(value = ["sourceKey"], unique = true)])
data class CapturedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val sourceKey: String,
    val channelId: String?,
    val title: String,
    val text: String,
    val postedAt: Long,
    val updatedAt: Long,
    val state: NotificationState = NotificationState.ACTIVE_ORIGINAL,
    val nextShowAt: Long? = null,
    val expireAt: Long? = null,
    val firstCopyAt: Long? = null,
    val copyId: Int? = null,
    val actionVersion: Long = 0,
    val isGroupSummary: Boolean = false,
    val sender: String? = null,
    val shortcutId: String? = null,
    val digest: Boolean = false,
    val policyDeferred: Boolean = false,
    val resumeAt: Long? = null,
    val isVip: Boolean = false,
    val nextRepeatAt: Long? = null,
    val userSerial: Long = 0,
)

@Entity
data class Settings(
    @PrimaryKey val id: Int = 1,
    val activeProfileId: Long = 1,
    val returnDelayMinutes: Int = 0,
    val expireAfterMinutes: Int? = null,
) { init { require(id == 1); require(returnDelayMinutes >= 0); require(expireAfterMinutes == null || expireAfterMinutes > 0) } }

@Entity
data class NotificationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationId: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val capturedAt: Long,
    val userSerial: Long = 0,
)

data class AppCount(val packageName: String, val appLabel: String, val count: Int, val userSerial: Long = 0)

@Entity
data class MuteRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val profileId: Long = 1,
    val kind: String,
    val value: String,
) {
    init { require(kind in listOf("Channel", "Title", "Text")); require(value.isNotBlank()) }
    fun matches(row: CapturedNotification): Boolean = packageName == row.packageName && when (kind) {
        "Channel" -> row.channelId == value
        "Title" -> row.title.contains(value, ignoreCase = true)
        else -> row.text.contains(value, ignoreCase = true)
    }
}

@Entity(primaryKeys = ["packageName", "channelId"])
data class SeenChannel(val packageName: String, val channelId: String)

@Entity
data class Profile(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity
data class QuietSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val packageName: String,
    val days: String,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(days.split(",").all { it.toIntOrNull() in 1..7 })
        require(startMinute in 0..1439 && endMinute in 0..1439)
    }
    fun matches(time: java.time.ZonedDateTime): Boolean {
        val selected = days.split(",").map { it.toInt() }
        val minute = time.hour * 60 + time.minute
        return if (startMinute < endMinute) time.dayOfWeek.value in selected && minute in startMinute until endMinute
        else (time.dayOfWeek.value in selected && minute >= startMinute) ||
            (time.minusDays(1).dayOfWeek.value in selected && minute < endMinute)
    }
}

fun quietEnd(schedules: List<QuietSchedule>, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long? {
    var time = java.time.Instant.ofEpochMilli(now).atZone(zone)
    if (schedules.none { it.matches(time) }) return null
    // A full week of quiet time has no finite end; check again next week.
    repeat(7 * 24 * 60) {
        time = time.plusMinutes(1).withSecond(0).withNano(0)
        if (schedules.none { it.matches(time) }) return time.toInstant().toEpochMilli()
    }
    return time.toInstant().toEpochMilli()
}

@Entity(primaryKeys = ["packageName", "profileId"])
data class AppOptions(
    val packageName: String,
    val profileId: Long = 1,
    val vipSenders: String = "",
    val repeatMinutes: Int? = null,
    val digestOnly: Boolean = false,
    val digestTimes: String = "09:00,18:00",
) {
    init { require(repeatMinutes == null || repeatMinutes > 0); require(parseDigestTimes(digestTimes).isNotEmpty()) }
    fun isVip(row: CapturedNotification) = vipSenders.lines().filter { it.isNotBlank() }.any {
        it.trim().equals(row.sender ?: row.title, ignoreCase = true)
    }
}
fun repeatAt(now: Long, minutes: Int?): Long? = minutes?.let { now + it * 60_000L }

fun groupingKey(row: CapturedNotification): List<String> = if (row.digest) listOf(row.packageName, row.userSerial.toString(), "digest")
    else listOf(row.packageName, row.userSerial.toString(), "conversation", if (!row.shortcutId.isNullOrBlank()) "shortcut" else "title", row.shortcutId?.takeIf { it.isNotBlank() } ?: row.title)

fun parseDigestTimes(value: String): List<java.time.LocalTime> = try {
    value.split(",").map { java.time.LocalTime.parse(it.trim()) }.distinct().sorted()
} catch (_: java.time.format.DateTimeParseException) { emptyList() }

fun nextDigestAt(now: Long, times: List<java.time.LocalTime>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
    require(times.isNotEmpty())
    val local = java.time.Instant.ofEpochMilli(now).atZone(zone)
    return (0L..2L).flatMap { offset -> times.map { local.toLocalDate().plusDays(offset).atTime(it).atZone(zone).toInstant().toEpochMilli() } }
        .filter { it > now }.min()
}

fun nextQuietStart(schedules: List<QuietSchedule>, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long? {
    val date = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return schedules.flatMap { schedule ->
        (0L..7L).mapNotNull { offset ->
            val day = date.plusDays(offset)
            if (day.dayOfWeek.value !in schedule.days.split(",").map { it.toInt() }) null
            else day.atStartOfDay().plusMinutes(schedule.startMinute.toLong()).atZone(zone).toInstant().toEpochMilli().takeIf { it > now }
        }
    }.minOrNull()
}

@Entity
data class CopyOpen(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val openedAt: Long)

@Entity
data class SpamDecision(@PrimaryKey val packageName: String, val appLabel: String, val count: Int, val answered: Boolean = false)

fun shouldSuggestSpam(count: Int, opened: Boolean, alreadySuggested: Boolean) = count >= 20 && !opened && !alreadySuggested
