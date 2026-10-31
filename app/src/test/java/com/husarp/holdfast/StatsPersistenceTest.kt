package com.husarp.holdfast

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsPersistenceTest {
    @Test fun versionTwoUpgradePreservesPlacesAndBackfillsHeldCounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val name = "stats-migration.db"
        var db = Room.databaseBuilder(context, HoldfastDatabase::class.java, name).build()
        val dao = db.dao()
        dao.saveNotification(CapturedNotification(id = 1, packageName = "app", appLabel = "App", sourceKey = "key", channelId = null, title = "", text = "", postedAt = 1, updatedAt = 1, firstCopyAt = 2))
        dao.saveEvent(NotificationEvent(notificationId = 1, packageName = "app", appLabel = "App", title = "", text = "", capturedAt = 1))
        dao.savePlace(QuietPlace(name = "Home", latitude = 1.0, longitude = 2.0, radius = 100f, packages = "app"))
        db.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { old ->
            old.execSQL("ALTER TABLE NotificationEvent DROP COLUMN heldAsCopy")
            old.execSQL("ALTER TABLE QuietPlace DROP COLUMN profileId")
            old.execSQL("DROP TABLE room_master_table")
            old.version = 2
        }
        db = Room.databaseBuilder(context, HoldfastDatabase::class.java, name).addMigrations(MIGRATION_2_3).build()
        try {
            assertTrue(db.dao().observeHistory().first().single().heldAsCopy)
            assertEquals(1L, db.dao().observePlaces().first().single().profileId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun heldCountSurvivesFinishingAndRepostingDoesNotInflateIt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<HoldfastApplication>(), HoldfastDatabase::class.java).build()
        try {
            val dao = db.dao()
            val row = CapturedNotification(id = 1, packageName = "app", appLabel = "App", sourceKey = "key", channelId = null, title = "", text = "", postedAt = 1, updatedAt = 1)
            dao.saveNotification(row)
            dao.saveEvent(NotificationEvent(notificationId = 1, packageName = "app", appLabel = "App", title = "", text = "", capturedAt = 1))
            dao.saveNotification(row.copy(firstCopyAt = 2, state = NotificationState.COPY_VISIBLE))
            dao.saveNotification(row.copy(firstCopyAt = 2, state = NotificationState.COPY_VISIBLE))
            dao.saveNotification(row.copy(state = NotificationState.DONE))
            assertTrue(dao.observeHistory().first().single().heldAsCopy)
            val range = QuietSchedule(packageName = "app", days = "1,2", startMinute = 900, endMinute = 960)
            dao.addQuiet(range)
            dao.replaceQuietDay(1, "app", 1, listOf(range, range.copy(startMinute = 1200, endMinute = 1260)))
            assertEquals(2, dao.quiet(1, "app").count { it.days == "1" })
            assertEquals(1, dao.quiet(1, "app").count { it.days == "2" })
            dao.replaceQuietDay(1, "app", 1, emptyList())
            assertEquals("2", dao.quiet(1, "app").single().days)
            dao.savePlace(QuietPlace(name = "Home", latitude = 1.0, longitude = 2.0, radius = 100f, packages = "app", profileId = 2))
            assertEquals(2L, dao.observePlaces().first().single().profileId)
        } finally { db.close() }
    }
}
