package com.husarp.holdfast

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
    private val dao = database.dao()
    @After fun close() = database.close()

    @Test fun pinnedAppRoundTrip() = runBlocking {
        dao.savePinnedApp(PinnedApp("example.app"))
        assertEquals(listOf(PinnedApp("example.app")), dao.observePinnedApps().first())
    }

    @Test fun settingsRoundTrip() = runBlocking {
        assertEquals(Settings(), HoldfastRepository(database).settings.first())
        dao.saveSettings(Settings(returnDelayMinutes = 10, expireAfterMinutes = 60))
        assertEquals(Settings(returnDelayMinutes = 10, expireAfterMinutes = 60), dao.observeSettings().first())
    }

    @Test fun everyStateAndNullableFieldRoundTrips() = runBlocking {
        NotificationState.entries.forEach { state ->
            dao.saveNotification(CapturedNotification(packageName = "example.app", appLabel = "Example",
                sourceKey = state.name, channelId = "messages", title = "Title", text = "Full text",
                postedAt = 100, updatedAt = 200, state = state, nextShowAt = 300, expireAt = 400,
                copyId = 42, isGroupSummary = false))
            val saved = dao.notification(state.name)!!
            assertTrue(saved.id > 0)
            assertEquals(state, saved.state)
            assertEquals(300L, saved.nextShowAt)
            assertEquals(400L, saved.expireAt)
            assertEquals(42, saved.copyId)
            assertEquals("Full text", saved.text)
        }
        assertEquals(5, dao.observeNotifications().first().size)
    }

    @Test(expected = IllegalArgumentException::class) fun settingsHasOneRowIdentity() {
        Settings(id = 2)
    }
}
