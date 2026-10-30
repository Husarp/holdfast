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
class SelectionRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoldfastDatabase::class.java).build()
    private val repository = HoldfastRepository(database)
    @After fun close() = database.close()

    @Test fun unknownPackageIsNotSelected() = runBlocking {
        assertFalse(repository.isPinned("missing.app"))
    }
    @Test fun selectionCanBeEnabledAndDisabled() = runBlocking {
        repository.setPinned("example.app", true)
        assertTrue(repository.isPinned("example.app"))
        repository.setPinned("example.app", false)
        assertFalse(repository.isPinned("example.app"))
        assertEquals(listOf(PinnedApp("example.app", false)), repository.pinnedApps.first())
    }
    @Test fun repeatedSelectionDoesNotDuplicateRows() = runBlocking {
        repeat(3) { repository.setPinned("example.app", true) }
        assertEquals(1, repository.pinnedApps.first().size)
    }
    @Test fun packagesAreIndependentAndVisibleToNewRepository() = runBlocking {
        repository.setPinned("one.app", true)
        repository.setPinned("two.app", true)
        repository.setPinned("one.app", false)
        val reopened = HoldfastRepository(database)
        assertFalse(reopened.isPinned("one.app"))
        assertTrue(reopened.isPinned("two.app"))
        assertEquals(2, reopened.pinnedApps.first().size)
    }
}
