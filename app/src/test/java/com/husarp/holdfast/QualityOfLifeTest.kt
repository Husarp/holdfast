package com.husarp.holdfast

import com.husarp.holdfast.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class QualityOfLifeTest {
    @Test fun pauseChoicesUseLocalTomorrowAcrossDaylightSaving() {
        val zone = ZoneId.of("Europe/Warsaw")
        val now = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(now + 3_600_000, pauseDeadline("1 hour", now, zone))
        assertEquals(ZonedDateTime.of(2026, 3, 29, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), pauseDeadline("Tomorrow at 08:00", now, zone))
        assertEquals(Long.MAX_VALUE, pauseDeadline("Until resumed", now, zone))
    }
    @Test fun appTimingOverridesAndGlobalFallback() {
        val global = Settings(returnDelayMinutes = 10, expireAfterMinutes = 60)
        val app = AppOptions("app")
        assertEquals(10, effectiveDelay(global, app))
        assertEquals(60, effectiveExpiry(global, app))
        assertEquals(0, effectiveDelay(global, app.copy(returnDelayMinutes = 0)))
        assertNull(effectiveExpiry(global, app.copy(overrideExpiry = true)))
        assertEquals(5, effectiveExpiry(global, app.copy(overrideExpiry = true, expireAfterMinutes = 5)))
    }
    @Test fun pinnedThenRecentThenOtherApps() {
        assertEquals(listOf("Pinned", "Recent", "Other"), listOf("Other" to appSortRank(false, false), "Recent" to appSortRank(false, true), "Pinned" to appSortRank(true, true)).sortedBy { it.second }.map { it.first })
    }
    @Test fun copyAgeIsReadableAndNeverNegative() {
        assertEquals("Just now", relativeAge(200, 100))
        assertEquals("2 h ago", relativeAge(0, 7_200_000))
        assertEquals("2 d ago", relativeAge(0, 172_800_000))
    }
}
