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
