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

    suspend fun removed(key: String, reason: Int) {
        val row = dao.notification(key) ?: return
        val mode = container.repository.policy(row).mode
        if (mode == CopyMode.TAKE_OVER) return
        originalRemoved(key, copiesOnRemoval(mode, reason))
    }

    suspend fun takeOver(id: Long) = mutex.withLock {
        val row = dao.notificationById(id) ?: return@withLock
        if (row.state != NotificationState.ACTIVE_ORIGINAL) { refresh(); return@withLock }
        dao.saveNotification(row.copy(state = NotificationState.COPY_WAITING, nextShowAt = System.currentTimeMillis(), copyId = row.id.toInt()))
        refresh()
    }

    suspend fun originalRemoved(key: String, userDismissal: Boolean) = mutex.withLock {
        val row = dao.notification(key) ?: return@withLock
        if (row.digest && !userDismissal && (row.state == NotificationState.COPY_WAITING || row.state == NotificationState.COPY_VISIBLE)) {
            dao.saveNotification(row.copy(state = NotificationState.DONE, nextShowAt = null, nextRepeatAt = null, updatedAt = System.currentTimeMillis()))
            container.contentIntents.remove(key)
            container.replyActions.remove(key)
            container.appearance.delete(key)
            refresh()
            return@withLock
        }
        if (row.state != NotificationState.ACTIVE_ORIGINAL) return@withLock
        if (!userDismissal || container.repository.isMuted(row)) {
            dao.saveNotification(row.copy(state = NotificationState.DONE, updatedAt = System.currentTimeMillis()))
            container.contentIntents.remove(key)
            container.replyActions.remove(key)
            container.appearance.delete(key)
            return@withLock
        }
        val options = container.repository.options(row)
        val copy = row.copy(isVip = options.isVip(row), nextRepeatAt = repeatAt(System.currentTimeMillis(), options.repeatMinutes),state = NotificationState.COPY_VISIBLE, copyId = row.id.toInt(), updatedAt = System.currentTimeMillis(), actionVersion = row.actionVersion + 1,
            firstCopyAt = System.currentTimeMillis(), expireAt = expiryAt(System.currentTimeMillis(), effectiveExpiry(container.repository.settings.first(), options)))
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
        val delay = effectiveDelay(container.repository.settings.first(), container.repository.options(row)) * 60_000L
        val waiting = row.copy(state = NotificationState.COPY_WAITING, updatedAt = System.currentTimeMillis(), nextShowAt = System.currentTimeMillis() + delay)
        group(row).forEach { dao.saveNotification(it.copy(state = waiting.state, nextShowAt = waiting.nextShowAt)) }
        refresh()
    }

    suspend fun snooze(id: Long, hours: Int, version: Long? = null) = snoozeTo(id, System.currentTimeMillis() + hours * 3_600_000L, version)

    suspend fun snoozeTo(id: Long, until: Long, version: Long? = null) = mutex.withLock {
        val row = dao.notificationById(id) ?: return@withLock
        if (version != null && version != row.actionVersion) return@withLock
        if (row.state != NotificationState.COPY_VISIBLE) return@withLock
        val now = System.currentTimeMillis()
        if (isExpired(row, now)) { refresh(); return@withLock }
        group(row).forEach { dao.saveNotification(it.copy(state = NotificationState.COPY_WAITING,
            nextShowAt = until, updatedAt = now, actionVersion = row.actionVersion + 1)) }
        row.copyId?.let { manager.cancel("copy", it) }
        refresh()
    }

    suspend fun action(id: Long, version: Long, action: String) {
        val row = dao.notificationById(id) ?: return
        if (row.actionVersion != version || row.state != NotificationState.COPY_VISIBLE) return
        when {
            action.startsWith("snooze:") && validButton(action) -> snoozeTo(id, System.currentTimeMillis() + action.substringAfter(':').toLong() * 60_000L, version)
            action.startsWith("until:") && validButton(action) -> snoozeTo(id, snoozeUntil(System.currentTimeMillis(), action.substringAfter(':')), version)
            action.startsWith("tomorrow:") && validButton(action) -> snoozeTo(id, snoozeTomorrow(System.currentTimeMillis(), action.substringAfter(':')), version)
            action == "done" || action == "dismiss" -> complete(id, version)?.let { container.contentIntents.remove(it.sourceKey) }
            action == "muteToday" -> mutex.withLock {
                if (dao.notificationById(id)?.let { it.actionVersion == version && it.state == NotificationState.COPY_VISIBLE } != true) return@withLock
                val options = container.repository.options(row)
                dao.saveOptions(options.copy(mutedUntil = tomorrow(System.currentTimeMillis())))
                refresh()
            }
            action == "muteType" -> mutex.withLock {
                if (dao.notificationById(id)?.let { it.actionVersion == version && it.state == NotificationState.COPY_VISIBLE } != true) return@withLock
                dao.saveType(TypePolicy(packageName = row.packageName, profileId = container.repository.activeProfile(),
                    kind = if (row.channelId != null) "Channel" else "Title", value = row.channelId ?: row.title.ifBlank { row.text.ifBlank { row.sourceKey } }, mode = CopyMode.OFF))
                refresh()
            }
        }
    }

    suspend fun complete(id: Long, version: Long? = null): CapturedNotification? = mutex.withLock {
        val row = dao.notificationById(id) ?: return@withLock null
        if (version != null && version != row.actionVersion) return@withLock null
        if (row.state != NotificationState.COPY_VISIBLE && row.state != NotificationState.COPY_WAITING) return@withLock null
        if (isExpired(row, System.currentTimeMillis())) { refresh(); return@withLock null }
        group(row).forEach { container.replyActions.remove(it.sourceKey); container.appearance.delete(it.sourceKey); dao.saveNotification(it.copy(state = NotificationState.DONE, nextShowAt = null, nextRepeatAt = null, updatedAt = System.currentTimeMillis())); if (it.id != row.id) container.contentIntents.remove(it.sourceKey) }
        row.copyId?.let { manager.cancel("copy", it) }
        refresh()
        row
    }

    suspend fun bringBack(event: NotificationEvent) = mutex.withLock {
        val old = dao.notificationById(event.notificationId)
        val now = System.currentTimeMillis()
        val row = old ?: CapturedNotification(packageName = event.packageName, userSerial = event.userSerial,
            appLabel = event.appLabel, sourceKey = "history:${event.id}", channelId = null,
            title = event.title, text = event.text, postedAt = event.capturedAt, updatedAt = now)
        val options = container.repository.options(row)
        dao.saveNotification(row.copy(state = NotificationState.COPY_WAITING, nextShowAt = now,
            policyDeferred = false, resumeAt = null, firstCopyAt = null,
            expireAt = expiryAt(now, effectiveExpiry(container.repository.settings.first(), options)), actionVersion = row.actionVersion + 1))
        refresh(true)
    }

    suspend fun undo(rows: List<CapturedNotification>) = mutex.withLock {
        rows.forEach { row ->
            val current = dao.notificationById(row.id)
            if (current?.state == NotificationState.DONE)
                dao.saveNotification(row.copy(actionVersion = current.actionVersion + 1))
        }
        refresh(true)
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
        container.appearance.delete(row.sourceKey)
        container.contentIntents.remove(row.sourceKey)
        row.copyId?.let { manager.cancel("copy", it) }
        refresh(true)
        true
    }

    suspend fun recover(repost: Boolean = true, clockChanged: Boolean = false) = mutex.withLock {
        if (clockChanged) {
            val now = System.currentTimeMillis()
            dao.allNotifications().filter { it.digest && it.firstCopyAt == null && it.state == NotificationState.COPY_WAITING }.forEach { row ->
                val due = nextWeeklyDigestAt(now, container.repository.options(row).digestTimes)
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
            val pause = container.repository.settings.first().pausedUntil?.takeIf { it > now }
            val mutedUntil = options.mutedUntil.takeIf { it > now }
            val quietUntil = listOfNotNull(pause, mutedUntil, container.repository.quietUntil(row, now)).maxOrNull()
            val unselected = row.packageName != context.packageName && !container.repository.isPinned(row.packageName, row.userSerial)
            if (isExpired(row, now) || (mutedUntil == null && container.repository.isMuted(row))) {
                dao.saveNotification(row.copy(state = if (isExpired(row, now)) NotificationState.EXPIRED else NotificationState.DONE,
                    nextShowAt = null, nextRepeatAt = null, updatedAt = now))
                row.copyId?.let { manager.cancel("copy", it) }
                group(row).filter { it.id != row.id && it.state == NotificationState.COPY_VISIBLE }.forEach { posts[copyGroupKey(it)] = it.id to false }
                container.contentIntents.remove(row.sourceKey)
                container.replyActions.remove(row.sourceKey)
                container.appearance.delete(row.sourceKey)
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
                val visible = row.copy(state = NotificationState.COPY_VISIBLE, nextShowAt = null, returnCount = row.returnCount + if (row.firstCopyAt != null) 1 else 0,
                    firstCopyAt = row.firstCopyAt ?: now,
                    expireAt = if (row.firstCopyAt == null) expiryAt(now, effectiveExpiry(container.repository.settings.first(), options)) else row.expireAt, nextRepeatAt = repeatAt(now, options.repeatMinutes), actionVersion = row.actionVersion + 1)
                dao.saveNotification(visible)
                posts[copyGroupKey(visible)] = visible.id to false
            } else if (row.state == NotificationState.COPY_VISIBLE && (row.nextRepeatAt ?: Long.MAX_VALUE) <= now) {
                val repeated = row.copy(nextRepeatAt = repeatAt(now, options.repeatMinutes))
                group(row).forEach { dao.saveNotification(it.copy(nextRepeatAt = repeated.nextRepeatAt)) }
                posts[copyGroupKey(repeated)] = repeated.id to true
            } else if (row.state == NotificationState.COPY_VISIBLE) {
                if (options.repeatMinutes == null && row.nextRepeatAt != null || options.repeatMinutes != null && row.nextRepeatAt == null)
                    dao.saveNotification(row.copy(nextRepeatAt = repeatAt(now, options.repeatMinutes)))
                if (repost) posts.putIfAbsent(copyGroupKey(row), row.id to false)
            }
        }
        dao.allNotifications().filter { it.state == NotificationState.COPY_VISIBLE }.forEach {
            posts.putIfAbsent(copyGroupKey(it), it.id to false)
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
    fun open(row: CapturedNotification, keep: Boolean = false): Boolean {
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
            (if (keep) container.contentIntents[row.sourceKey] else container.contentIntents.remove(row.sourceKey))?.let {
                val options = if (android.os.Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(if (android.os.Build.VERSION.SDK_INT >= 36)
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
                it.send(context, 0, null, null, null, null, options)
                return true
            }
        } catch (_: PendingIntent.CanceledException) { container.contentIntents.remove(row.sourceKey) } catch (_: SecurityException) { container.contentIntents.remove(row.sourceKey) }
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
        val requestCode = "${row.id}:$action".hashCode()
        return if (activity) PendingIntent.getActivity(context, requestCode, intent, flags) else PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private suspend fun copyGroupKey(row: CapturedNotification): List<String> {
        val policy = container.repository.policy(row)
        return groupingKey(row) + listOf(if (row.digest) "" else row.channelId.orEmpty(), policy.mode, policy.openAndFinish.toString(), policy.buttons)
    }

    private suspend fun group(row: CapturedNotification): List<CapturedNotification> = dao.allNotifications().filter {
        it.state == row.state && copyGroupKey(it) == copyGroupKey(row) &&
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
        val silent = container.repository.policy(row).mode == CopyMode.TAKE_OVER
        if (silent) manager.createNotificationChannel(NotificationChannel("take_over", "Silent take-over copies", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        val builder = container.appearance.builder(row, if (silent) "take_over" else if (row.isVip) "vip" else "copies")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(!alert)
            .setContentIntent(pending(row, "open", true)).setDeleteIntent(pending(row, "swipe"))
        val sourceActions = container.appearance.originals[row.sourceKey]?.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() }
        fillButtonSlots(container.repository.policy(row).buttons, replyRow != null, sourceActions.size).forEach { button ->
            when {
                button.startsWith("original:") -> builder.addAction(sourceActions[button.substringAfter(':').toInt()])
                button == "reply" -> replyRow?.let { target -> container.replyActions[target.sourceKey]?.let { source ->
                    val action = Notification.Action.Builder(null, "Reply", pending(target, "reply"))
                    source.remoteInputs.forEach { action.addRemoteInput(it) }; builder.addAction(action.build())
                } }
                else -> builder.addAction(Notification.Action.Builder(null, buttonLabel(button), pending(row, button, button == "openKeep" || button == "copyText")).build())
            }
        }
        if (members.size > 1 || row.digest) {
            val inbox = Notification.InboxStyle().setBigContentTitle(if (row.digest) "${row.appLabel} digest" else row.title)
            members.takeLast(7).forEach { inbox.addLine(if (row.digest) "${it.title}: ${it.text}" else it.text) }
            builder.setStyle(inbox).setContentText("${members.size} notifications")
            if (row.digest) builder.setContentTitle("${row.appLabel} digest")
        }
        members.filter { !it.isVip }.mapNotNull { it.expireAt }.minOrNull()?.let {
            if (members.none { member -> member.isVip }) builder.setTimeoutAfter((it - now).coerceAtLeast(1))
        }
        if (container.appearance.load(row.sourceKey)?.largeIcon == null) try { builder.setLargeIcon(com.husarp.holdfast.ui.profileIcon(context, row.packageName, row.userSerial).toBitmap(128, 128)) }
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
                else container.copies.action(id, intent.getLongExtra("version", -1), intent.action.orEmpty())
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
            val policy = opening?.let { container.repository.policy(it) }
            val keep = intent.action == "openKeep" || intent.action == "copyText" || policy?.openAndFinish == false
            val row = if (keep) opening?.takeIf { it.actionVersion == intent.getLongExtra("version", -1) && it.state == NotificationState.COPY_VISIBLE && !isExpired(it, System.currentTimeMillis()) }
                else container.copies.complete(intent.getLongExtra("id", -1), intent.getLongExtra("version", -1))
            if (row != null && opening != null && intent.action != "copyText") container.database.dao().saveOpen(CopyOpen(packageName = row.packageName, openedAt = System.currentTimeMillis()))
            withContext(Dispatchers.Main) {
                if (row != null && intent.action == "copyText") {
                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText(row.title, "${row.title}\n${row.text}"))
                    android.widget.Toast.makeText(this@CopyOpenActivity, "Text copied", android.widget.Toast.LENGTH_SHORT).show()
                } else if (row != null && !container.copies.open(row, keep)) android.widget.Toast.makeText(this@CopyOpenActivity,
                    "The app is frozen in its profile or is unavailable.", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
