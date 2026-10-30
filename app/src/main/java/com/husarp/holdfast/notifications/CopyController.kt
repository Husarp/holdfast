package com.husarp.holdfast.notifications

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import com.husarp.holdfast.*
import com.husarp.holdfast.data.*
import com.husarp.holdfast.scheduling.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

class CopyController(private val context: Context, private val container: AppContainer) {
    private val mutex get() = container.repository.notificationMutex
    private val dao get() = container.database.dao()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    suspend fun originalRemoved(key: String, userDismissal: Boolean) = mutex.withLock {
        val row = dao.notification(key) ?: return@withLock
        if (row.digest && !userDismissal && (row.state == NotificationState.COPY_WAITING || row.state == NotificationState.COPY_VISIBLE)) {
            dao.saveNotification(row.copy(state = NotificationState.DONE, nextShowAt = null, nextRepeatAt = null, updatedAt = System.currentTimeMillis()))
            container.contentIntents.remove(key)
            container.replyActions.remove(key)
            refresh()
            return@withLock
        }
        if (row.state != NotificationState.ACTIVE_ORIGINAL) return@withLock
        if (!userDismissal || container.repository.isMuted(row)) {
            dao.saveNotification(row.copy(state = NotificationState.DONE, updatedAt = System.currentTimeMillis()))
            container.contentIntents.remove(key)
            container.replyActions.remove(key)
            return@withLock
        }
        val options = container.repository.options(row)
        val copy = row.copy(isVip = options.isVip(row), nextRepeatAt = repeatAt(System.currentTimeMillis(), options.repeatMinutes),state = NotificationState.COPY_VISIBLE, copyId = row.id.toInt(), updatedAt = System.currentTimeMillis(), actionVersion = row.actionVersion + 1,
            firstCopyAt = System.currentTimeMillis(), expireAt = expiryAt(System.currentTimeMillis(), container.repository.settings.first().expireAfterMinutes))
        val members = group(copy)
        val version = (members.maxOfOrNull { it.actionVersion } ?: copy.actionVersion) + 1
        val leader = members.minOfOrNull { it.copyId ?: it.id.toInt() } ?: copy.id.toInt()
        members.forEach { member -> dao.saveNotification(member.copy(copyId = leader, actionVersion = version)) }
        dao.saveNotification(copy.copy(copyId = leader, actionVersion = version))
        refresh(true)
    }

    suspend fun swipe(id: Long, version: Long? = null) = mutex.withLock {
        val row = dao.notificationById(id) ?: return@withLock
        if (version != null && version != row.actionVersion) return@withLock
        if (row.state != NotificationState.COPY_VISIBLE) return@withLock
        if (isExpired(row, System.currentTimeMillis())) { refresh(); return@withLock }
        val delay = container.repository.settings.first().returnDelayMinutes * 60_000L
        val waiting = row.copy(state = NotificationState.COPY_WAITING, updatedAt = System.currentTimeMillis(), nextShowAt = System.currentTimeMillis() + delay)
        group(row).forEach { dao.saveNotification(it.copy(state = waiting.state, nextShowAt = waiting.nextShowAt)) }
        refresh()
    }

    suspend fun snooze(id: Long, hours: Int, version: Long? = null) = mutex.withLock {
        require(hours == 1 || hours == 3)
        val row = dao.notificationById(id) ?: return@withLock
        if (version != null && version != row.actionVersion) return@withLock
        if (row.state != NotificationState.COPY_VISIBLE) return@withLock
        val now = System.currentTimeMillis()
        if (isExpired(row, now)) { refresh(); return@withLock }
        group(row).forEach { dao.saveNotification(it.copy(state = NotificationState.COPY_WAITING,
            nextShowAt = now + hours * 3_600_000L, updatedAt = now, actionVersion = row.actionVersion + 1)) }
        row.copyId?.let { manager.cancel("copy", it) }
        refresh()
    }

    suspend fun complete(id: Long, version: Long? = null): CapturedNotification? = mutex.withLock {
        val row = dao.notificationById(id) ?: return@withLock null
        if (version != null && version != row.actionVersion) return@withLock null
        if (row.state != NotificationState.COPY_VISIBLE && row.state != NotificationState.COPY_WAITING) return@withLock null
        if (isExpired(row, System.currentTimeMillis())) { refresh(); return@withLock null }
        group(row).forEach { container.replyActions.remove(it.sourceKey); dao.saveNotification(it.copy(state = NotificationState.DONE, nextShowAt = null, nextRepeatAt = null, updatedAt = System.currentTimeMillis())); if (it.id != row.id) container.contentIntents.remove(it.sourceKey) }
        row.copyId?.let { manager.cancel("copy", it) }
        refresh()
        row
    }

