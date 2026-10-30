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
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "history-cleanup", androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.husarp.holdfast.scheduling.HistoryCleanupWorker>(
                1, java.util.concurrent.TimeUnit.DAYS).build())
    }
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val database = Room.databaseBuilder(application, HoldfastDatabase::class.java, "holdfast.db")
        .fallbackToDestructiveMigration(true).build()
    val replyActions = ConcurrentHashMap<String, android.app.Notification.Action>()
    val contentIntents = ConcurrentHashMap<String, PendingIntent>()
    val listenerConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val scheduler = com.husarp.holdfast.scheduling.CopyScheduler(application)
    val copies = com.husarp.holdfast.notifications.CopyController(application, this)
    val repository = HoldfastRepository(database) { com.husarp.holdfast.location.locationPermitted(application) }
}
