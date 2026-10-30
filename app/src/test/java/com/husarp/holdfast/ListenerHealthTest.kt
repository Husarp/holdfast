package com.husarp.holdfast

import android.content.ComponentName
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.notifications.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListenerHealthTest {
    private val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
    private val prefs get() = app.getSharedPreferences("health", 0)
    private fun allowListener() {
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", ComponentName(app, HoldfastListenerService::class.java).flattenToString())
    }
    @Test fun newInstallationDoesNotWarnBeforeSetup() {
        assertFalse(listenerUnhealthy(app))
    }
    @Test fun revokedAccessWarnsImmediatelyAfterSetup() {
        prefs.edit().putBoolean("used", true).apply()
        assertTrue(listenerUnhealthy(app))
        allowListener()
        assertFalse(listenerUnhealthy(app))
    }
    @Test fun disconnectionHasFifteenMinuteGraceAndReconnectClearsIt() {
        allowListener()
        val now = System.currentTimeMillis()
        prefs.edit().putBoolean("used", true).putLong("disconnected", now).apply()
        assertFalse(listenerUnhealthy(app))
        prefs.edit().putLong("disconnected", now - 900_001).apply()
        assertTrue(listenerUnhealthy(app))
        listenerHealth(app, true)
        assertFalse(listenerUnhealthy(app))
    }
}
