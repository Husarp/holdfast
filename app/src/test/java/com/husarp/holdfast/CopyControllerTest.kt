package com.husarp.holdfast

import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import com.husarp.holdfast.notifications.NotificationFields
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CopyControllerTest {
    private val container = ApplicationProvider.getApplicationContext<HoldfastApplication>().container
    private val dao = container.database.dao()
    private suspend fun source(): CapturedNotification {
        container.repository.setPinned("example.app", true)
        return container.repository.capture(NotificationFields("example.app", "Example", "key", "messages", "Title", "Text", 100), "com.husarp.holdfast", 200)!!
    }
    @Test fun pauseDefersCopiesIncludingVipAndResumesAfterDeadline() = runBlocking {
        val row = source()
        dao.saveOptions(AppOptions("example.app", vipSenders = "Title"))
        container.repository.saveSettings(Settings(pausedUntil = System.currentTimeMillis() + 60_000))
        container.copies.originalRemoved("key", true)
        assertEquals(NotificationState.COPY_WAITING, dao.notificationById(row.id)!!.state)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.activeNotifications.none { it.tag == "copy" })
        container.repository.saveSettings(Settings(pausedUntil = System.currentTimeMillis() - 1))
        container.copies.recover()
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(row.id)!!.state)
        assertEquals(1, manager.activeNotifications.count { it.tag == "copy" })
    }
    @Test fun pausePreservesSnooze() = runBlocking {
        val row = source()
        container.copies.originalRemoved("key", true)
        container.copies.snooze(row.id, 3)
        val deadline = dao.notificationById(row.id)!!.nextShowAt
        container.repository.saveSettings(Settings(pausedUntil = Long.MAX_VALUE))
        container.copies.recover()
        container.repository.saveSettings(Settings())
        container.copies.recover()
        assertEquals(deadline, dao.notificationById(row.id)!!.nextShowAt)
        assertEquals(NotificationState.COPY_WAITING, dao.notificationById(row.id)!!.state)
    }
    @Test fun undoRestoresWholeConversation() = runBlocking {
        val row = source()
        container.copies.originalRemoved("key", true)
        val before = dao.notificationById(row.id)!!
        container.copies.complete(row.id)
        container.copies.undo(listOf(before))
        val restored = dao.notificationById(row.id)!!
        assertEquals(NotificationState.COPY_VISIBLE, restored.state)
        assertTrue(restored.actionVersion > before.actionVersion)
    }
    @Test fun appTimingControlsExpiryAndReturn() = runBlocking {
        val row = source()
        container.repository.saveSettings(Settings(returnDelayMinutes = 30, expireAfterMinutes = 60))
        dao.saveOptions(AppOptions("example.app", returnDelayMinutes = 5, overrideExpiry = true))
        container.copies.originalRemoved("key", true)
        assertNull(dao.notificationById(row.id)!!.expireAt)
        val now = System.currentTimeMillis()
        container.copies.swipe(row.id)
        assertTrue(dao.notificationById(row.id)!!.nextShowAt!! in (now + 300_000)..(System.currentTimeMillis() + 300_000))
    }
    @Test fun bringBackStillRespectsSelectionAndMuteRules() = runBlocking {
        val row = source()
        container.copies.originalRemoved("key", true)
        container.copies.complete(row.id)
        container.repository.setPinned("example.app", false)
        val event = NotificationEvent(notificationId = row.id, packageName = row.packageName, appLabel = row.appLabel, title = row.title, text = row.text, capturedAt = row.postedAt)
        container.copies.bringBack(event)
        assertEquals(NotificationState.COPY_WAITING, dao.notificationById(row.id)!!.state)
        container.repository.setPinned("example.app", true)
        dao.addRule(MuteRule(packageName = row.packageName, kind = "Title", value = "Title"))
        container.copies.recover()
        assertEquals(NotificationState.DONE, dao.notificationById(row.id)!!.state)
    }

    @After fun close() = container.database.close()
    @Test fun newArrivalAndDismissDoNotConsumeSnoozedConversation() = runBlocking {
        val first = source()
        container.copies.originalRemoved("key", true)
        container.copies.snooze(first.id, 3)
        val waiting = dao.notificationById(first.id)!!
        val second = container.repository.capture(NotificationFields("example.app", "Example", "second", "messages", "Title", "Second", 101), "own", 201)!!
        container.copies.originalRemoved("second", true)
        container.copies.complete(second.id)
        assertEquals(waiting, dao.notificationById(first.id))
        assertEquals(NotificationState.DONE, dao.notificationById(second.id)!!.state)
    }

    @Test fun conversationUpdateSplitsCardsWithoutIdCollision() = runBlocking {
        val first = source()
        container.copies.originalRemoved("key", true)
        val second = container.repository.capture(NotificationFields("example.app", "Example", "second", "messages", "Title", "Second", 101), "own", 201)!!
        container.copies.originalRemoved("second", true)
        container.repository.capture(NotificationFields("example.app", "Example", "key", "messages", "New chat", "Changed", 102), "own", 202)
        container.copies.recover()
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertEquals(2, manager.activeNotifications.size)
        assertNotEquals(dao.notificationById(first.id)!!.copyId, dao.notificationById(second.id)!!.copyId)
        container.copies.complete(first.id)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(second.id)!!.state)
    }

    @Test fun quietMemberCannotHideVipMemberOfSameConversation() = runBlocking {
        source()
        container.copies.originalRemoved("key", true)
        dao.saveOptions(AppOptions("example.app", vipSenders = "VIP"))
        val vip = container.repository.capture(NotificationFields("example.app", "Example", "vip", "messages", "Title", "VIP line", 101, sender = "VIP"), "own", 201)!!
        container.copies.originalRemoved("vip", true)
        dao.addQuiet(QuietSchedule(packageName = "example.app", days = "1,2,3,4,5,6,7", startMinute = 0, endMinute = 0))
        container.copies.recover(false)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertEquals("vip", manager.activeNotifications.single().notification.channelId)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(vip.id)!!.state)
        assertEquals(NotificationState.COPY_WAITING, dao.notification("key")!!.state)
    }

    @Test fun expiryKeepsSurvivorAndInvalidatesOldGroupedAction() = runBlocking {
        val first = source()
        container.copies.originalRemoved("key", true)
        val second = container.repository.capture(NotificationFields("example.app", "Example", "second", "messages", "Title", "Second", 101), "own", 201)!!
        container.copies.originalRemoved("second", true)
        val oldVersion = dao.notificationById(second.id)!!.actionVersion
        dao.saveNotification(dao.notificationById(first.id)!!.copy(expireAt = 1))
        container.copies.recover(false)
        assertNull(container.copies.complete(second.id, oldVersion))
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(second.id)!!.state)
    }

    @Test fun digestExpiryStartsAtFirstDeliveryAndSurvivesRecovery() = runBlocking {
        container.repository.saveSettings(Settings(expireAfterMinutes = 2))
        dao.saveOptions(AppOptions("example.app", digestOnly = true))
        val row = source()
        assertNull(row.expireAt)
        dao.saveNotification(row.copy(nextShowAt = 1))
        val before = System.currentTimeMillis()
        container.copies.recover()
        val delivered = dao.notificationById(row.id)!!
        assertTrue(delivered.expireAt!! in (before + 120_000)..(System.currentTimeMillis() + 120_000))
        container.copies.swipe(row.id)
        container.copies.recover()
        assertEquals(delivered.expireAt, dao.notificationById(row.id)!!.expireAt)
    }

    @Test fun appCancelledPendingDigestNeverDelivers() = runBlocking {
        dao.saveOptions(AppOptions("example.app", digestOnly = true))
        val row = source()
        dao.saveNotification(row.copy(nextShowAt = 1))
        container.copies.originalRemoved("key", false)
        container.copies.recover()
        assertEquals(NotificationState.DONE, dao.notificationById(row.id)!!.state)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun appCancelledDeliveredDigestRemovesOnlyItsMember() = runBlocking {
        dao.saveOptions(AppOptions("example.app", digestOnly = true))
        val first = source()
        val second = container.repository.capture(NotificationFields("example.app", "Example", "second", "messages", "Other", "Second", 101), "own", 201)!!
        listOf(first, second).forEach { dao.saveNotification(it.copy(nextShowAt = 1)) }
        container.copies.recover()
        container.copies.originalRemoved("key", false)
        assertEquals(NotificationState.DONE, dao.notificationById(first.id)!!.state)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notificationById(second.id)!!.state)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(1, manager.activeNotifications.single().notification.extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)!!.size)
    }

    @Test fun zeroDelaySwipeAppliesNewQuietPolicyBeforePosting() = runBlocking {
        val row = source()
        container.copies.originalRemoved("key", true)
        dao.addQuiet(QuietSchedule(packageName = "example.app", days = "1,2,3,4,5,6,7", startMinute = 0, endMinute = 0))
        container.copies.swipe(row.id)
        assertEquals(NotificationState.COPY_WAITING, dao.notificationById(row.id)!!.state)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun clockRecoveryRecalculatesUndeliveredDigestButPreservesSnooze() = runBlocking {
        val regular = source()
        container.copies.originalRemoved("key", true)
        container.copies.snooze(regular.id, 3)
        val snooze = dao.notificationById(regular.id)!!.nextShowAt
        dao.saveOptions(AppOptions("example.app", digestOnly = true))
        val digest = container.repository.capture(NotificationFields("example.app", "Example", "digest", "messages", "Other", "Pending", System.currentTimeMillis()), "own", System.currentTimeMillis())!!
        dao.saveNotification(digest.copy(nextShowAt = Long.MAX_VALUE))
        container.copies.recover(clockChanged = true)
        assertTrue(dao.notificationById(digest.id)!!.nextShowAt!! < Long.MAX_VALUE)
        assertEquals(snooze, dao.notificationById(regular.id)!!.nextShowAt)
    }

    @Test fun profilesKeepSelectionsAndRulesSeparateAndPauseCopies() = runBlocking {
        source(); container.copies.originalRemoved("key", true)
        container.repository.ensureProfiles()
        container.repository.saveSettings(Settings(activeProfileId = 2))
        assertFalse(container.repository.isPinned("example.app"))
        container.copies.recover()
        assertEquals(NotificationState.COPY_WAITING, dao.notification("key")!!.state)
        container.repository.setPinned("example.app", true)
        dao.addRule(MuteRule(profileId = 1, packageName = "example.app", kind = "Title", value = "Title"))
        container.copies.recover()
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
    }
    @Test fun removingQuietHoursPreservesAnExistingSnoozeDeadline() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true)
        container.copies.snooze(row.id, 3)
        val due = dao.notification("key")!!.nextShowAt
        dao.addQuiet(QuietSchedule(packageName = row.packageName, days = "1,2,3,4,5,6,7", startMinute = 0, endMinute = 0))
        container.copies.recover()
        dao.removeQuiet(container.repository.quiet.first().single().id)
        container.copies.recover()
        assertEquals(due, dao.notification("key")!!.nextShowAt)
        assertEquals(NotificationState.COPY_WAITING, dao.notification("key")!!.state)
    }
    @Test fun quietWaitsButVipBypassesQuietAndExpiryAndRepeatsStopOnDismiss() = runBlocking {
        val row = source()
        dao.addQuiet(QuietSchedule(packageName = row.packageName, days = "1,2,3,4,5,6,7", startMinute = 0, endMinute = 0))
        container.copies.originalRemoved("key", true)
        assertEquals(NotificationState.COPY_WAITING, dao.notification("key")!!.state)
        dao.saveOptions(AppOptions(row.packageName, vipSenders = "Title", repeatMinutes = 5))
        container.copies.recover()
        var visible = dao.notification("key")!!
        assertEquals(NotificationState.COPY_VISIBLE, visible.state)
        dao.saveNotification(visible.copy(expireAt = 1, nextRepeatAt = 1))
        container.copies.recover(false)
        visible = dao.notification("key")!!
        assertTrue(visible.nextRepeatAt!! > System.currentTimeMillis())
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertEquals("vip", manager.activeNotifications.single().notification.channelId)
        assertEquals(0L, manager.activeNotifications.single().notification.timeoutAfter)
        assertNotNull(container.copies.complete(row.id))
        container.copies.recover()
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun groupingPublishesOneInboxAndDismissCompletesEveryMember() = runBlocking {
        val first = source(); container.copies.originalRemoved("key", true)
        val second = container.repository.capture(NotificationFields("example.app", "Example", "second", "messages", "Title", "Second line", 101), "own", 201)!!
        container.copies.originalRemoved("second", true)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        val copy = manager.activeNotifications.single().notification
        assertEquals(2, copy.extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)!!.size)
        container.copies.complete(first.id)
        assertEquals(NotificationState.DONE, dao.notificationById(second.id)!!.state)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun digestWaitsWithoutSwipeThenPublishesOneSummaryAndRecovers() = runBlocking {
        container.repository.setPinned("example.app", true)
        dao.saveOptions(AppOptions("example.app", digestOnly = true))
        val first = source()
        val second = container.repository.capture(NotificationFields("example.app", "Example", "second", "messages", "Other", "Second line", 101), "own", System.currentTimeMillis())!!
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.activeNotifications.isEmpty())
        listOf(first, second).forEach { dao.saveNotification(it.copy(nextShowAt = 1)) }
        container.copies.recover()
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(2, manager.activeNotifications.single().notification.extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)!!.size)
        container.copies.recover()
        assertEquals(1, manager.activeNotifications.size)
        container.copies.complete(second.id)
        assertEquals(NotificationState.DONE, dao.notificationById(first.id)!!.state)
    }
    @Test fun mutedCaptureStaysInHistoryWithoutCopyAndRuleRemovalAllowsFutureCopies() = runBlocking {
        val row = source()
        dao.addRule(MuteRule(packageName = row.packageName, kind = "Title", value = "title"))
        container.copies.originalRemoved("key", true)
        assertEquals(NotificationState.DONE, dao.notification("key")!!.state)
        assertEquals(1, dao.counts(0).single().count)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.activeNotifications.isEmpty())
        dao.removeRule(dao.rules().single().id)
        container.repository.capture(NotificationFields("example.app", "Example", "key", "messages", "Title", "Text", System.currentTimeMillis() + 10), "own", System.currentTimeMillis() + 10)
        container.copies.originalRemoved("key", true)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
    }
    @Test fun addingRuleStopsExistingWaitingAndVisibleCopiesOnRecovery() = runBlocking {
        source(); container.copies.originalRemoved("key", true)
        dao.addRule(MuteRule(packageName = "example.app", kind = "Channel", value = "messages"))
        container.copies.recover()
        assertEquals(NotificationState.DONE, dao.notification("key")!!.state)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun snoozeOneAndThreeHoursPreserveExpiryAndIgnoreRepeatedOrStaleTaps() = runBlocking {
        val row = source()
        container.repository.saveSettings(Settings(expireAfterMinutes = 600))
        container.copies.originalRemoved("key", true)
        val initial = dao.notification("key")!!
        val before = System.currentTimeMillis()
        container.copies.snooze(row.id, 1, initial.actionVersion)
        val waiting = dao.notification("key")!!
        assertEquals(NotificationState.COPY_WAITING, waiting.state)
        assertTrue(waiting.nextShowAt!! in (before + 3_600_000)..(System.currentTimeMillis() + 3_600_000))
        assertEquals(initial.expireAt, waiting.expireAt)
        container.copies.snooze(row.id, 3, initial.actionVersion)
        container.copies.swipe(row.id, initial.actionVersion)
        assertNull(container.copies.complete(row.id, initial.actionVersion))
        assertEquals(waiting, dao.notification("key"))
        dao.saveNotification(waiting.copy(nextShowAt = 1))
        container.copies.recover()
        val returned = dao.notification("key")!!
        val next = System.currentTimeMillis()
        container.copies.snooze(row.id, 3, returned.actionVersion)
        val three = dao.notification("key")!!
        assertTrue(three.nextShowAt!! in (next + 10_800_000)..(System.currentTimeMillis() + 10_800_000))
        assertEquals(initial.expireAt, three.expireAt)
        assertNotNull(container.copies.complete(row.id))
        container.copies.snooze(row.id, 1)
        container.copies.recover()
        assertEquals(NotificationState.DONE, dao.notification("key")!!.state)
    }
    @Test fun expiryWinsOverSnooze() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true)
        dao.saveNotification(dao.notification("key")!!.copy(expireAt = 1))
        container.copies.snooze(row.id, 1)
        assertEquals(NotificationState.EXPIRED, dao.notification("key")!!.state)
        assertNull(dao.notification("key")!!.nextShowAt)
    }
    @Test fun copyButtonsHaveDistinctPendingIntentsPerRowAndAction() = runBlocking {
        source(); container.copies.originalRemoved("key", true)
        container.repository.capture(NotificationFields("example.app", "Example", "other", "messages", "Other", "Text", 100), "own", 200)
        container.copies.originalRemoved("other", true)
        val manager = ApplicationProvider.getApplicationContext<HoldfastApplication>().getSystemService(android.app.NotificationManager::class.java)
        val copies = manager.activeNotifications.map { it.notification }
        assertEquals(2, copies.size)
        copies.forEach { assertEquals(listOf("Snooze 60m", "Dismiss"), it.actions.map { action -> action.title.toString() }) }
        val intents = copies.flatMap { listOf(it.contentIntent, it.deleteIntent) + it.actions.map { action -> action.actionIntent } }
        assertEquals(8, intents.toSet().size)
        assertEquals(8, intents.map { org.robolectric.Shadows.shadowOf(it).requestCode }.toSet().size)
    }
    @Test fun originalSwipeCreatesStableCopyAndDuplicateIsIgnored() = runBlocking {
        val row = source()
        repeat(2) { container.copies.originalRemoved("key", true) }
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
        assertEquals(row.id.toInt(), dao.notification("key")!!.copyId)
    }
    @Test fun appCancellationDoesNotCreateCopy() = runBlocking {
        source(); container.copies.originalRemoved("key", false)
        assertEquals(NotificationState.DONE, dao.notification("key")!!.state)
    }
    @Test fun copySwipeReturnsImmediatelyAtZeroDelay() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true); container.copies.swipe(row.id)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
    }
    @Test fun copySwipeWaitsWithDelayAndDismissStopsReturn() = runBlocking {
        val row = source(); container.repository.saveSettings(Settings(returnDelayMinutes = 5))
        container.copies.originalRemoved("key", true); container.copies.swipe(row.id)
        assertEquals(NotificationState.COPY_WAITING, dao.notification("key")!!.state)
        assertNotNull(dao.notification("key")!!.nextShowAt)
        assertNotNull(container.copies.complete(row.id)); container.copies.swipe(row.id)
        assertEquals(NotificationState.DONE, dao.notification("key")!!.state)
        assertNull(container.copies.complete(row.id))
    }
    @Test fun recoveryPostsDueRowsAndExpiresBeforePosting() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true)
        val visible = dao.notification("key")!!
        dao.saveNotification(visible.copy(state = NotificationState.COPY_WAITING, nextShowAt = 1))
        container.copies.recover()
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
        dao.saveNotification(visible.copy(state = NotificationState.COPY_WAITING, nextShowAt = 1, expireAt = 2))
        container.copies.recover()
        assertEquals(NotificationState.EXPIRED, dao.notification("key")!!.state)
        assertNull(dao.notification("key")!!.nextShowAt)
        assertNull(container.copies.complete(row.id))
    }
    @Test fun expirySurvivesSwipesAndRecovery() = runBlocking {
        val row = source(); container.repository.saveSettings(Settings(returnDelayMinutes = 5, expireAfterMinutes = 60))
        container.copies.originalRemoved("key", true)
        val expiry = dao.notification("key")!!.expireAt
        container.copies.swipe(row.id); container.copies.recover()
        assertEquals(expiry, dao.notification("key")!!.expireAt)
        assertEquals(NotificationState.COPY_WAITING, dao.notification("key")!!.state)
    }
    @Test fun staleSwipeAndDismissCannotAffectReturnedCopy() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true)
        val version = dao.notification("key")!!.actionVersion
        container.copies.swipe(row.id, version)
        val returnedVersion = dao.notification("key")!!.actionVersion
        assertTrue(returnedVersion > version)
        container.copies.swipe(row.id, version)
        assertNull(container.copies.complete(row.id, version))
        assertEquals(returnedVersion, dao.notification("key")!!.actionVersion)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
    }
    @Test fun permissionDenialPreservesCopyForRecovery() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        org.robolectric.Shadows.shadowOf(manager).setNotificationsEnabled(false)
        val row = source(); container.copies.originalRemoved("key", true)
        assertEquals(NotificationState.COPY_VISIBLE, dao.notification("key")!!.state)
        assertTrue(manager.activeNotifications.isEmpty())
        org.robolectric.Shadows.shadowOf(manager).setNotificationsEnabled(true)
        container.copies.recover()
        val copy = manager.activeNotifications.single().notification
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, copy.visibility)
        assertNull(copy.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT))
        assertNotNull(copy.contentIntent); assertNotNull(copy.deleteIntent)
        assertEquals(2, copy.actions.size)
        container.copies.complete(row.id)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun tappingExpiredCopyCannotOverrideExpiry() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true)
        dao.saveNotification(dao.notification("key")!!.copy(expireAt = 1))
        assertNull(container.copies.complete(row.id))
        assertEquals(NotificationState.EXPIRED, dao.notification("key")!!.state)
    }
    @Test fun cachedContentIntentOpensAndIsRemoved() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val row = source(); container.copies.originalRemoved("key", true)
        container.contentIntents["key"] = android.app.PendingIntent.getActivity(app, 0,
            android.content.Intent(app, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
        assertTrue(container.copies.open(container.copies.complete(row.id)!!))
        assertFalse(container.contentIntents.containsKey("key"))
    }
    @Test fun cancelledContentIntentFallsBackWithoutCrashing() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val row = source(); container.copies.originalRemoved("key", true)
        val pending = android.app.PendingIntent.getActivity(app, 0,
            android.content.Intent(app, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
        pending.cancel(); container.contentIntents["key"] = pending
        assertFalse(container.copies.open(container.copies.complete(row.id)!!))
        assertFalse(container.contentIntents.containsKey("key"))
    }
    @Test fun tapCompletesEvenWhenSourceAppIsMissing() = runBlocking {
        val row = source(); container.copies.originalRemoved("key", true)
        val completed = container.copies.complete(row.id)!!
        assertFalse(container.copies.open(completed))
        assertEquals(NotificationState.DONE, dao.notification("key")!!.state)
    }
}
