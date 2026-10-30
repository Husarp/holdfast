package com.husarp.holdfast

import com.husarp.holdfast.data.*
import com.husarp.holdfast.notifications.*
import org.junit.Assert.*
import org.junit.Test

class WorkProfileTest {
    @Test fun captureAndGroupingKeepAndroidUser() {
        val personal = mapNotification(NotificationFields("app", "App", "personal", null, "Chat", "Hello", 1), "holdfast", true, 1)!!
        val work = mapNotification(NotificationFields("app", "App · Work", "work", null, "Chat", "Hello", 1, userSerial = 10), "holdfast", true, 1)!!
        assertEquals(10L, work.userSerial)
        assertNotEquals(groupingKey(personal), groupingKey(work))
    }

    @Test fun backupRoundTripKeepsBothAndroidUsers() {
        val backup = Backup(settings = Settings(), profiles = listOf(Profile(1, "Work")),
            pinned = listOf(PinnedApp("app"), PinnedApp("app", userSerial = 10)),
            rules = emptyList(), quiet = emptyList(), options = emptyList(),
            history = listOf(NotificationEvent(notificationId = 1, packageName = "app",
                appLabel = "App · Work", title = "Hello", text = "World", capturedAt = 1, userSerial = 10)))
        assertEquals(backup, BackupFormat.decode(BackupFormat.encode(backup)))
    }
}