    suspend fun reply(id: Long, version: Long, intent: Intent): Boolean = mutex.withLock {
        val row = dao.notificationById(id) ?: return@withLock false
        if (row.actionVersion != version || row.state != NotificationState.COPY_VISIBLE || isExpired(row, System.currentTimeMillis())) return@withLock false
        val action = container.replyActions[row.sourceKey] ?: return@withLock false
        val results = RemoteInput.getResultsFromIntent(intent) ?: return@withLock false
        val fill = Intent()
        RemoteInput.addResultsToIntent(action.remoteInputs, fill, results)
        try { action.actionIntent.send(context, 0, fill) }
        catch (_: PendingIntent.CanceledException) { return@withLock false }
        catch (_: SecurityException) { return@withLock false }
        dao.saveNotification(row.copy(state = NotificationState.DONE, nextShowAt = null, nextRepeatAt = null, updatedAt = System.currentTimeMillis()))
        container.replyActions.remove(row.sourceKey)
        container.contentIntents.remove(row.sourceKey)
        row.copyId?.let { manager.cancel("copy", it) }
        refresh(true)
        true
    }

    suspend fun recover(repost: Boolean = true, clockChanged: Boolean = false) = mutex.withLock {
        if (clockChanged) {
            val now = System.currentTimeMillis()
            dao.allNotifications().filter { it.digest && it.firstCopyAt == null && it.state == NotificationState.COPY_WAITING }.forEach { row ->
                val due = nextDigestAt(now, parseDigestTimes(container.repository.options(row).digestTimes))
                dao.saveNotification(if (row.policyDeferred) row.copy(resumeAt = due) else row.copy(nextShowAt = due))
            }
        }
        refresh(repost)
    }

    private suspend fun refresh(repost: Boolean = false) {
        val now = System.currentTimeMillis()
        val posts = mutableMapOf<List<String>, Pair<Long, Boolean>>()
        dao.allNotifications().forEach { snapshot ->
            val saved = dao.notificationById(snapshot.id) ?: return@forEach
            val options = container.repository.options(saved)
            var row = saved.copy(isVip = options.isVip(saved))
            if (row != saved) dao.saveNotification(row)
            if (row.state != NotificationState.COPY_VISIBLE && row.state != NotificationState.COPY_WAITING) return@forEach
            val quietUntil = container.repository.quietUntil(row, now)
            val unselected = row.packageName != context.packageName && !container.repository.isPinned(row.packageName, row.userSerial)
            if (isExpired(row, now) || container.repository.isMuted(row)) {
                dao.saveNotification(row.copy(state = if (isExpired(row, now)) NotificationState.EXPIRED else NotificationState.DONE,
                    nextShowAt = null, nextRepeatAt = null, updatedAt = now))
                row.copyId?.let { manager.cancel("copy", it) }
                group(row).filter { it.id != row.id && it.state == NotificationState.COPY_VISIBLE }.forEach { posts[groupingKey(it)] = it.id to false }
                container.contentIntents.remove(row.sourceKey)
                container.replyActions.remove(row.sourceKey)
                return@forEach
            }
            if (quietUntil != null || unselected) {
                dao.saveNotification(row.copy(state = NotificationState.COPY_WAITING, policyDeferred = true,
                    resumeAt = if (row.policyDeferred) row.resumeAt else row.nextShowAt,
                    nextShowAt = if (unselected) null else maxOf(quietUntil!!, (if (row.policyDeferred) row.resumeAt else row.nextShowAt) ?: now)))
                row.copyId?.let { manager.cancel("copy", it) }
                return@forEach
            }
            if (row.policyDeferred) {
                row = row.copy(policyDeferred = false, nextShowAt = maxOf(now, row.resumeAt ?: now), resumeAt = null)
                dao.saveNotification(row)
            }
            if (row.state == NotificationState.COPY_WAITING && (row.nextShowAt ?: Long.MAX_VALUE) <= now) {
                val visible = row.copy(state = NotificationState.COPY_VISIBLE, nextShowAt = null,
                    firstCopyAt = row.firstCopyAt ?: now,
                    expireAt = if (row.firstCopyAt == null) expiryAt(now, container.repository.settings.first().expireAfterMinutes) else row.expireAt, nextRepeatAt = repeatAt(now, options.repeatMinutes), actionVersion = row.actionVersion + 1)
                dao.saveNotification(visible)
                posts[groupingKey(visible)] = visible.id to false
            } else if (row.state == NotificationState.COPY_VISIBLE && (row.nextRepeatAt ?: Long.MAX_VALUE) <= now) {
                val repeated = row.copy(nextRepeatAt = repeatAt(now, options.repeatMinutes))
                group(row).forEach { dao.saveNotification(it.copy(nextRepeatAt = repeated.nextRepeatAt)) }
                posts[groupingKey(repeated)] = repeated.id to true
            } else if (row.state == NotificationState.COPY_VISIBLE) {
                if (options.repeatMinutes == null && row.nextRepeatAt != null || options.repeatMinutes != null && row.nextRepeatAt == null)
                    dao.saveNotification(row.copy(nextRepeatAt = repeatAt(now, options.repeatMinutes)))
                if (repost) posts.putIfAbsent(groupingKey(row), row.id to false)
            }
        }
        dao.allNotifications().filter { it.state == NotificationState.COPY_VISIBLE }.forEach {
            posts.putIfAbsent(groupingKey(it), it.id to false)
        }
        posts.values.forEach { (id, alert) -> dao.notificationById(id)?.takeIf { it.state == NotificationState.COPY_VISIBLE }?.let { post(it, alert) } }
        val rows = dao.allNotifications()
        val visibleIds = rows.filter { it.state == NotificationState.COPY_VISIBLE }.mapNotNull { it.copyId }.toSet()
        manager.activeNotifications.filter { it.tag == "copy" && it.id !in visibleIds }.forEach { manager.cancel("copy", it.id) }
        val quietStarts = rows.filter { it.state == NotificationState.COPY_VISIBLE && !it.isVip }
            .mapNotNull { container.repository.quietStarts(it, now) }
        container.scheduler.schedule((listOfNotNull(nextDeadline(rows)) + quietStarts).minOrNull())
    }

