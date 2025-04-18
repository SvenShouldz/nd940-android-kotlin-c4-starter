package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.udacity.project4.locationreminders.data.dto.ReminderDTO

import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Test

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Unit test the DAO
@SmallTest
class RemindersDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: RemindersDatabase

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        ).allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = database.close()

    @Test
    fun insertReminderAndGetById() = runBlockingTest {
        // GIVEN - Insert a reminder.
        val reminder = ReminderDTO("title", "description", "location", 10.0, 10.0, 10f)
        database.reminderDao().saveReminder(reminder)

        // WHEN - Get the reminder by id from the database.
        val loaded = database.reminderDao().getReminderById(reminder.id)

        // THEN - The loaded data contains the expected values.
        assertThat<ReminderDTO>(loaded as ReminderDTO, notNullValue())
        assertThat(loaded.id, `is`(reminder.id))
        assertThat(loaded.title, `is`(reminder.title))
        assertThat(loaded.description, `is`(reminder.description))
        assertThat(loaded.location, `is`(reminder.location))
        assertThat(loaded.latitude, `is`(reminder.latitude))
        assertThat(loaded.longitude, `is`(reminder.longitude))
        assertThat(loaded.geofence, `is`(reminder.geofence))
    }

    @Test
    fun insertRemindersAndGetReminders() = runBlockingTest {
        // GIVEN - Insert multiple reminders
        val reminder1 = ReminderDTO("t1", "d1", "l1", 1.0, 1.0, 1f)
        val reminder2 = ReminderDTO("t2", "d2", "l2", 2.0, 2.0, 2f)
        database.reminderDao().saveReminder(reminder1)
        database.reminderDao().saveReminder(reminder2)

        // WHEN - Get all reminders
        val reminders = database.reminderDao().getReminders()

        // THEN - The list contains the inserted reminders
        assertThat(reminders.size, `is`(2))
        assertThat(reminders.any { it.id == reminder1.id }, `is`(true))
        assertThat(reminders.any { it.id == reminder2.id }, `is`(true))
    }


    @Test
    fun saveReminder_replacesOnConflict() = runBlockingTest {
        // GIVEN - insert a reminder
        val originalReminder = ReminderDTO("original", "desc", "loc", 1.0, 1.0, 1f)
        database.reminderDao().saveReminder(originalReminder)

        // WHEN - insert a new reminder with the same ID
        val updatedReminder = ReminderDTO("updated", "desc_new", "loc_new", 2.0, 2.0, 2f, originalReminder.id)
        database.reminderDao().saveReminder(updatedReminder)

        // THEN - the loaded reminder should have the updated values
        val loaded = database.reminderDao().getReminderById(originalReminder.id)
        assertThat(loaded, notNullValue())
        assertThat(loaded?.title, `is`(updatedReminder.title))
        assertThat(loaded?.description, `is`(updatedReminder.description))
        assertThat(loaded?.location, `is`(updatedReminder.location))
        assertThat(loaded?.latitude, `is`(updatedReminder.latitude))
        assertThat(loaded?.longitude, `is`(updatedReminder.longitude))
        assertThat(loaded?.geofence, `is`(updatedReminder.geofence))
    }

    @Test
    fun deleteAllReminders_clearsDatabase() = runBlockingTest {
        // GIVEN - Insert reminders
        val reminder1 = ReminderDTO("t1", "d1", "l1", 1.0, 1.0, 1f)
        val reminder2 = ReminderDTO("t2", "d2", "l2", 2.0, 2.0, 2f)
        database.reminderDao().saveReminder(reminder1)
        database.reminderDao().saveReminder(reminder2)

        // WHEN - Delete all reminders
        database.reminderDao().deleteAllReminders()

        // THEN - Getting reminders returns an empty list
        val reminders = database.reminderDao().getReminders()
        assertThat(reminders.isEmpty(), `is`(true))
    }

    @Test
    fun getReminderById_notFound_returnsNull() = runBlockingTest {
        // GIVEN - Empty database or reminder doesn't exist
        val nonExistentId = "non_existent_id"

        // WHEN - Getting the reminder by id
        val loaded = database.reminderDao().getReminderById(nonExistentId)

        // THEN - The result is null
        assertThat(loaded, org.hamcrest.CoreMatchers.nullValue())
    }
}