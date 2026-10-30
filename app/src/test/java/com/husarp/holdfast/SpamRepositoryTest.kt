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
class SpamRepositoryTest {
    @Test fun weeklyCountsExcludeOldCapturesAndKeepIsRemembered() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
        try {
            val repo = HoldfastRepository(db)
            val dao = db.dao()
            val now = 10 * 86_400_000L
            repeat(20) { index ->
                dao.saveEvent(NotificationEvent(notificationId = index.toLong(), packageName = "app", appLabel = "App", title = "", text = "", capturedAt = now))
                dao.saveEvent(NotificationEvent(notificationId = index.toLong(), packageName = "old", appLabel = "Old", title = "", text = "", capturedAt = 1))
                dao.saveEvent(NotificationEvent(notificationId = index.toLong(), packageName = "opened", appLabel = "Opened", title = "", text = "", capturedAt = now))
            }
            dao.saveOpen(CopyOpen(packageName = "opened", openedAt = now))
            repo.checkSpam(now)
            val hint = repo.spam.first().single()
            assertEquals("app", hint.packageName)
            repo.setPinned("app", true)
            repo.answerSpam(hint, false)
            repo.checkSpam(now + 86_400_000)
            assertTrue(repo.spam.first().single().answered)
            assertTrue(repo.isPinned("app"))
            repo.answerSpam(hint, true)
            assertFalse(repo.isPinned("app"))
        } finally { db.close() }
    }
}
