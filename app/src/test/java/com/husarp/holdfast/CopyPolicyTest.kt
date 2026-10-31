package com.husarp.holdfast

import com.husarp.holdfast.data.*
import com.husarp.holdfast.ui.helpText
import android.service.notification.NotificationListenerService as L
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CopyPolicyTest {
    private val row = CapturedNotification(packageName = "app", appLabel = "App", sourceKey = "key", channelId = "due", title = "Cards due", text = "Study now", postedAt = 1, updatedAt = 1)
    @Test fun modeResolutionUsesTextThenChannelThenAppThenGlobal() {
        val global = Settings(holdEverything = true)
        val app = AppOptions("app")
        assertEquals(CopyMode.TAKE_OVER, resolvePolicy(row, emptyList(), app, global).mode)
        assertEquals(CopyMode.SWIPE, resolvePolicy(row, emptyList(), app.copy(mode = CopyMode.SWIPE), global).mode)
        val channel = TypePolicy(1, "app", value = "due", mode = CopyMode.KEEP)
        val text = TypePolicy(2, "app", kind = "Title", value = "cards", mode = CopyMode.OFF)
        assertEquals(CopyMode.KEEP, resolvePolicy(row, listOf(channel), app, global).mode)
        assertEquals(CopyMode.OFF, resolvePolicy(row, listOf(channel, text), app, global).mode)
        assertEquals(CopyMode.TAKE_OVER, resolvePolicy(row, listOf(channel.copy(profileId = 2)), app, global).mode)
    }
    @Test fun tapAndButtonsCanBeOverriddenIndependently() {
        val app = AppOptions("app", mode = CopyMode.TAKE_OVER)
        assertFalse(resolvePolicy(row, emptyList(), app, Settings()).openAndFinish)
        val type = TypePolicy(packageName = "app", value = "due", openAndFinish = true, buttons = "done")
        val policy = resolvePolicy(row, listOf(type), app, Settings())
        assertEquals(CopyMode.TAKE_OVER, policy.mode); assertTrue(policy.openAndFinish); assertEquals("done", policy.buttons)
    }
    @Test fun everyRemovalReasonIsExplicit() {
        val swipes = setOf(L.REASON_CANCEL, L.REASON_CANCEL_ALL)
        val keep = swipes + setOf(L.REASON_CLICK, L.REASON_APP_CANCEL, L.REASON_APP_CANCEL_ALL)
        for (reason in -1..25) for (mode in CopyMode.choices) {
            assertEquals("$mode reason $reason", when(mode) { CopyMode.SWIPE -> reason in swipes; CopyMode.KEEP -> reason in keep; else -> false }, copiesOnRemoval(mode, reason))
        }
    }
    @Test fun slotFillingSkipsUnavailableAndExpandsOriginals() {
        assertEquals(listOf("done", "snooze:15", "dismiss"), fillButtonSlots("reply|original|done|snooze:15|dismiss", false, 0))
        assertEquals(listOf("reply", "original:0", "original:1"), fillButtonSlots("reply|original|done", true, 3))
        assertEquals(listOf("snooze:60", "dismiss", "reply"), fillButtonSlots(AppOptions("app").buttons, true, 0))
        assertEquals(listOf("snooze:60", "dismiss"), fillButtonSlots(AppOptions("app").buttons, false, 0))
        assertTrue(fillButtonSlots("snooze:-1|until:99:99|invalid", true, 2).isEmpty())
    }
    @Test fun snoozeUntilUsesNextLocalOccurrenceAcrossMidnightAndDst() {
        val zone = ZoneId.of("Europe/Warsaw")
        val now = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(ZonedDateTime.of(2026, 3, 29, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), snoozeUntil(now, "08:00", zone))
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, zone).toInstant().toEpochMilli(), snoozeUntil(now, "02:30", zone))
        assertEquals(snoozeUntil(now, "08:00", zone), snoozeTomorrow(now, "08:00", zone))
        val morning = snoozeUntil(now, "08:00", zone)
        assertEquals(ZonedDateTime.of(2026, 3, 30, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), snoozeUntil(morning, "08:00", zone))
        assertEquals(ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), tomorrow(now, zone))
    }
    @Test fun everyScreenHasShortHelp() {
        listOf("Pinned", "Apps", "When", "History", "Settings", "App page", "Profiles", "Places", "Setup", "Timing", "Backup", "Intro", "Pause", "Updates", "What’s new", "About").forEach {
            assertTrue(it, helpText(it).lines().size in 2..5)
        }
    }
}
