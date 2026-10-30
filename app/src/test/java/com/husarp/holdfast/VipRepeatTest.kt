package com.husarp.holdfast

import com.husarp.holdfast.data.*
import com.husarp.holdfast.scheduling.*
import org.junit.Assert.*
import org.junit.Test

class VipRepeatTest {
    private val row = CapturedNotification(packageName = "app", appLabel = "App", sourceKey = "key", channelId = null, title = "Alice", text = "Hi", postedAt = 0, updatedAt = 0)
    @Test fun vipMatchesWholeSenderIgnoringCaseAndWhitespace() {
        val options = AppOptions("app", vipSenders = " Bob\n alice \n")
        assertTrue(options.isVip(row))
        assertFalse(options.isVip(row.copy(title = "Alice Smith")))
        assertFalse(AppOptions("app").isVip(row.copy(title = "")))
    }
    @Test fun messagingSenderTakesPrecedenceOverTitle() {
        val options = AppOptions("app", vipSenders = "Bob")
        assertTrue(options.isVip(row.copy(sender = "Bob")))
        assertFalse(options.isVip(row.copy(title = "Bob", sender = "Carol")))
    }
    @Test fun repeatDeadlineAndCompletion() {
        assertEquals(301000L, repeatAt(1000, 5))
        assertNull(repeatAt(1000, null))
        val visible = row.copy(state = NotificationState.COPY_VISIBLE, nextRepeatAt = 301000)
        assertEquals(301000L, nextDeadline(listOf(visible)))
        assertNull(nextDeadline(listOf(visible.copy(state = NotificationState.DONE))))
    }
    @Test fun vipIgnoresExpiry() {
        assertFalse(isExpired(row.copy(isVip = true, expireAt = 1), 1000))
        assertNull(nextDeadline(listOf(row.copy(state = NotificationState.COPY_VISIBLE, isVip = true, expireAt = 1))))
    }
}
