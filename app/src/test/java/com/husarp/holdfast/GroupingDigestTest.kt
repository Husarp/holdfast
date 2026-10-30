package com.husarp.holdfast

import com.husarp.holdfast.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class GroupingDigestTest {
    private val row = CapturedNotification(packageName = "app", appLabel = "App", sourceKey = "key", channelId = null, title = "Alice", text = "Hi", postedAt = 0, updatedAt = 0)
    @Test fun conversationUsesShortcutBeforeTitleAndSeparatesApps() {
        assertEquals(groupingKey(row), groupingKey(row.copy(sourceKey = "other", text = "New")))
        assertNotEquals(groupingKey(row), groupingKey(row.copy(packageName = "other")))
        assertEquals(groupingKey(row.copy(shortcutId = "chat")), groupingKey(row.copy(shortcutId = "chat", title = "Changed")))
        assertNotEquals(groupingKey(row), groupingKey(row.copy(shortcutId = "Alice")))
    }
    @Test fun digestCombinesConversationsButNotApps() {
        assertEquals(groupingKey(row.copy(digest = true)), groupingKey(row.copy(digest = true, title = "Bob")))
        assertNotEquals(groupingKey(row.copy(digest = true)), groupingKey(row))
    }
    @Test fun digestUsesNextTimeAndRollsOverAtExactBoundary() {
        val zone = ZoneId.of("UTC")
        val times = parseDigestTimes("18:00,09:00,09:00")
        fun at(value: String) = Instant.parse(value).toEpochMilli()
        assertEquals(at("2026-10-30T09:00:00Z"), nextDigestAt(at("2026-10-30T08:59:00Z"), times, zone))
        assertEquals(at("2026-10-30T18:00:00Z"), nextDigestAt(at("2026-10-30T09:00:00Z"), times, zone))
        assertEquals(at("2026-10-31T09:00:00Z"), nextDigestAt(at("2026-10-30T19:00:00Z"), times, zone))
        assertTrue(parseDigestTimes("wrong").isEmpty())
    }
    @Test fun digestResolvesSpringClockGap() {
        val now = Instant.parse("2026-03-29T00:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), nextDigestAt(now, parseDigestTimes("02:30"), ZoneId.of("Europe/Warsaw")))
    }
}
