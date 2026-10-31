package com.husarp.holdfast

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import com.husarp.holdfast.notifications.NotificationFields
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
    private val repo = HoldfastRepository(db)
    private val fields = NotificationFields("app", "App", "key", "channel", "Title", "Text", 100)
    @After fun close() = db.close()
    @Test fun countsCapturesButNotUpdatesOrCopiesAndCountsReusedKeys() = runBlocking {
        repo.setPinned("app", true)
        val row = repo.capture(fields, "own", 100)!!
        repo.capture(fields.copy(text = "update"), "own", 200)
        db.dao().saveNotification(row.copy(state = NotificationState.COPY_VISIBLE))
        assertEquals(1, db.dao().counts(0).single().count)
        db.dao().saveNotification(row.copy(state = NotificationState.DONE, updatedAt = 300))
        repo.capture(fields.copy(postedAt = 400), "own", 400)
        assertEquals(2, db.dao().counts(0).single().count)
        assertEquals(1, db.dao().counts(101).single().count)
        assertTrue(db.dao().counts(401).isEmpty())
    }
    @Test fun retentionSweepsOrphansAndKeepsLiveAppearance() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        val appearance = com.husarp.holdfast.notifications.OriginalAppearance(app)
        val repository = HoldfastRepository(db, appearance)
        repository.setPinned("app", true)
        val live = repository.capture(fields, "own", 1)!!
        val done = repository.capture(fields.copy(sourceKey = "done"), "own", 1)!!
        val notification = android.app.Notification.Builder(app, "source").setSmallIcon(R.drawable.ic_holdfast).build()
        appearance.save(live.copy(packageName = app.packageName), notification)
        appearance.save(done.copy(packageName = app.packageName), notification)
        db.dao().saveNotification(done.copy(state = NotificationState.DONE))
        java.io.File(app.filesDir, "appearance/unknown.png").writeText("orphan")
        repository.cleanup(31L * 86_400_000)
        assertNotNull(appearance.load(live.sourceKey))
        assertNull(appearance.load(done.sourceKey))
        assertFalse(java.io.File(app.filesDir, "appearance/unknown.png").exists())
        assertNull(db.dao().notification(done.sourceKey))
    }

    @Test fun cleanupPreservesActiveRowsAndExactCutoff() = runBlocking {
        repo.setPinned("app", true)
        val cutoff = 1000L
        NotificationState.entries.forEachIndexed { i, state ->
            val row = repo.capture(fields.copy(sourceKey = "$i"), "own", cutoff - 1)!!
            db.dao().saveNotification(row.copy(state = state))
        }
        val boundary = repo.capture(fields.copy(sourceKey = "boundary"), "own", cutoff)!!
        db.dao().saveNotification(boundary.copy(state = NotificationState.DONE))
        repo.cleanup(cutoff + 30L * 86_400_000)
        assertEquals(4, repo.history.first().size)
        assertEquals(4, db.dao().allNotifications().size)
    }
}