    @Suppress("DEPRECATION")
    fun open(row: CapturedNotification): Boolean {
        val users = context.getSystemService(android.os.UserManager::class.java)
        val personal = users.getSerialNumberForUser(android.os.Process.myUserHandle())
        if (useProfileLauncher(row.userSerial, personal)) {
            val user = users.getUserForSerialNumber(row.userSerial) ?: return false
            try {
                val info = context.getSystemService(android.content.pm.LauncherApps::class.java)
                    .getApplicationInfo(row.packageName, 0, user)
                if (!info.enabled || info.flags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED != 0) return false
            } catch (_: PackageManager.NameNotFoundException) { return false }
            catch (_: RuntimeException) { return false }
        }
        try {
            container.contentIntents.remove(row.sourceKey)?.let {
                val options = if (android.os.Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(if (android.os.Build.VERSION.SDK_INT >= 36)
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
                it.send(context, 0, null, null, null, null, options)
                return true
            }
        } catch (_: PendingIntent.CanceledException) { } catch (_: SecurityException) { }
        return try {
            if (useProfileLauncher(row.userSerial, personal)) {
                val user = users.getUserForSerialNumber(row.userSerial) ?: return false
                val launcher = context.getSystemService(android.content.pm.LauncherApps::class.java)
                val activity = launcher.getActivityList(row.packageName, user).firstOrNull() ?: return false
                launcher.startMainActivity(activity.componentName, user, null, null)
                return true
            }
            val intent = context.packageManager.getLaunchIntentForPackage(row.packageName) ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: RuntimeException) { false }
    }

    private fun pending(row: CapturedNotification, action: String, activity: Boolean = false): PendingIntent {
        val intent = Intent(context, if (activity) CopyOpenActivity::class.java else CopyActionReceiver::class.java)
            .setAction(action).setData(Uri.parse("holdfast://copy/${row.id}/${row.actionVersion}/$action")).putExtra("id", row.id).putExtra("version", row.actionVersion)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (action == "reply") PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        val requestCode = (row.id * 6 + when (action) {
            "open" -> 0
            "swipe" -> 1
            "snooze1" -> 2
            "snooze3" -> 3
            "reply" -> 5
            else -> 4
        }).toInt()
        return if (activity) PendingIntent.getActivity(context, requestCode, intent, flags) else PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private suspend fun group(row: CapturedNotification): List<CapturedNotification> = dao.allNotifications().filter {
        it.state == row.state && groupingKey(it) == groupingKey(row) &&
            (row.state == NotificationState.COPY_VISIBLE || it.nextShowAt == row.nextShowAt)
    }.let { if (it.none { member -> member.id == row.id }) it + row else it }

    private suspend fun post(input: CapturedNotification, alert: Boolean = false) {
        var row = dao.notificationById(input.id) ?: return
        val now = System.currentTimeMillis()
        if (isExpired(row, now) || container.repository.isMuted(row) || container.repository.quietUntil(row, now) != null ||
            (row.packageName != context.packageName && !container.repository.isPinned(row.packageName, row.userSerial))) return
        manager.createNotificationChannel(NotificationChannel("copies", "Pinned copies", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("vip", "VIP", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
        })
        val members = group(row).filter { it.state == NotificationState.COPY_VISIBLE }.sortedBy { it.postedAt }
        val version = (members.maxOfOrNull { it.actionVersion } ?: row.actionVersion) + 1
        row = row.copy(actionVersion = version, isVip = members.any { it.isVip })
        val copyId = members.minOfOrNull { it.id.toInt() } ?: return
        members.forEach { member ->
            if (member.copyId != copyId || member.actionVersion != version) {
                dao.saveNotification(member.copy(copyId = copyId, actionVersion = version))
            }
        }
        val replyRow = members.lastOrNull { container.replyActions.containsKey(it.sourceKey) }?.copy(actionVersion = version)
        val builder = Notification.Builder(context, if (row.isVip) "vip" else "copies")
            .setSmallIcon(com.husarp.holdfast.R.drawable.ic_holdfast).setContentTitle(row.title).setContentText(row.text)
            .setStyle(Notification.BigTextStyle().bigText(row.text)).setSubText(row.appLabel)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(!alert)
            .setContentIntent(pending(row, "open", true)).setDeleteIntent(pending(row, "swipe"))
            .addAction(Notification.Action.Builder(null, "Snooze 1h", pending(row, "snooze1")).build())
            .addAction(replyRow?.let { target -> container.replyActions[target.sourceKey]?.let { source ->
                val action = Notification.Action.Builder(null, "Reply", pending(target, "reply"))
                source.remoteInputs.forEach { action.addRemoteInput(it) }; action.build()
            } } ?: Notification.Action.Builder(null, "Snooze 3h", pending(row, "snooze3")).build())
            .addAction(Notification.Action.Builder(null, "Dismiss", pending(row, "dismiss")).build())
        if (members.size > 1 || row.digest) {
            val inbox = Notification.InboxStyle().setBigContentTitle(if (row.digest) "${row.appLabel} digest" else row.title)
            members.takeLast(7).forEach { inbox.addLine(if (row.digest) "${it.title}: ${it.text}" else it.text) }
            builder.setStyle(inbox).setContentText("${members.size} notifications")
            if (row.digest) builder.setContentTitle("${row.appLabel} digest")
        }
        members.filter { !it.isVip }.mapNotNull { it.expireAt }.minOrNull()?.let {
            if (members.none { member -> member.isVip }) builder.setTimeoutAfter((it - now).coerceAtLeast(1))
        }
        try { builder.setLargeIcon(com.husarp.holdfast.ui.profileIcon(context, row.packageName, row.userSerial).toBitmap(128, 128)) }
        catch (_: PackageManager.NameNotFoundException) { }
        try { manager.notify("copy", copyId, builder.build()) } catch (_: SecurityException) { }
    }
}

class CopyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as HoldfastApplication).container
        container.scope.launch {
            try {
                val id = intent.getLongExtra("id", -1)
                if (intent.action == "reply") container.copies.reply(id, intent.getLongExtra("version", -1), intent)
                else if (intent.action == "swipe") container.copies.swipe(id, intent.getLongExtra("version", -1))
                else if (intent.action == "snooze1" || intent.action == "snooze3")
                    container.copies.snooze(id, if (intent.action == "snooze1") 1 else 3, intent.getLongExtra("version", -1))
                else if (intent.action == "dismiss") container.copies.complete(id, intent.getLongExtra("version", -1))?.let { container.contentIntents.remove(it.sourceKey) }
            } finally { pending.finish() }
        }
    }
}

class CopyOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HoldfastApplication).container
        container.scope.launch {
            val opening = container.database.dao().notificationById(intent.getLongExtra("id", -1))
            val row = container.copies.complete(intent.getLongExtra("id", -1), intent.getLongExtra("version", -1))
            if (row != null && opening != null) container.database.dao().saveOpen(CopyOpen(packageName = row.packageName, openedAt = System.currentTimeMillis()))
            withContext(Dispatchers.Main) {
                if (row != null && !container.copies.open(row)) android.widget.Toast.makeText(this@CopyOpenActivity,
                    "The app is frozen in its profile or is unavailable.", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
