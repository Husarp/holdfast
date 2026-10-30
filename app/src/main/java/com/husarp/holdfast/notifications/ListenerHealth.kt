package com.husarp.holdfast.notifications

import android.app.*
import android.content.*
import com.husarp.holdfast.MainActivity
import com.husarp.holdfast.R
import androidx.work.*

fun listenerUnhealthy(context: Context): Boolean {
    val prefs = context.getSharedPreferences("health", 0)
    if (!prefs.getBoolean("used", false)) return false
    val enabled = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        .split(':').mapNotNull(ComponentName::unflattenFromString).any { it == ComponentName(context, HoldfastListenerService::class.java) }
    val disconnected = prefs.getLong("disconnected", 0)
    return !enabled || (disconnected > 0 && System.currentTimeMillis() - disconnected >= 900_000)
}
fun listenerHealth(context: Context, connected: Boolean) {
    val prefs = context.getSharedPreferences("health", 0)
    prefs.edit().putBoolean("used", true).putLong("disconnected", if (connected) 0 else prefs.getLong("disconnected", 0).takeIf { it > 0 } ?: System.currentTimeMillis()).apply()
    if (connected) {
        context.getSystemService(NotificationManager::class.java).cancel("health", 1)
        WorkManager.getInstance(context).cancelUniqueWork("listener-disconnected")
    } else {
        WorkManager.getInstance(context).enqueueUniqueWork("listener-disconnected", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ListenerHealthWorker>().setInitialDelay(15, java.util.concurrent.TimeUnit.MINUTES).build())
        if (listenerUnhealthy(context)) WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ListenerHealthWorker>().build())
    }
}
class ListenerHealthWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (listenerUnhealthy(applicationContext)) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("health", "Listener health", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = Intent(applicationContext, MainActivity::class.java).putExtra("setup", true)
            try { manager.notify("health", 1, Notification.Builder(applicationContext, "health").setSmallIcon(R.drawable.ic_holdfast)
                .setContentTitle("Holdfast isn't receiving notifications").setContentText("Open Setup to restore notification access.")
                .setContentIntent(PendingIntent.getActivity(applicationContext, 92, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true).build()) }
            catch (_: SecurityException) { }
        }
        return Result.success()
    }
}
