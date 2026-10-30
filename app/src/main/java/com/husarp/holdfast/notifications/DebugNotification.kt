package com.husarp.holdfast.notifications

import android.app.*
import android.content.*
import com.husarp.holdfast.MainActivity

const val DEBUG_CHANNEL = "debug_source"

fun sendTestNotification(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(DEBUG_CHANNEL, "Test source", NotificationManager.IMPORTANCE_DEFAULT))
    if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(DEBUG_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
    val click = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    return try {
        manager.notify("debug_source", System.currentTimeMillis().toInt(), Notification.Builder(context, DEBUG_CHANNEL)
            .setSmallIcon(com.husarp.holdfast.R.drawable.ic_holdfast).setContentTitle("Holdfast test")
            .setContentText("Swipe this notification to create a pinned copy.").setContentIntent(click).build())
        true
    } catch (_: SecurityException) { false }
}
