package com.husarp.holdfast

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.scheduling.CopyScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CopySchedulerTest {
    private val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
    private val manager = app.getSystemService(AlarmManager::class.java)
    private val scheduler = CopyScheduler(app)
    @Test fun deniedExactAccessUsesInexactAlarm() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.schedule(1000)
        val alarm = shadowOf(manager).peekNextScheduledAlarm()!!
        assertEquals(1000L, alarm.triggerAtTime)
        assertFalse(alarm.windowLengthMs == 0L)
    }
    @Test fun exactAccessUsesExactAlarmAndReplacesPreviousAlarm() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(1000); scheduler.schedule(2000)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        val alarm = shadowOf(manager).peekNextScheduledAlarm()!!
        assertEquals(2000L, alarm.triggerAtTime)
        assertEquals(0L, alarm.windowLengthMs)
        scheduler.schedule(null)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }
}
