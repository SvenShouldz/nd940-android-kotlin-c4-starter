package com.udacity.project4.locationreminders.reminderslist

// Removed pause/resume imports
import android.app.Application
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.locationreminders.MainCoroutineRule
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.getOrAwaitValue
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

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@Config(sdk = [Build.VERSION_CODES.Q])
class RemindersListViewModelTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var remindersListViewModel: RemindersListViewModel
    private lateinit var fakeDataSource: FakeDataSource
    private lateinit var application: Application

    @Before
    fun setupViewModel() {
        stopKoin()
        fakeDataSource = FakeDataSource()
        application = ApplicationProvider.getApplicationContext()
        remindersListViewModel = RemindersListViewModel(application, fakeDataSource)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun loadReminders_populatesRemindersList_whenDataSourceIsNotEmpty() =
        mainCoroutineRule.runTest { // Use runTest
            val reminder1 = ReminderDTO("Title1", "Desc1", "Loc1", 1.0, 1.0, 10f)
            val reminder2 = ReminderDTO("Title2", "Desc2", "Loc2", 2.0, 2.0, 20f)
            fakeDataSource.saveReminder(reminder1)
            fakeDataSource.saveReminder(reminder2)

            remindersListViewModel.loadReminders()

            mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

            val loadedReminders = remindersListViewModel.remindersList.getOrAwaitValue()
            assertThat(loadedReminders.size, `is`(2))
            assertThat(remindersListViewModel.showNoData.getOrAwaitValue(), `is`(false))
        }

    @Test
    fun loadReminders_showsError_whenDataSourceReturnsError() =
        mainCoroutineRule.runTest {
            fakeDataSource.setReturnError(true)
            remindersListViewModel.loadReminders()

            mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(
                remindersListViewModel.showSnackBar.getOrAwaitValue(),
                `is`("Test exception: Could not retrieve reminders")
            )
            assertThat(remindersListViewModel.showNoData.getOrAwaitValue(), `is`(true))
        }

    @Test
    fun loadReminders_setsShowLoading_showsLoadingAndCompletes() =
        mainCoroutineRule.runTest { // Use runTest

            remindersListViewModel.loadReminders()

            assertThat(
                remindersListViewModel.showLoading.getOrAwaitValue(),
                `is`(true)
            )

            mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(remindersListViewModel.showLoading.getOrAwaitValue(), `is`(false))
        }

    @Test
    fun loadReminders_emptyDataSource_showsNoData() = mainCoroutineRule.runTest {
        remindersListViewModel.loadReminders()

        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(remindersListViewModel.showNoData.getOrAwaitValue(), `is`(true))
        assertThat(remindersListViewModel.remindersList.getOrAwaitValue().isEmpty(), `is`(true))
    }

    @Test
    fun check_loading() = mainCoroutineRule.runTest {
        remindersListViewModel.loadReminders()
        val initialLoading =
            remindersListViewModel.showLoading.getOrAwaitValue()
        assertThat(initialLoading, `is`(true))

        mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(remindersListViewModel.showLoading.getOrAwaitValue(), `is`(false))
    }

    @Test
    fun shouldReturnError_loadReminders_showSnackbarError() =
        mainCoroutineRule.runTest {
            fakeDataSource.setReturnError(true)
            remindersListViewModel.loadReminders()


            mainCoroutineRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(
                remindersListViewModel.showSnackBar.getOrAwaitValue(),
                `is`("Test exception: Could not retrieve reminders")
            )
        }
}