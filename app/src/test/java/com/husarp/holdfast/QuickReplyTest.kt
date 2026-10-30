package com.husarp.holdfast

import android.app.*
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.notifications.NotificationFields
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickReplyTest {
    @Test fun replyReplacesSecondSnoozeAndDisappearsWithoutCache() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val c = app.container
        c.repository.setPinned("example", true)
        c.repository.capture(NotificationFields("example", "Example", "key", null, "Title", "Text", 1), "own", 2)
        c.replyActions["key"] = Notification.Action.Builder(null, "Reply",
            PendingIntent.getBroadcast(app, 1, Intent("test").setPackage(app.packageName), PendingIntent.FLAG_MUTABLE))
            .addRemoteInput(RemoteInput.Builder("text").setLabel("Reply").build()).build()
        c.copies.originalRemoved("key", true)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals(listOf("Snooze 1h", "Reply", "Dismiss"), manager.activeNotifications.single().notification.actions.map { it.title.toString() })
        val actions = manager.activeNotifications.single().notification.actions
        assertNotEquals(org.robolectric.Shadows.shadowOf(actions[1].actionIntent).requestCode,
            org.robolectric.Shadows.shadowOf(actions[2].actionIntent).requestCode)
        assertFalse(actions[1].actionIntent.isImmutable)
        assertTrue(actions[2].actionIntent.isImmutable)
        c.replyActions.clear()
        c.copies.recover()
        assertEquals("Snooze 3h", manager.activeNotifications.single().notification.actions[1].title)
        c.database.close()
    }

    @Test fun replyNeedsCurrentVersionAndResultsAndCompletesOnlyAfterSend() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val c = app.container
        c.repository.setPinned("example", true)
        val row = c.repository.capture(NotificationFields("example", "Example", "key", null, "Title", "Text", 1), "own", 2)!!
        val remote = RemoteInput.Builder("text").setLabel("Reply").build()
        val token = PendingIntent.getBroadcast(app, 1, Intent("test").setPackage(app.packageName), PendingIntent.FLAG_MUTABLE)
        c.replyActions["key"] = Notification.Action.Builder(null, "Reply", token).addRemoteInput(remote).build()
        c.copies.originalRemoved("key", true)
        val version = c.database.dao().notificationById(row.id)!!.actionVersion
        val intent = Intent()
        assertFalse(c.copies.reply(row.id, version, intent))
        RemoteInput.addResultsToIntent(arrayOf(remote), intent, android.os.Bundle().apply { putCharSequence("text", "Hello") })
        assertFalse(c.copies.reply(row.id, version - 1, intent))
        assertTrue(c.copies.reply(row.id, version, intent))
        assertEquals(com.husarp.holdfast.data.NotificationState.DONE, c.database.dao().notificationById(row.id)!!.state)
        assertFalse(c.copies.reply(row.id, version, intent))
        assertFalse(c.replyActions.containsKey("key"))
        c.database.close()
    }

    @Test fun cancelledSourceDoesNotCompleteCopy() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val c = app.container
        c.repository.setPinned("example", true)
        val row = c.repository.capture(NotificationFields("example", "Example", "key", null, "Title", "Text", 1), "own", 2)!!
        val remote = RemoteInput.Builder("text").build()
        val token = PendingIntent.getBroadcast(app, 1, Intent("test").setPackage(app.packageName), PendingIntent.FLAG_MUTABLE)
        c.replyActions["key"] = Notification.Action.Builder(null, "Reply", token).addRemoteInput(remote).build()
        c.copies.originalRemoved("key", true)
        token.cancel()
        val intent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(remote), intent, android.os.Bundle().apply { putCharSequence("text", "Hello") })
        val current = c.database.dao().notificationById(row.id)!!
        assertFalse(c.copies.reply(row.id, current.actionVersion, intent))
        assertEquals(current.state, c.database.dao().notificationById(row.id)!!.state)
        c.database.close()
    }

    @Test fun groupedReplyTargetsItsOriginalAndKeepsOtherMembers() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val c = app.container
        c.repository.setPinned("example", true)
        val first = c.repository.capture(NotificationFields("example", "Example", "first", null, "Chat", "First", 1), "own", 2)!!
        c.copies.originalRemoved("first", true)
        val second = c.repository.capture(NotificationFields("example", "Example", "second", null, "Chat", "Second", 3), "own", 4)!!
        val remote = RemoteInput.Builder("reply").build()
        c.replyActions["second"] = Notification.Action.Builder(null, "Reply",
            PendingIntent.getBroadcast(app, 1, Intent("reply-test").setPackage(app.packageName), PendingIntent.FLAG_MUTABLE))
            .addRemoteInput(remote).build()
        c.copies.originalRemoved("second", true)
        c.copies.recover()
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals("Reply", manager.activeNotifications.single().notification.actions[1].title)
        val intent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(remote), intent, android.os.Bundle().apply { putCharSequence("reply", "Hello") })
        assertTrue(c.copies.reply(second.id, c.database.dao().notificationById(second.id)!!.actionVersion, intent))
        assertEquals(com.husarp.holdfast.data.NotificationState.DONE, c.database.dao().notificationById(second.id)!!.state)
        assertEquals(com.husarp.holdfast.data.NotificationState.COPY_VISIBLE, c.database.dao().notificationById(first.id)!!.state)
        assertEquals(1, manager.activeNotifications.size)
        c.database.close()
    }
}
