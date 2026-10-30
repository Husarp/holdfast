package com.husarp.holdfast

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.notifications.HoldfastListenerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListenerServiceTest {
    private val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
    private val container = app.container
    private val controller = Robolectric.buildService(HoldfastListenerService::class.java).create()
    private val service = controller.get()

    private fun notification(id: Int, packageName: String = "example.app", flags: Int = 0,
        text: String = "Text", bigText: String? = null, intent: PendingIntent? = null, channel: String = "messages"): StatusBarNotification {
        val builder = Notification.Builder(app, channel).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Title").setContentText(text).setContentIntent(intent)
        if (bigText != null) builder.setStyle(Notification.BigTextStyle().bigText(bigText))
        val notification = builder.build()
        notification.flags = flags
        return StatusBarNotification(packageName, packageName, id, null, 1000, 0, 0, notification, UserHandle.getUserHandleForUid(1000), 100)
    }

    private suspend fun awaitCache(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @After fun close() {
        controller.destroy()
        container.database.close()
    }

    @Test fun savesExpandedTextAndCachesClickIntent() = runBlocking {
        container.repository.setPinned("example.app", true)
        val intent = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val source = notification(1, bigText = "Full expanded message", intent = intent)
        service.onNotificationPosted(source)
        val saved = withTimeout(5_000) { container.repository.notifications.first { it.isNotEmpty() }.single() }
        awaitCache { container.contentIntents[source.key] == intent }
        assertEquals("Full expanded message", saved.text)
        assertEquals("Title", saved.title)
        assertEquals("messages", saved.channelId)
        assertEquals("example.app", saved.appLabel)
        assertEquals(100L, saved.postedAt)
    }

    @Test fun sourceUpdateReplacesContentAndClearsMissingClickIntent() = runBlocking {
        container.repository.setPinned("example.app", true)
        val intent = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val source = notification(1, intent = intent)
        service.onNotificationPosted(source)
        awaitCache { container.contentIntents.containsKey(source.key) }
        service.onNotificationPosted(notification(1, text = "Updated"))
        val saved = withTimeout(5_000) { container.repository.notifications.first { it.singleOrNull()?.text == "Updated" } }
        awaitCache { !container.contentIntents.containsKey(source.key) }
        assertEquals(1, saved.size)
    }

    @Test fun onlyOwnDebugChannelIsCapturedAndCopyCallbacksAreIgnored() = runBlocking {
        val debug = notification(7, packageName = app.packageName, channel = com.husarp.holdfast.notifications.DEBUG_CHANNEL)
        service.onNotificationPosted(notification(8, packageName = app.packageName, channel = "copies"))
        service.onNotificationPosted(debug)
        val rows = withTimeout(5_000) { container.repository.notifications.first { it.isNotEmpty() } }
        assertEquals(listOf(debug.key), rows.map { it.sourceKey })
        service.onNotificationRemoved(debug, null, android.service.notification.NotificationListenerService.REASON_CANCEL)
        withTimeout(5_000) { container.repository.notifications.first { it.single().state == com.husarp.holdfast.data.NotificationState.COPY_VISIBLE } }
        service.onNotificationPosted(notification(9, packageName = app.packageName, channel = "copies"))
        val barrier = notification(10, packageName = app.packageName, channel = com.husarp.holdfast.notifications.DEBUG_CHANNEL)
        service.onNotificationPosted(barrier)
        val final = withTimeout(5_000) { container.repository.notifications.first { it.size >= 2 } }
        assertEquals(2, final.size)
    }
    @Test fun mutedUpdateCompletesCopyAndClearsCachedClickIntent() = runBlocking {
        container.repository.setPinned("example.app", true)
        val intent = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val source = notification(1, intent = intent)
        service.onNotificationPosted(source)
        awaitCache { container.contentIntents.containsKey(source.key) }
        container.copies.originalRemoved(source.key, true)
        container.database.dao().addRule(com.husarp.holdfast.data.MuteRule(packageName = "example.app", kind = "Text", value = "muted"))
        service.onNotificationPosted(notification(1, text = "Now MUTED", intent = intent))
        withTimeout(5_000) { container.repository.notifications.first { it.single().state == com.husarp.holdfast.data.NotificationState.DONE } }
        // A second event is a barrier: the first event must finish updating its click cache.
        service.onNotificationPosted(notification(2))
        withTimeout(5_000) { container.repository.notifications.first { it.size == 2 } }
        assertFalse(container.contentIntents.containsKey(source.key))
        assertEquals("Now MUTED", container.repository.history.first().first { it.notificationId == container.database.dao().notification(source.key)!!.id }.text)
    }
    @Test fun disconnectionIsReported() {
        container.listenerConnected.value = true
        service.onListenerDisconnected()
        assertFalse(container.listenerConnected.value)
    }
    @Test fun reconnectKeepsReplyActionWhenSourceHasNoContentIntent() = runBlocking {
        container.repository.setPinned("example.app", true)
        val source = notification(1)
        source.notification.actions = arrayOf(Notification.Action.Builder(null, "Reply",
            PendingIntent.getBroadcast(app, 1, Intent("reply-source").setPackage(app.packageName), PendingIntent.FLAG_MUTABLE))
            .addRemoteInput(android.app.RemoteInput.Builder("reply").build()).build())
        org.robolectric.Shadows.shadowOf(service).addActiveNotification(source)
        service.onListenerConnected()
        service.onNotificationPosted(notification(2))
        withTimeout(5_000) { container.repository.notifications.first { it.size == 2 } }
        assertFalse(container.contentIntents.containsKey(source.key))
        assertTrue(container.replyActions.containsKey(source.key))
    }

    @Test fun sourceThatBecomesOngoingDoesNotCreateCopyOnUserRemoval() = runBlocking {
        container.repository.setPinned("example.app", true)
        val original = notification(1)
        service.onNotificationPosted(original)
        withTimeout(5_000) { container.repository.notifications.first { it.isNotEmpty() } }
        val ongoing = notification(1, flags = Notification.FLAG_ONGOING_EVENT)
        service.onNotificationPosted(ongoing)
        service.onNotificationRemoved(ongoing, null, android.service.notification.NotificationListenerService.REASON_CANCEL)
        withTimeout(5_000) { container.repository.notifications.first { it.single().state == com.husarp.holdfast.data.NotificationState.DONE } }
        assertTrue(app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun serviceIgnoresOwnUnselectedAndFlaggedNotifications() = runBlocking {
        container.repository.setPinned("example.app", true)
        container.repository.setPinned(app.packageName, true)
        service.onNotificationPosted(notification(1, packageName = app.packageName))
        service.onNotificationPosted(notification(2, packageName = "unselected.app"))
        service.onNotificationPosted(notification(3, flags = Notification.FLAG_ONGOING_EVENT))
        service.onNotificationPosted(notification(4, flags = Notification.FLAG_FOREGROUND_SERVICE))
        service.onNotificationPosted(notification(5, flags = Notification.FLAG_GROUP_SUMMARY))
        val accepted = notification(6)
        service.onNotificationPosted(accepted)
        val saved = withTimeout(5_000) { container.repository.notifications.first { it.isNotEmpty() } }
        assertEquals(listOf(accepted.key), saved.map { it.sourceKey })
    }
}
