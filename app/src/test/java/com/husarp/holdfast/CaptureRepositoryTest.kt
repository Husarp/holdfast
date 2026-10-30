package com.husarp.holdfast

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import com.husarp.holdfast.notifications.NotificationFields
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
    private val repository = HoldfastRepository(database)
    private val fields = NotificationFields("example.app", "Example", "key", "messages", "Title", "Text", 100)
    private suspend fun capture(input: NotificationFields = fields, now: Long = 200) = repository.capture(input, "com.husarp.holdfast", now)
    @After fun close() = database.close()

    @Test fun captureRequiresEnabledSelection() = runBlocking {
        assertNull(capture())
        repository.setPinned(fields.packageName, true)
        assertNotNull(capture())
        repository.setPinned(fields.packageName, false)
        assertNull(capture(fields.copy(sourceKey = "another")))
        assertEquals(1, repository.notifications.first().size)
    }
    @Test fun sameSourceKeyUpdatesOneRow() = runBlocking {
        repository.setPinned(fields.packageName, true)
        val first = capture()!!
        val updated = capture(fields.copy(title = "Updated", text = "Expanded", postedAt = 250), 300)!!
        assertEquals(first.id, updated.id)
        assertEquals(100L, updated.postedAt)
        assertEquals(300L, updated.updatedAt)
        assertEquals("Updated", updated.title)
        assertEquals("Expanded", updated.text)
        assertEquals(1, repository.notifications.first().size)
    }
    @Test fun differentSourceKeysStayIndependent() = runBlocking {
        repository.setPinned(fields.packageName, true)
        assertNotEquals(capture()!!.id, capture(fields.copy(sourceKey = "other"))!!.id)
        assertEquals(2, repository.notifications.first().size)
    }
    @Test fun concurrentUpdatesCannotDuplicateSourceKey() = runBlocking {
        repository.setPinned(fields.packageName, true)
        coroutineScope { repeat(10) { launch(Dispatchers.Default) { capture(fields.copy(text = "Text $it")) } } }
        assertEquals(1, repository.notifications.first().size)
    }
    @Test fun reconnectCapturesActiveWithoutResurrectingDone() = runBlocking {
        repository.setPinned(fields.packageName, true)
        val initial = capture()!!
        database.dao().saveNotification(initial.copy(state = NotificationState.DONE))
        assertEquals(NotificationState.DONE, capture(fields.copy(text = "Updated"))!!.state)
        capture(fields.copy(sourceKey = "new"))
        repository.reconcileActiveKeys(listOf("key", "new"), 400)
        assertEquals(NotificationState.ACTIVE_ORIGINAL, database.dao().notification("new")!!.state)
        assertEquals(NotificationState.DONE, database.dao().notification("key")!!.state)
    }
    @Test fun reconnectClosesMissingOriginalsButPreservesCopiesAndExpiry() = runBlocking {
        repository.setPinned(fields.packageName, true)
        NotificationState.entries.forEach { state ->
            val saved = capture(fields.copy(sourceKey = state.name))!!
            database.dao().saveNotification(saved.copy(state = state, expireAt = 900, nextShowAt = 700))
        }
        repository.reconcileActiveKeys(emptyList(), 500)
        NotificationState.entries.forEach { state ->
            val saved = database.dao().notification(state.name)!!
            assertEquals(if (state == NotificationState.ACTIVE_ORIGINAL) NotificationState.DONE else state, saved.state)
            assertEquals(900L, saved.expireAt)
            if (state == NotificationState.ACTIVE_ORIGINAL) assertNull(saved.nextShowAt)
            else assertEquals(700L, saved.nextShowAt)
        }
    }
    @Test fun reconnectKeepsPresentOriginalAndClosesOnlyMissingOne() = runBlocking {
        repository.setPinned(fields.packageName, true)
        capture()
        capture(fields.copy(sourceKey = "missing"))
        repository.reconcileActiveKeys(listOf("key"), 500)
        assertEquals(NotificationState.ACTIVE_ORIGINAL, database.dao().notification("key")!!.state)
        assertEquals(NotificationState.DONE, database.dao().notification("missing")!!.state)
    }
}
