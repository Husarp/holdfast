package com.husarp.holdfast

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import com.husarp.holdfast.notifications.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.service.notification.NotificationListenerService as L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TakeOverButtonsTest {
    private val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
    private val c = app.container
    private val dao = c.database.dao()
    @After fun close() { c.database.close() }
    private suspend fun capture(mode: String = CopyMode.SWIPE): CapturedNotification {
        c.repository.setPinned("example", true)
        dao.saveOptions(AppOptions("example", mode = mode))
        return c.repository.capture(NotificationFields("example", "Example", "key", "due", "Cards", "Study", System.currentTimeMillis()), "own", System.currentTimeMillis())!!
    }
    private suspend fun visible(): CapturedNotification {
        val row = capture(); c.copies.originalRemoved(row.sourceKey, true); return dao.notificationById(row.id)!!
    }
    @Test fun immediateTakeOverIsSilentAndIgnoresOriginalRemoval() = runBlocking {
        val row = capture(CopyMode.TAKE_OVER)
        c.copies.takeOver(row.id)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals("take_over", manager.activeNotifications.single().notification.channelId)
        assertNull(manager.getNotificationChannel("take_over").sound)
        c.copies.removed("key", L.REASON_APP_CANCEL)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
    }
    @Test fun keepCopiesAppCancellationButSwipeModeDoesNot() = runBlocking {
        var row = capture(CopyMode.KEEP)
        c.copies.removed("key", L.REASON_APP_CANCEL)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
        c.copies.complete(row.id)
        dao.saveNotification(row.copy(state = NotificationState.ACTIVE_ORIGINAL))
        dao.saveOptions(AppOptions("example", mode = CopyMode.SWIPE))
        c.copies.removed("key", L.REASON_APP_CANCEL)
        assertEquals(NotificationState.DONE, dao.notificationById(row.id)!!.state)
    }
    @Test fun customSnoozeAndUntilPersistDeadlinesAndRejectStaleActions() = runBlocking {
        val row = visible(); val now = System.currentTimeMillis()
        c.copies.action(row.id, row.actionVersion - 1, "done")
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
        c.copies.action(row.id, row.actionVersion, "snooze:15")
        var waiting = dao.notificationById(row.id)!!
        assertEquals(NotificationState.COPY_WAITING, waiting.state)
        assertTrue(waiting.nextShowAt!! in now + 900_000..System.currentTimeMillis() + 900_000)
        dao.saveNotification(waiting.copy(state = NotificationState.COPY_VISIBLE, nextShowAt = null))
        c.copies.action(row.id, waiting.actionVersion, "until:08:00")
        waiting = dao.notificationById(row.id)!!
        assertEquals(snoozeUntil(System.currentTimeMillis(), "08:00"), waiting.nextShowAt)
    }
    @Test fun dismissAndDoneFinishWithoutOpening() = runBlocking {
        val row = visible()
        c.copies.action(row.id, row.actionVersion, "done")
        assertEquals(NotificationState.DONE, dao.notificationById(row.id)!!.state)
        dao.saveNotification(row)
        c.copies.action(row.id, row.actionVersion, "dismiss")
        assertEquals(NotificationState.DONE, dao.notificationById(row.id)!!.state)
        assertEquals(0, dao.opens("example", 0))
    }
    @Test fun muteTypeAddsOffOverrideAndFinishesMatchingCopies() = runBlocking {
        val row = visible()
        c.copies.action(row.id, row.actionVersion, "muteType")
        assertEquals(CopyMode.OFF, c.repository.policy(row).mode)
        assertEquals("due", dao.types().single().value)
        assertEquals(NotificationState.DONE, dao.notificationById(row.id)!!.state)
    }
    @Test fun muteTodayDefersUntilMidnightAndCanRecover() = runBlocking {
        val row = visible()
        c.copies.action(row.id, row.actionVersion, "muteToday")
        val waiting = dao.notificationById(row.id)!!
        assertEquals(NotificationState.COPY_WAITING, waiting.state)
        assertEquals(tomorrow(System.currentTimeMillis()), waiting.nextShowAt)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        dao.saveOptions(c.repository.options(row).copy(mutedUntil = 0))
        c.copies.recover()
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
    }
    @Test fun typeButtonsKeepOtherwiseMatchingConversationsSeparate() = runBlocking {
        val first = visible()
        dao.saveType(TypePolicy(packageName = "example", kind = "Text", value = "Different", buttons = "done"))
        val second = c.repository.capture(NotificationFields("example", "Example", "second", "due", "Cards", "Different", System.currentTimeMillis()), "own", System.currentTimeMillis())!!
        c.copies.originalRemoved(second.sourceKey, true)
        assertNotEquals(dao.notificationById(first.id)!!.copyId, dao.notificationById(second.id)!!.copyId)
        assertEquals(2, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
    }
    @Test fun openAndKeepRetainsLiveClickAndNotification() = runBlocking {
        val row = visible()
        val token = android.app.PendingIntent.getBroadcast(app, 9, Intent("source-click").setPackage(app.packageName), android.app.PendingIntent.FLAG_IMMUTABLE)
        c.contentIntents[row.sourceKey] = token
        assertTrue(c.copies.open(row, keep = true))
        assertEquals(token, c.contentIntents[row.sourceKey])
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
    }
    @Test fun copyTextUsesForegroundClipboardAndKeepsCopyWithoutRecordingAnOpen() = runBlocking {
        val row = visible()
        val controller = org.robolectric.Robolectric.buildActivity(CopyOpenActivity::class.java,
            Intent(app, CopyOpenActivity::class.java).setAction("copyText").putExtra("id", row.id).putExtra("version", row.actionVersion)).create()
        val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
        kotlinx.coroutines.withTimeout(5_000) {
            while (!clipboard.hasPrimaryClip()) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); kotlinx.coroutines.delay(10) }
        }
        assertEquals("Cards\nStudy", clipboard.primaryClip!!.getItemAt(0).text)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
        assertEquals(0, dao.opens("example", 0))
        controller.destroy()
        Unit
    }
    @Test fun mirrorsLiveOriginalActionsAndFallsThroughWhenLost() = runBlocking {
        val row = visible()
        val token = android.app.PendingIntent.getBroadcast(app, 10, Intent("mark-read").setPackage(app.packageName), android.app.PendingIntent.FLAG_IMMUTABLE)
        c.appearance.originals[row.sourceKey] = android.app.Notification.Builder(app, "source").setSmallIcon(R.drawable.ic_holdfast)
            .addAction(android.app.Notification.Action.Builder(null, "Mark as read", token).build()).build()
        dao.saveOptions(c.repository.options(row).copy(buttons = "original|done|dismiss|snooze:15"))
        c.copies.recover()
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals(token, manager.activeNotifications.single().notification.actions[0].actionIntent)
        c.appearance.originals.clear(); c.copies.recover()
        assertEquals(listOf("Done", "Dismiss", "Snooze 15m"), manager.activeNotifications.single().notification.actions.map { it.title.toString() })
    }
    @Test fun globalDefaultAffectsOnlyNewlyPinnedApps() = runBlocking {
        c.repository.setPinned("old", true)
        c.repository.saveSettings(Settings(holdEverything = true))
        c.repository.setPinned("new", true)
        assertEquals(CopyMode.SWIPE, dao.options("old", 1)!!.mode)
        assertEquals(CopyMode.TAKE_OVER, dao.options("new", 1)!!.mode)
    }
    @Test fun buttonsAreLimitedAndUnavailableTokensDisappearAfterProcessLoss() = runBlocking {
        val row = visible()
        dao.saveOptions(c.repository.options(row).copy(buttons = "reply|original|done|snooze:180|dismiss|copyText"))
        c.copies.recover()
        val actions = app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification.actions
        assertEquals(listOf("Done", "Snooze 180m", "Dismiss"), actions.map { it.title.toString() })
    }
}
