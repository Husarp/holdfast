package com.husarp.holdfast

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupStoreTest {
    @Test fun importReplacesRulesAndOptionalHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
        try {
            val d = db.dao()
            val repo = HoldfastRepository(db)
            repo.ensureProfiles()
            repo.setPinned("app", true)
            d.addRule(MuteRule(packageName = "app", kind = "Title", value = "old"))
            d.saveEvent(NotificationEvent(notificationId = 1, packageName = "app", appLabel = "App", title = "Private", text = "text", capturedAt = 1))
            val store = BackupStore(db)
            val original = BackupFormat.decode(store.export(true))
            d.addRule(MuteRule(packageName = "app", kind = "Text", value = "new"))
            repo.setPinned("other", true)
            store.replace(original)
            assertEquals(original.rules, d.rules())
            assertEquals(original.pinned, d.observePinnedApps().first())
            assertEquals("Private", d.observeHistory().first().single().title)
            store.replace(original.copy(history = null))
            assertEquals(1, d.observeHistory().first().size)
        } finally { db.close() }
    }
    @Test fun locationQuietResumesOutsideAndIsOffWhenDenied() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
        try {
            var allowed = true
            val repo = HoldfastRepository(db) { allowed }
            val place = QuietPlace(1, "Home", 50.0, 20.0, 200f, "app", true)
            val row = CapturedNotification(packageName = "app", appLabel = "App", sourceKey = "key", channelId = null,
                title = "", text = "", postedAt = 1, updatedAt = 1)
            db.dao().savePlace(place)
            assertNotNull(repo.quietUntil(row, 100))
            allowed = false
            assertNull(repo.quietUntil(row, 100))
            allowed = true
            db.dao().savePlace(place.copy(inside = false))
            assertNull(repo.quietUntil(row, 100))
            db.dao().savePlace(place)
            assertNull(repo.quietUntil(row.copy(isVip = true), 100))
        } finally { db.close() }
    }
}
