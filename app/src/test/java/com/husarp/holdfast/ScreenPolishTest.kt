package com.husarp.holdfast

import com.husarp.holdfast.data.QuietSchedule
import com.husarp.holdfast.ui.appBarFraction
import com.husarp.holdfast.ui.quietHoursSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenPolishTest {
    @Test fun barsCompareWithLargestApp() {
        assertEquals(1f, appBarFraction(16, 16))
        assertEquals(0.25f, appBarFraction(4, 16))
        assertEquals(0f, appBarFraction(0, 0))
    }

    @Test fun quietSummaryShowsDaysAndOvernightTimes() {
        assertEquals("Off", quietHoursSummary(emptyList()))
        val row = QuietSchedule(packageName = "", days = "1,2,3,4,5", startMinute = 1320, endMinute = 420)
        assertEquals("Mon–Fri 22:00–07:00", quietHoursSummary(listOf(row)))
        assertEquals("Mon, Wed 22:00–07:00", quietHoursSummary(listOf(row.copy(days = "1,3"))))
        assertEquals("2 quiet schedules", quietHoursSummary(listOf(row, row)))
    }
}
