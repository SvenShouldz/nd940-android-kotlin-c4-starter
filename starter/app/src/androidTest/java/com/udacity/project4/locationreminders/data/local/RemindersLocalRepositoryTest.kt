package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking // Use runBlocking for repository tests
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Medium Test to test the repository
@MediumTest
class RemindersLocalRepositoryTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: RemindersDatabase
    private lateinit var repository: RemindersLocalRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        repository = RemindersLocalRepository(database.reminderDao(), Dispatchers.Main)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun saveReminder_retrievesReminder() = runBlocking {
        // GIVEN - A new reminder saved in the repository.
        val newReminder = ReminderDTO("title", "description", "location", 1.0, 1.0, 10f)
        repository.saveReminder(newReminder)

        // WHEN - Reminder retrieved by ID.
        val result = repository.getReminder(newReminder.id)

        // THEN - Same reminder is returned.
        assertThat(result is Result.Success, `is`(true))
        result as Result.Success
        assertThat(result.data.id, `is`(newReminder.id))
        assertThat(result.data.title, `is`(newReminder.title))
        assertThat(result.data.description, `is`(newReminder.description))
        assertThat(result.data.location, `is`(newReminder.location))
        assertThat(result.data.latitude, `is`(newReminder.latitude))
        assertThat(result.data.longitude, `is`(newReminder.longitude))
        assertThat(result.data.geofence, `is`(newReminder.geofence))
    }

    @Test
    fun getReminders_retrievesSavedReminders() = runBlocking {
        // GIVEN - Save multiple reminders
        val reminder1 = ReminderDTO("t1", "d1", "l1", 1.0, 1.0, 1f)
        val reminder2 = ReminderDTO("t2", "d2", "l2", 2.0, 2.0, 2f)
        repository.saveReminder(reminder1)
        repository.saveReminder(reminder2)

        // WHEN - Get all reminders
        val result = repository.getReminders()

        // THEN - The result is success and contains the saved reminders
        assertThat(result is Result.Success, `is`(true))
        result as Result.Success
        assertThat(result.data.size, `is`(2))
        assertThat(result.data.any { it.id == reminder1.id }, `is`(true))
        assertThat(result.data.any { it.id == reminder2.id }, `is`(true))
    }

    @Test
    fun deleteAllReminders_emptyListRetrieved() = runBlocking {
        // GIVEN - Save a reminder
        val reminder = ReminderDTO("title", "description", "location", 1.0, 1.0, 10f)
        repository.saveReminder(reminder)

        // WHEN - Delete all reminders
        repository.deleteAllReminders()

        // THEN - Getting reminders returns success with an empty list
        val result = repository.getReminders()
        assertThat(result is Result.Success, `is`(true))
        result as Result.Success
        assertThat(result.data.isEmpty(), `is`(true))
    }

    @Test
    fun getReminder_nonExistentId_returnsErrorNotFound() = runBlocking {
        // GIVEN - An ID that does not exist in the repository
        val nonExistentId = "non_existent_id"

        // WHEN - Getting the reminder by the non-existent ID
        val result = repository.getReminder(nonExistentId)

        // THEN - The result is an error with the specific "not found" message
        assertThat(result is Result.Error, `is`(true))
        result as Result.Error
        assertThat(result.message, `is`("Reminder not found!"))
    }
}