package com.husarp.holdfast

import android.database.sqlite.SQLiteDatabase
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
class VersionTwoMigrationTest {
    @Test fun upgradePreservesPinsHistoryAndTimingAndSeedsExistingSwipeMode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val name = "migration-test.db"
        var db = Room.databaseBuilder(context, HoldfastDatabase::class.java, name).build()
        db.dao().savePinnedApp(PinnedApp("existing"))
        db.dao().saveEvent(NotificationEvent(notificationId = 1, packageName = "existing", appLabel = "Existing", title = "Saved", text = "Private", capturedAt = 1))
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
            old.execSQL("DROP TABLE Settings")
            old.execSQL("CREATE TABLE Settings (id INTEGER NOT NULL PRIMARY KEY, activeProfileId INTEGER NOT NULL, pausedUntil INTEGER, returnDelayMinutes INTEGER NOT NULL, expireAfterMinutes INTEGER)")
            old.execSQL("INSERT INTO Settings VALUES (1, 1, NULL, 15, NULL)")
            old.execSQL("DROP TABLE AppOptions")
            old.execSQL("CREATE TABLE AppOptions (packageName TEXT NOT NULL, profileId INTEGER NOT NULL, vipSenders TEXT NOT NULL, returnDelayMinutes INTEGER, overrideExpiry INTEGER NOT NULL, expireAfterMinutes INTEGER, repeatMinutes INTEGER, digestOnly INTEGER NOT NULL, digestTimes TEXT NOT NULL, PRIMARY KEY(packageName, profileId))")
            old.execSQL("DROP TABLE SeenChannel")
            old.execSQL("CREATE TABLE SeenChannel (packageName TEXT NOT NULL, channelId TEXT NOT NULL, PRIMARY KEY(packageName, channelId))")
            old.execSQL("INSERT INTO SeenChannel VALUES ('existing', 'due')")
            old.execSQL("DROP TABLE TypePolicy")
            old.execSQL("DROP TABLE room_master_table")
            old.execSQL("ALTER TABLE NotificationEvent DROP COLUMN heldAsCopy")
            old.execSQL("ALTER TABLE QuietPlace DROP COLUMN profileId")
            old.version = 1
        }
        db = Room.databaseBuilder(context, HoldfastDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            assertEquals(15, db.dao().observeSettings().first()!!.returnDelayMinutes)
            assertEquals("Private", db.dao().observeHistory().first().single().text)
            assertTrue(db.dao().isPinned("existing") == true)
            assertEquals(CopyMode.SWIPE, db.dao().options("existing", 1)!!.mode)
            assertEquals("due", db.dao().observeChannels().first().single().name)
            assertTrue(db.dao().types().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
