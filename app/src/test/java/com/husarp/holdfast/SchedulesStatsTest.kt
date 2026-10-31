package com.husarp.holdfast

import com.husarp.holdfast.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SchedulesStatsTest {
    private val zone = ZoneId.of("UTC")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun multipleRangesLeaveAGapAndOvernightUsesStartingDay() {
        val ranges = listOf(QuietSchedule(packageName = "app", days = "1", startMinute = 900, endMinute = 960),
            QuietSchedule(packageName = "app", days = "1", startMinute = 1200, endMinute = 60))
        fun quiet(value: String) = quietEnd(ranges, at(value), zone)
        assertEquals(at("2026-11-02T16:00:00Z"), quiet("2026-11-02T15:30:00Z"))
        assertNull(quiet("2026-11-02T17:00:00Z"))
        assertEquals(at("2026-11-03T01:00:00Z"), quiet("2026-11-03T00:30:00Z"))
        assertNull(quiet("2026-11-03T20:30:00Z"))
    }

    @Test fun weeklyDigestSkipsEmptyDaysAndAcceptsSeveralTimes() {
        val schedule = "1@09:00,1@15:00,5@18:00"
        assertEquals(at("2026-11-02T15:00:00Z"), nextWeeklyDigestAt(at("2026-11-02T09:00:00Z"), schedule, zone))
        assertEquals(at("2026-11-06T18:00:00Z"), nextWeeklyDigestAt(at("2026-11-02T15:00:00Z"), schedule, zone))
        assertEquals(at("2026-11-09T09:00:00Z"), nextWeeklyDigestAt(at("2026-11-06T18:00:00Z"), schedule, zone))
        assertEquals(14, digestEntries("09:00,18:00").size)
        assertTrue(digestEntries("8@09:00").isEmpty())
    }

    @Test fun statsIncludeWholeCalendarDaysAndExcludeFutureAndOldEvents() {
        val now = at("2026-11-02T12:00:00Z")
        fun event(id: Long, time: String, held: Boolean = false) = NotificationEvent(id, id, "app", "App", "", "", at(time), heldAsCopy = held)
        val stats = activityStats(listOf(event(1, "2026-10-27T00:00:00Z", true), event(2, "2026-11-02T11:00:00Z"),
            event(3, "2026-10-26T23:59:59Z"), event(4, "2026-11-02T13:00:00Z")),
            listOf(CopyOpen(packageName = "app", openedAt = now), CopyOpen(packageName = "app", openedAt = now + 1)), 7, now, zone)
        assertEquals(2, stats.events.size)
        assertEquals(1, stats.held)
        assertEquals(1, stats.opened)
        assertEquals(7, stats.daily.size)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 1), stats.daily.map { it.second })
        assertEquals(30, activityStats(emptyList(), emptyList(), 30, now, zone).daily.size)
    }
}
