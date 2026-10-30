package com.husarp.holdfast

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.notifications.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugNotificationTest {
    private val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    @Test fun sampleUsesOnlyDebugChannel() {
        assertTrue(sendTestNotification(app))
        val sample = manager.activeNotifications.single().notification
        assertEquals(DEBUG_CHANNEL, sample.channelId)
        assertNotNull(sample.contentIntent)
        assertNull(sample.deleteIntent)
    }
    @Test fun deniedPostingReturnsFailureWithoutPublishing() {
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(sendTestNotification(app))
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
