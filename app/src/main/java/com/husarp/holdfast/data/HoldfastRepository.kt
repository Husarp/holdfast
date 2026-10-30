package com.husarp.holdfast.data

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import com.husarp.holdfast.notifications.NotificationFields
import com.husarp.holdfast.notifications.mapNotification

class HoldfastRepository(private val database: HoldfastDatabase, private val locationAllowed: () -> Boolean = { false }) {
    val spam = database.dao().observeSpam()
    suspend fun checkSpam(now: Long) = notificationMutex.withLock {
        val since = now - 7L * 24 * 60 * 60 * 1000
        val previous = dao.observeSpam().first().map { it.packageName }
        dao.counts(since).forEach {
            if (shouldSuggestSpam(it.count, dao.opens(it.packageName, since) > 0, it.packageName in previous))
                dao.saveSpam(SpamDecision(it.packageName, it.appLabel, it.count))
        }
    }
    suspend fun answerSpam(decision: SpamDecision, mute: Boolean) = notificationMutex.withLock {
        if (mute) setPinned(decision.packageName, false)
        dao.saveSpam(decision.copy(answered = true))
    }
    val notificationMutex = Mutex()
    private val dao = database.dao()
    val options = dao.observeOptions()
    suspend fun options(row: CapturedNotification) = dao.options(row.packageName, activeProfile()) ?: AppOptions(row.packageName, activeProfile())
    val profiles = dao.observeProfiles()
    val quiet = dao.observeQuiet()
    suspend fun ensureProfiles() { listOf("Work", "Home", "Sleep").forEachIndexed { index, name -> dao.insertProfile(Profile(index + 1L, name)) } }
    suspend fun activeProfile() = settings.first().activeProfileId
    suspend fun quietUntil(row: CapturedNotification, now: Long) = if (row.isVip) null else if (placeQuiets(dao.observePlaces().first(), row.packageName, locationAllowed())) now + 86_400_000L else quietEnd(dao.quiet(activeProfile(), row.packageName), now)
    suspend fun quietStarts(row: CapturedNotification, now: Long) = nextQuietStart(dao.quiet(activeProfile(), row.packageName), now)
    val rules = combine(dao.observeRules(), settingsFlow()) { rows, settings -> rows.filter { it.profileId == settings.activeProfileId } }
    private fun settingsFlow() = dao.observeSettings().map { it ?: Settings() }
    val channels = dao.observeChannels()
    suspend fun isMuted(row: CapturedNotification) = dao.rules().any { it.profileId == activeProfile() && it.matches(row) }
    val history = dao.observeHistory()
    val pinnedApps = combine(dao.observePinnedApps(), settingsFlow()) { rows, settings -> rows.filter { it.profileId == settings.activeProfileId } }
    val notifications = dao.observeNotifications()
    val settings = dao.observeSettings().map { it ?: Settings() }
    suspend fun setPinned(packageName: String, enabled: Boolean, userSerial: Long = 0) = dao.savePinnedApp(PinnedApp(packageName, enabled, activeProfile(), userSerial))
    suspend fun isPinned(packageName: String, userSerial: Long = 0) = dao.isPinned(packageName, activeProfile(), userSerial) == true
    suspend fun saveSettings(settings: Settings) = dao.saveSettings(settings)
    suspend fun capture(fields: NotificationFields, ownPackage: String, now: Long): CapturedNotification? =
        notificationMutex.withLock { database.withTransaction {
            val previous = dao.notification(fields.sourceKey)
            val mapped = mapNotification(fields, ownPackage, isPinned(fields.packageName, fields.userSerial), now, previous)
                ?: return@withTransaction null
            mapped.channelId?.let { dao.saveChannel(SeenChannel(mapped.packageName, it)) }
            val options = options(mapped)
            val ready = if (options.digestOnly && mapped.state == NotificationState.ACTIVE_ORIGINAL && !isMuted(mapped))
                mapped.copy(digest = true, state = NotificationState.COPY_WAITING,
                    nextShowAt = nextDigestAt(now, parseDigestTimes(options.digestTimes)), copyId = (previous?.id ?: 0).takeIf { it > 0 }?.toInt())
            else mapped
            dao.saveNotification(ready)
            var saved = dao.notification(fields.sourceKey)!!
            if (saved.digest && saved.copyId == null) {
                saved = saved.copy(copyId = saved.id.toInt()); dao.saveNotification(saved)
            }
            if (previous == null || mapped.actionVersion != previous.actionVersion) {
                dao.saveEvent(NotificationEvent(notificationId = saved.id, packageName = saved.packageName,
                    userSerial = saved.userSerial, appLabel = saved.appLabel, title = saved.title, text = saved.text, capturedAt = now))
            } else dao.updateEvent(saved.id, saved.title, saved.text, saved.appLabel)
            saved
        } }

    suspend fun cleanup(now: Long) = notificationMutex.withLock {
        database.withTransaction {
            val cutoff = now - 30L * 24 * 60 * 60 * 1000
            dao.deleteOldEvents(cutoff)
            dao.deleteOldNotifications(cutoff)
        }
    }

    suspend fun reconcileActiveKeys(activeKeys: List<String>, now: Long) {
        // A missed removal has no dismissal reason, so it must not trigger a copy.
        dao.finishMissingOriginals(activeKeys, now)
    }
}
