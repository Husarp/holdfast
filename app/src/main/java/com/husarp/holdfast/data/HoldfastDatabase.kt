package com.husarp.holdfast.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldfastDao {
    @Query("DELETE FROM TypePolicy") suspend fun clearTypes()
    @Query("SELECT * FROM TypePolicy ORDER BY id") fun observeTypes(): Flow<List<TypePolicy>>
    @Query("SELECT * FROM TypePolicy ORDER BY id") suspend fun types(): List<TypePolicy>
    @Upsert suspend fun saveType(policy: TypePolicy)
    @Query("DELETE FROM TypePolicy WHERE id = :id") suspend fun removeType(id: Long)

    @Query("SELECT * FROM QuietPlace") fun observePlaces(): Flow<List<QuietPlace>>
    @Upsert suspend fun savePlace(place: QuietPlace)
    @Query("DELETE FROM QuietPlace WHERE id = :id") suspend fun removePlace(id: Long)

@Query("DELETE FROM PinnedApp") suspend fun clearPinned()
@Query("DELETE FROM MuteRule") suspend fun clearRules()
@Query("DELETE FROM QuietSchedule") suspend fun clearQuiet()
@Query("DELETE FROM AppOptions") suspend fun clearOptions()
@Query("DELETE FROM Profile") suspend fun clearProfiles()
@Query("DELETE FROM NotificationEvent") suspend fun clearEvents()

    @Query("SELECT * FROM CopyOpen") fun observeOpens(): Flow<List<CopyOpen>>
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
    @androidx.room.Transaction
    suspend fun replaceQuietDay(profileId: Long, packageName: String, day: Int, ranges: List<QuietSchedule>) {
        require(day in 1..7)
        quiet(profileId, packageName).filter { day.toString() in it.days.split(",") }.forEach { old ->
            removeQuiet(old.id)
            val remaining = old.days.split(",").filter { it != day.toString() }
            if (remaining.isNotEmpty()) addQuiet(old.copy(id = 0, days = remaining.joinToString(",")))
        }
        ranges.forEach { addQuiet(it.copy(id = 0, profileId = profileId, packageName = packageName, days = "$day")) }
    }
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
    @Query("SELECT packageName, userSerial, MAX(appLabel) AS appLabel, COUNT(*) AS count FROM NotificationEvent WHERE capturedAt >= :since GROUP BY packageName, userSerial ORDER BY count DESC, packageName")
    suspend fun counts(since: Long): List<AppCount>
    @Query("DELETE FROM NotificationEvent WHERE capturedAt < :cutoff AND notificationId NOT IN (SELECT id FROM CapturedNotification WHERE state IN ('ACTIVE_ORIGINAL', 'COPY_VISIBLE', 'COPY_WAITING'))")
    suspend fun deleteOldEvents(cutoff: Long)
    @Query("DELETE FROM CapturedNotification WHERE updatedAt < :cutoff AND state IN ('DONE', 'EXPIRED')")
    suspend fun deleteOldNotifications(cutoff: Long)
    @Query("SELECT * FROM PinnedApp ORDER BY packageName")
    fun observePinnedApps(): Flow<List<PinnedApp>>
    @Upsert suspend fun savePinnedApp(app: PinnedApp)
    @Query("SELECT enabled FROM PinnedApp WHERE packageName = :packageName AND profileId = :profileId AND userSerial = :userSerial")
    suspend fun isPinned(packageName: String, profileId: Long = 1, userSerial: Long = 0): Boolean?
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
    @Upsert suspend fun upsertNotification(notification: CapturedNotification)
    @Query("UPDATE NotificationEvent SET heldAsCopy = 1 WHERE id = (SELECT MAX(id) FROM NotificationEvent WHERE notificationId = :id)") suspend fun markHeld(id: Long)
    @androidx.room.Transaction
    suspend fun saveNotification(notification: CapturedNotification) {
        upsertNotification(notification)
        if (notification.firstCopyAt != null) markHeld(notification.id)
    }
    @Query("SELECT * FROM Settings WHERE id = 1")
    fun observeSettings(): Flow<Settings?>
    @Upsert suspend fun saveSettings(settings: Settings)
}

@Database(entities = [TypePolicy::class, QuietPlace::class, CopyOpen::class, SpamDecision::class, PinnedApp::class, CapturedNotification::class, Settings::class, NotificationEvent::class, MuteRule::class, SeenChannel::class, Profile::class, QuietSchedule::class, AppOptions::class], version = 3, exportSchema = false)
abstract class HoldfastDatabase : RoomDatabase() {
    abstract fun dao(): HoldfastDao
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Settings ADD COLUMN holdEverything INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SeenChannel ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE SeenChannel SET name = channelId")
        db.execSQL("ALTER TABLE AppOptions ADD COLUMN mode TEXT")
        db.execSQL("UPDATE AppOptions SET mode = 'Bring back when I swipe it'")
        db.execSQL("ALTER TABLE AppOptions ADD COLUMN openAndFinish INTEGER")
        db.execSQL("ALTER TABLE AppOptions ADD COLUMN buttons TEXT NOT NULL DEFAULT 'snooze:60|dismiss|reply'")
        db.execSQL("ALTER TABLE AppOptions ADD COLUMN mutedUntil INTEGER NOT NULL DEFAULT 0")
        db.execSQL("INSERT OR IGNORE INTO AppOptions (packageName, profileId, mode, buttons, mutedUntil, vipSenders, returnDelayMinutes, overrideExpiry, expireAfterMinutes, repeatMinutes, digestOnly, digestTimes) SELECT packageName, profileId, 'Bring back when I swipe it', 'snooze:60|dismiss|reply', 0, '', NULL, 0, NULL, NULL, 0, '09:00,18:00' FROM PinnedApp")
        db.execSQL("CREATE TABLE IF NOT EXISTS TypePolicy (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageName TEXT NOT NULL, profileId INTEGER NOT NULL, kind TEXT NOT NULL, value TEXT NOT NULL, mode TEXT, openAndFinish INTEGER, buttons TEXT)")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE NotificationEvent ADD COLUMN heldAsCopy INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE NotificationEvent SET heldAsCopy = 1 WHERE notificationId IN (SELECT id FROM CapturedNotification WHERE firstCopyAt IS NOT NULL)")
        db.execSQL("ALTER TABLE QuietPlace ADD COLUMN profileId INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE QuietPlace SET profileId = COALESCE((SELECT activeProfileId FROM Settings WHERE id = 1), 1)")
    }
}
