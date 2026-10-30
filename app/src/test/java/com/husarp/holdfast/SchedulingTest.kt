package com.husarp.holdfast

import com.husarp.holdfast.data.*
import com.husarp.holdfast.scheduling.*
import org.junit.Assert.*
import org.junit.Test

class SchedulingTest {
    private fun row(state: NotificationState = NotificationState.COPY_WAITING, due: Long? = 200, expiry: Long? = 500) =
        CapturedNotification(packageName = "app", appLabel = "App", sourceKey = "key", channelId = null,
            title = "Title", text = "Text", postedAt = 0, updatedAt = 0, state = state, nextShowAt = due, expireAt = expiry)
    @Test fun expiryOffHasNoDeadline() { assertNull(expiryAt(100, null)) }
    @Test fun expiryUsesMinutesFromFirstCopy() { assertEquals(120100L, expiryAt(100, 2)) }
    @Test fun expiryWinsAtExactBoundary() { assertTrue(isExpired(row(), 500)); assertFalse(isExpired(row(), 499)) }
    @Test fun choosesEarliestDueAcrossRows() { assertEquals(100L, nextDeadline(listOf(row(), row(due = 100)))) }
    @Test fun expiryCanWinBeforeReturn() { assertEquals(50L, nextDeadline(listOf(row(expiry = 50)))) }
    @Test fun visibleCopiesScheduleOnlyExpiry() { assertEquals(500L, nextDeadline(listOf(row(NotificationState.COPY_VISIBLE)))) }
    @Test fun terminalAndOriginalRowsNeverSchedule() {
        assertNull(nextDeadline(listOf(row(NotificationState.DONE), row(NotificationState.EXPIRED), row(NotificationState.ACTIVE_ORIGINAL))))
    }
    @Test fun emptyRowsCancelAlarm() { assertNull(nextDeadline(emptyList())) }
}
