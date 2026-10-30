package com.husarp.holdfast
import com.husarp.holdfast.data.*
import org.junit.Assert.*
import org.junit.Test
class BackupTest {
    @Test fun roundTrip() {
        val b = Backup(settings = Settings(), profiles = listOf(Profile(1, "Work")), pinned = listOf(PinnedApp("app")),
            rules = listOf(MuteRule(packageName = "app", kind = "Text", value = "private")), quiet = emptyList(), options = listOf(AppOptions("app")),
            history = listOf(NotificationEvent(notificationId = 1, packageName = "app", appLabel = "App", title = "Secret", text = "text", capturedAt = 1)))
        assertEquals(b, BackupFormat.decode(BackupFormat.encode(b)))
    }
    @Test fun rejectsBadFiles() {
        listOf("", "{}", "[]", """{"version":99}""", """{"version":1,"settings":null}""").forEach {
            try { BackupFormat.decode(it); fail("Accepted $it") } catch (_: IllegalArgumentException) {}
        }
    }
}
