package com.husarp.holdfast.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.NotificationState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class HoldfastListenerService : NotificationListenerService() {
    private val container get() = (application as HoldfastApplication).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (event in events) {
                try {
                    event()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SQLiteException) {
                    Log.w("HoldfastListener", "Could not save notification data")
                } catch (_: SecurityException) {
                    Log.w("HoldfastListener", "Notification access is unavailable")
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        events.trySend { capture(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (sbn.packageName == packageName && !isDebugSource(sbn)) return
        val eligible = sbn.notification.flags and
            (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) == 0
        events.trySend { container.copies.removed(sbn.key, if (eligible) reason else -1) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        container.listenerConnected.value = true
        listenerHealth(this, true)
        val active = try {
            activeNotifications ?: return
        } catch (_: SecurityException) {
            container.listenerConnected.value = false
        listenerHealth(this, false)
            return
        }
        events.trySend {
            active.forEach { capture(it) }
            container.repository.reconcileActiveKeys(active.map { it.key }, System.currentTimeMillis())
            val actionable = container.database.dao().let { dao ->
                (container.contentIntents.keys + container.replyActions.keys).filter { key ->
                    val state = dao.notification(key)?.state
                    state != null && state != NotificationState.DONE && state != NotificationState.EXPIRED
                }.toSet()
            }
            container.contentIntents.keys.retainAll(actionable)
            container.replyActions.keys.retainAll(actionable)
            container.appearance.originals.keys.retainAll(actionable)
            container.copies.recover()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        container.listenerConnected.value = false
        listenerHealth(this, false)
        try { requestRebind(android.content.ComponentName(this, HoldfastListenerService::class.java)) }
        catch (_: SecurityException) { }
    }

    private fun isDebugSource(sbn: StatusBarNotification) = sbn.packageName == packageName && sbn.notification.channelId == DEBUG_CHANNEL

    private suspend fun capture(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val flags = notification.flags
        val serial = getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(sbn.user)
        if (serial < 0) return
        // Check eligibility before loading another app's label or reading its content.
        if ((!isDebugSource(sbn) && (sbn.packageName == packageName || !container.repository.isPinned(sbn.packageName, serial))) ||
            flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName
        }
        val badgedLabel = packageManager.getUserBadgedLabel(label, sbn.user).toString()
        val fields = NotificationFields(sbn.packageName, badgedLabel, sbn.key, notification.channelId,
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            (notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(), sbn.postTime, debugSource = isDebugSource(sbn),
            shortcutId = notification.shortcutId, userSerial = serial,
            sender = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages?.lastOrNull()?.person?.name?.toString())
        notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }?.let { container.replyActions[sbn.key] = it }
            ?: container.replyActions.remove(sbn.key)
        var saved = container.repository.capture(fields, packageName, System.currentTimeMillis()) ?: return
        container.appearance.save(saved, notification)
        notification.channelId?.let { id ->
            val ranking = Ranking()
            val name = if (currentRanking.getRanking(sbn.key, ranking)) ranking.channel?.name?.toString() ?: id else id
            container.database.dao().saveChannel(com.husarp.holdfast.data.SeenChannel(saved.packageName, id, name))
        }
        if (container.repository.policy(saved).mode == com.husarp.holdfast.data.CopyMode.TAKE_OVER && !container.repository.isMuted(saved)) {
            notification.contentIntent?.let { container.contentIntents[sbn.key] = it }
            cancelNotification(sbn.key)
            container.copies.takeOver(saved.id)
            return
        }
        if (saved.state == NotificationState.COPY_VISIBLE || saved.state == NotificationState.COPY_WAITING) {
            container.copies.recover()
            saved = container.database.dao().notificationById(saved.id) ?: return
        }
        if (saved.state == NotificationState.DONE || saved.state == NotificationState.EXPIRED) {
            container.contentIntents.remove(sbn.key)
            container.replyActions.remove(sbn.key)
            container.appearance.delete(sbn.key)
        } else {
            notification.contentIntent?.let { container.contentIntents[sbn.key] = it }
                ?: container.contentIntents.remove(sbn.key)
        }
    }

    override fun onDestroy() {
        container.listenerConnected.value = false
        listenerHealth(this, false)
        events.close()
        scope.cancel()
        super.onDestroy()
    }
}
