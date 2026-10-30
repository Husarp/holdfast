package com.husarp.holdfast

import com.husarp.holdfast.data.*
import com.husarp.holdfast.notifications.*
import org.junit.Assert.*
import org.junit.Test

class NotificationCaptureTest {
    private val fields = NotificationFields("example.app", "Example", "key", "messages", "Title", "Full text", 100)
    private fun map(input: NotificationFields = fields, pinned: Boolean = true, existing: CapturedNotification? = null) =
        mapNotification(input, "com.husarp.holdfast", pinned, 200, existing)

    @Test fun mapsAllSourceFieldsAndInitialState() {
        assertEquals(CapturedNotification(packageName = "example.app", appLabel = "Example", sourceKey = "key",
            channelId = "messages", title = "Title", text = "Full text", postedAt = 100, updatedAt = 200), map())
    }
    @Test fun ignoresUnselectedApp() { assertNull(map(pinned = false)) }
    @Test fun ignoresOwnPackageEvenIfSelected() { assertNull(map(fields.copy(packageName = "com.husarp.holdfast"))) }
    @Test fun ignoresOngoingNotification() { assertNull(map(fields.copy(ongoing = true))) }
    @Test fun ignoresForegroundService() { assertNull(map(fields.copy(foregroundService = true))) }
    @Test fun ignoresGroupSummary() { assertNull(map(fields.copy(groupSummary = true))) }
    @Test fun acceptsGroupChildren() { assertNotNull(map(fields.copy(groupSummary = false))) }
    @Test fun missingContentIsStoredAsEmptyText() {
        val result = map(fields.copy(title = null, text = null, channelId = null))!!
        assertEquals("", result.title)
        assertEquals("", result.text)
        assertNull(result.channelId)
    }
    @Test fun updatesContentWithoutResettingIdentityTimesOrLifecycle() {
        val existing = map()!!.copy(id = 7, postedAt = 50, updatedAt = 100, state = NotificationState.COPY_WAITING,
            copyId = 9, nextShowAt = 300, expireAt = 500)
        val result = map(fields.copy(appLabel = "New name", title = "New title", text = "New text", channelId = "new", postedAt = 150), existing = existing)!!
        assertEquals(existing.copy(appLabel = "New name", title = "New title", text = "New text", channelId = "new", updatedAt = 200), result)
    }
    @Test fun debugSourceIsAcceptedWithoutSelectingHoldfast() {
        assertNotNull(map(fields.copy(packageName = "com.husarp.holdfast", channelId = DEBUG_CHANNEL, debugSource = true), pinned = false))
    }
    @Test fun debugFlagCannotAllowOwnCopiesOrOtherChannels() {
        assertNull(map(fields.copy(packageName = "com.husarp.holdfast", channelId = "copies", debugSource = true)))
        assertNull(map(fields.copy(debugSource = true), pinned = false))
    }
    @Test fun laterSourceOccurrenceCanReuseCompletedNotificationKey() {
        val old = map()!!.copy(id = 7, state = NotificationState.DONE, updatedAt = 200, copyId = 7, actionVersion = 2)
        val next = map(fields.copy(postedAt = 300), existing = old)!!
        assertEquals(7L, next.id)
        assertEquals(NotificationState.ACTIVE_ORIGINAL, next.state)
        assertEquals(300L, next.postedAt)
        assertNull(next.copyId)
        assertEquals(3L, next.actionVersion)
    }
    @Test fun duplicateCallbacksNeverResurrectTerminalStates() {
        listOf(NotificationState.DONE, NotificationState.EXPIRED).forEach { state ->
            assertEquals(state, map(existing = map()!!.copy(state = state))!!.state)
        }
    }
}
