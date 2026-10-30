package com.husarp.holdfast.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldfastDao {
    @Query("SELECT * FROM QuietPlace") fun observePlaces(): Flow<List<QuietPlace>>
    @Upsert suspend fun savePlace(place: QuietPlace)
    @Query("DELETE FROM QuietPlace WHERE id = :id") suspend fun removePlace(id: Long)

@Query("DELETE FROM PinnedApp") suspend fun clearPinned()
@Query("DELETE FROM MuteRule") suspend fun clearRules()
@Query("DELETE FROM QuietSchedule") suspend fun clearQuiet()
@Query("DELETE FROM AppOptions") suspend fun clearOptions()
@Query("DELETE FROM Profile") suspend fun clearProfiles()
@Query("DELETE FROM NotificationEvent") suspend fun clearEvents()

    @Insert suspend fun saveOpen(open: CopyOpen)
    @Query("SELECT COUNT(*) FROM CopyOpen WHERE packageName = :pkg AND openedAt >= :since") suspend fun opens(pkg: String, since: Long): Int
    @Query("SELECT * FROM SpamDecision") fun observeSpam(): Flow<List<SpamDecision>>
    @Upsert suspend fun saveSpam(decision: SpamDecision)

    @Query("SELECT * FROM AppOptions") fun observeOptions(): Flow<List<AppOptions>>
    @Query("SELECT * FROM AppOptions WHERE packageName = :packageName AND profileId = :profileId")
    suspend fun options(packageName: String, profileId: Long): AppOptions?
    @Upsert suspend fun saveOptions(options: AppOptions)

    @Query("SELECT * FROM Profile ORDER BY id") fun observeProfiles(): Flow<List<Profile>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProfile(profile: Profile)
    @Upsert suspend fun saveProfile(profile: Profile)
    @Query("SELECT * FROM QuietSchedule") fun observeQuiet(): Flow<List<QuietSchedule>>
    @Query("SELECT * FROM QuietSchedule WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun quiet(profileId: Long, packageName: String): List<QuietSchedule>
    @Insert suspend fun addQuiet(schedule: QuietSchedule)
    @Query("DELETE FROM QuietSchedule WHERE id = :id") suspend fun removeQuiet(id: Long)

    @Query("SELECT * FROM MuteRule ORDER BY packageName, id")
    fun observeRules(): Flow<List<MuteRule>>
    @Query("SELECT * FROM MuteRule") suspend fun rules(): List<MuteRule>
    @Insert suspend fun addRule(rule: MuteRule)
    @Query("DELETE FROM MuteRule WHERE id = :id") suspend fun removeRule(id: Long)
    @Upsert suspend fun saveChannel(channel: SeenChannel)
    @Query("SELECT * FROM SeenChannel ORDER BY packageName, channelId")
    fun observeChannels(): Flow<List<SeenChannel>>
    @Query("UPDATE NotificationEvent SET title = :title, text = :text, appLabel = :label WHERE id = (SELECT MAX(id) FROM NotificationEvent WHERE notificationId = :notificationId)")
    suspend fun updateEvent(notificationId: Long, title: String, text: String, label: String)
    @Insert suspend fun saveEvent(event: NotificationEvent)
    @Query("SELECT * FROM NotificationEvent ORDER BY capturedAt DESC, id DESC")
    fun observeHistory(): Flow<List<NotificationEvent>>
    @Query("SELECT packageName, MAX(appLabel) AS appLabel, COUNT(*) AS count FROM NotificationEvent WHERE capturedAt >= :since GROUP BY packageName ORDER BY count DESC, packageName")
    suspend fun counts(since: Long): List<AppCount>
    @Query("DELETE FROM NotificationEvent WHERE capturedAt < :cutoff AND notificationId NOT IN (SELECT id FROM CapturedNotification WHERE state IN ('ACTIVE_ORIGINAL', 'COPY_VISIBLE', 'COPY_WAITING'))")
    suspend fun deleteOldEvents(cutoff: Long)
    @Query("DELETE FROM CapturedNotification WHERE updatedAt < :cutoff AND state IN ('DONE', 'EXPIRED')")
    suspend fun deleteOldNotifications(cutoff: Long)
    @Query("SELECT * FROM PinnedApp ORDER BY packageName")
    fun observePinnedApps(): Flow<List<PinnedApp>>
    @Upsert suspend fun savePinnedApp(app: PinnedApp)
    @Query("SELECT enabled FROM PinnedApp WHERE packageName = :packageName AND profileId = :profileId")
    suspend fun isPinned(packageName: String, profileId: Long = 1): Boolean?
    @Query("SELECT * FROM CapturedNotification ORDER BY postedAt DESC")
    fun observeNotifications(): Flow<List<CapturedNotification>>
    @Query("SELECT * FROM CapturedNotification WHERE sourceKey = :sourceKey")
    suspend fun notification(sourceKey: String): CapturedNotification?
    @Query("UPDATE CapturedNotification SET state = 'DONE', nextShowAt = NULL, updatedAt = :now WHERE state = 'ACTIVE_ORIGINAL' AND sourceKey NOT IN (:activeKeys)")
    suspend fun finishMissingOriginals(activeKeys: List<String>, now: Long)
    @Query("SELECT * FROM CapturedNotification WHERE id = :id")
    suspend fun notificationById(id: Long): CapturedNotification?
    @Query("SELECT * FROM CapturedNotification")
    suspend fun allNotifications(): List<CapturedNotification>
    @Upsert suspend fun saveNotification(notification: CapturedNotification)
    @Query("SELECT * FROM Settings WHERE id = 1")
    fun observeSettings(): Flow<Settings?>
    @Upsert suspend fun saveSettings(settings: Settings)
}

@Database(entities = [QuietPlace::class, CopyOpen::class, SpamDecision::class, PinnedApp::class, CapturedNotification::class, Settings::class, NotificationEvent::class, MuteRule::class, SeenChannel::class, Profile::class, QuietSchedule::class, AppOptions::class], version = 1, exportSchema = false)
abstract class HoldfastDatabase : RoomDatabase() {
    abstract fun dao(): HoldfastDao
}
