package com.udacity.project4.locationreminders.savereminder

import android.app.Application
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.R
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.locationreminders.MainCoroutineRule
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.getOrAwaitValue
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class SaveReminderViewModelTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var saveReminderViewModel: SaveReminderViewModel
    private lateinit var fakeDataSource: FakeDataSource
    private lateinit var application: Application

    // Set up ViewModel before test
    @Before
    fun setupViewModel() {
        stopKoin()
        fakeDataSource = FakeDataSource()
        application = ApplicationProvider.getApplicationContext()
        saveReminderViewModel = SaveReminderViewModel(application, fakeDataSource)
    }

    // Clean up Koin after test
    @After
    fun tearDown() {
        stopKoin()
    }

    // Test saving reminder flow
    @Test
    fun saveReminder_updatesLiveData_andNavigatesBack() = mainCoroutineRule.runTest {
        // GIVEN - A valid reminder
        val reminder = ReminderDataItem("Title", "Description", "Location", 1.0, 1.0, 10f)

        // WHEN - Saving the reminder
        saveReminderViewModel.saveReminder(reminder)

        // THEN - Verify loading state changes, toast shown, navigation occurs, and data saved
        assertThat(saveReminderViewModel.showLoading.getOrAwaitValue(), `is`(true))
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle() // Let coroutine finish
        assertThat(saveReminderViewModel.showLoading.getOrAwaitValue(), `is`(false))
        assertThat(saveReminderViewModel.showToast.getOrAwaitValue(), `is`("Reminder Saved !"))
        assertThat(
            saveReminderViewModel.navigationCommand.getOrAwaitValue(),
            `is`(NavigationCommand.Back)
        )

        val savedReminderResult = fakeDataSource.getReminder(reminder.id)
        assertThat(savedReminderResult is Result.Success, `is`(true))
        val savedReminder = (savedReminderResult as Result.Success).data
        assertThat(savedReminder.title, `is`(reminder.title))
        assertThat(savedReminder.description, `is`(reminder.description))
    }

    // Test validation with empty title
    @Test
    fun validateEnteredData_emptyTitle_returnsFalse_showsTitleError() {
        // GIVEN - Reminder with empty title but location set in VM
        saveReminderViewModel.setSelectedLocation(
            1.0,
            1.0
        )
        val reminder = ReminderDataItem("", "Description", "Location", 1.0, 1.0, 10f)

        // WHEN - Validating data
        val isValid = saveReminderViewModel.validateEnteredData(reminder)

        // THEN - Validation fails and title error snackbar is shown
        assertThat(isValid, `is`(false))
        assertThat(
            saveReminderViewModel.showSnackBarInt.getOrAwaitValue(),
            `is`(R.string.err_enter_title)
        )
    }

    // Test validation with null title
    @Test
    fun validateEnteredData_nullTitle_returnsFalse_showsTitleError() {
        // GIVEN - Reminder with null title but location set in VM
        saveReminderViewModel.setSelectedLocation(
            1.0,
            1.0
        )
        val reminder = ReminderDataItem(null, "Description", "Location", 1.0, 1.0, 10f)

        // WHEN - Validating data
        val isValid = saveReminderViewModel.validateEnteredData(reminder)

        // THEN - Validation fails and title error snackbar is shown
        assertThat(isValid, `is`(false))
        assertThat(
            saveReminderViewModel.showSnackBarInt.getOrAwaitValue(),
            `is`(R.string.err_enter_title)
        )
    }

    // Test validation with no location selected
    @Test
    fun validateEnteredData_nullLatLong_returnsFalse_showsLocationError() {
        // GIVEN - Reminder with title, but lat/lng are null in ViewModel (default state)
        val reminder = ReminderDataItem("Title", "Description", "Location", null, null, 10f)
        // DO NOT set location in ViewModel for this test

        // WHEN - Validating data
        val isValid = saveReminderViewModel.validateEnteredData(reminder)

        // THEN - Validation fails and location error snackbar is shown
        assertThat(isValid, `is`(false))
        assertThat(
            saveReminderViewModel.showSnackBarInt.getOrAwaitValue(),
            `is`(R.string.err_select_location)
        )
    }

    // Test validation with valid data
    @Test
    fun validateEnteredData_validData_returnsTrue() {
        // GIVEN - Reminder with valid title and location set in ViewModel
        val reminder =
            ReminderDataItem("Valid Title", "Valid Desc", "Valid Location", 1.0, 1.0, 10f)
        saveReminderViewModel.setSelectedLocation(1.0, 1.0) // Set location in ViewModel

        // WHEN - Validating data
        val isValid = saveReminderViewModel.validateEnteredData(reminder)

        // THEN - Validation passes
        assertThat(isValid, `is`(true))
    }

    // Test loading state during save
    @Test
    fun saveReminder_checkLoadingState() = mainCoroutineRule.runTest {
        // GIVEN - A reminder
        val reminder = ReminderDataItem("Title", "Description", "Location", 1.0, 1.0, 10f)

        // WHEN - Saving reminder
        saveReminderViewModel.saveReminder(reminder)

        // THEN - Loading state becomes true then false
        assertThat(saveReminderViewModel.showLoading.getOrAwaitValue(), `is`(true))
        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()
        assertThat(saveReminderViewModel.showLoading.getOrAwaitValue(), `is`(false))
    }
}