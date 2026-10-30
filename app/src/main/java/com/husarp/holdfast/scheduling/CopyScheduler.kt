package com.husarp.holdfast.scheduling

import android.app.*
import android.content.*
import android.os.Build
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

fun expiryAt(now: Long, minutes: Int?): Long? = minutes?.let { now + it * 60_000L }
fun isExpired(row: CapturedNotification, now: Long) = !row.isVip && row.expireAt?.let { it <= now } == true
fun nextDeadline(rows: List<CapturedNotification>): Long? = rows
    .filter { it.state == NotificationState.COPY_VISIBLE || it.state == NotificationState.COPY_WAITING }
    .flatMap { listOfNotNull(if (it.isVip) null else it.expireAt, if (it.state == NotificationState.COPY_VISIBLE) it.nextRepeatAt else null, if (it.state == NotificationState.COPY_WAITING) it.nextShowAt else null) }.minOrNull()

class CopyScheduler(private val context: Context) {
    fun schedule(at: Long?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, RecoveryReceiver::class.java).setAction("due"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        if (at == null) return
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return }
            catch (_: SecurityException) { }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
}

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as HoldfastApplication).container
        container.scope.launch {
            try { if (intent.action != "due") com.husarp.holdfast.location.Places(context).restore(); container.copies.recover(repost = intent.action != "due",
                clockChanged = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) }
            finally { pending.finish() }
        }
    }
}
