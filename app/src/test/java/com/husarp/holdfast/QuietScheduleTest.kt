package com.husarp.holdfast

import com.husarp.holdfast.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class QuietScheduleTest {
    private fun time(value: String) = ZonedDateTime.parse(value + "Z[UTC]")
    @Test fun weekdayRangeHasExclusiveEnd() {
        val rule = QuietSchedule(packageName = "app", days = "1,2,3,4,5", startMinute = 540, endMinute = 1020)
        assertTrue(rule.matches(time("2026-10-30T09:00:00")))
        assertFalse(rule.matches(time("2026-10-30T17:00:00")))
        assertFalse(rule.matches(time("2026-10-31T09:00:00")))
    }
    @Test fun overnightUsesStartDayIncludingWeekendSpill() {
        val rule = QuietSchedule(packageName = "app", days = "5", startMinute = 1080, endMinute = 480)
        assertTrue(rule.matches(time("2026-10-31T07:59:00")))
        assertFalse(rule.matches(time("2026-10-31T08:00:00")))
        assertFalse(rule.matches(time("2026-10-30T07:00:00")))
        assertEquals(time("2026-10-31T08:00:00").toInstant().toEpochMilli(), quietEnd(listOf(rule), time("2026-10-30T19:00:00").toInstant().toEpochMilli(), ZoneId.of("UTC")))
    }
    @Test fun nextQuietStartSchedulesHidingVisibleCopiesAndSkipsWeekend() {
        val rule = QuietSchedule(packageName = "app", days = "1,2,3,4,5", startMinute = 1080, endMinute = 480)
        assertEquals(time("2026-10-30T18:00:00").toInstant().toEpochMilli(), nextQuietStart(listOf(rule), time("2026-10-30T12:00:00").toInstant().toEpochMilli(), ZoneId.of("UTC")))
        assertEquals(time("2026-11-02T18:00:00").toInstant().toEpochMilli(), nextQuietStart(listOf(rule), time("2026-10-31T12:00:00").toInstant().toEpochMilli(), ZoneId.of("UTC")))
    }
    @Test fun overlappingSchedulesWaitUntilBothEnd() {
        val first = QuietSchedule(packageName = "app", days = "6,7", startMinute = 0, endMinute = 720)
        val second = first.copy(startMinute = 600, endMinute = 840)
        assertEquals(time("2026-10-31T14:00:00").toInstant().toEpochMilli(), quietEnd(listOf(first, second), time("2026-10-31T11:00:00").toInstant().toEpochMilli(), ZoneId.of("UTC")))
    }
}
