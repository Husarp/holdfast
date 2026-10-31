package com.husarp.holdfast

import android.app.Application
import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap
import androidx.room.Room
import com.husarp.holdfast.data.HoldfastDatabase
import com.husarp.holdfast.data.HoldfastRepository

class HoldfastApplication : Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration get() = androidx.work.Configuration.Builder().build()
    override fun onCreate() {
        super.onCreate()
        com.husarp.holdfast.update.Updates.onAppStart(this)
        if (getSharedPreferences("health", 0).getBoolean("used", false)) com.husarp.holdfast.notifications.listenerHealth(this, false)
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("listener-health", androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.husarp.holdfast.notifications.ListenerHealthWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build())
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "history-cleanup", androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.husarp.holdfast.scheduling.HistoryCleanupWorker>(
                1, java.util.concurrent.TimeUnit.DAYS).build())
    }
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val database = Room.databaseBuilder(application, HoldfastDatabase::class.java, "holdfast.db")
        .addMigrations(com.husarp.holdfast.data.MIGRATION_1_2, com.husarp.holdfast.data.MIGRATION_2_3).build()
    val appearance = com.husarp.holdfast.notifications.OriginalAppearance(application)
    val replyActions = ConcurrentHashMap<String, android.app.Notification.Action>()
    val contentIntents = ConcurrentHashMap<String, PendingIntent>()
    val listenerConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val scheduler = com.husarp.holdfast.scheduling.CopyScheduler(application)
    val copies = com.husarp.holdfast.notifications.CopyController(application, this)
    val repository = HoldfastRepository(database) { com.husarp.holdfast.location.locationPermitted(application) }
}
