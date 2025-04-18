package com.udacity.project4.locationreminders.reminderslist

import android.app.Application
import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
//UI Testing
@MediumTest
class ReminderListFragmentTest : KoinTest {

    private val dataSource: ReminderDataSource by inject()
    private lateinit var appContext: Application

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun init() {
        stopKoin()
        appContext = getApplicationContext()
        val myModule = module {
            viewModel {
                RemindersListViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single<ReminderDataSource> { FakeAndroidDataSource() }
        }

        startKoin {
            modules(listOf(myModule))
        }

        // Clear data before each test
        runBlockingTest {
            dataSource.deleteAllReminders()
        }
    }

    @Test
    fun clickAddReminderFAB_navigatesToSaveReminderFragment() {
        // GIVEN - On the home screen
        val scenario = launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)
        val navController = mock(NavController::class.java)
        scenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // WHEN - Click on the "+" button
        onView(withId(R.id.addReminderFAB)).perform(click())

        // THEN - Verify that we navigate to the add screen
        verify(navController).navigate(
            ReminderListFragmentDirections.toSaveReminder()
        )
    }

    @Test
    fun remindersList_DisplayedInUi() = runBlockingTest {
        // GIVEN - Add a reminder to the data source
        val reminder = ReminderDTO(
            "UI Test Title",
            "UI Test Desc",
            "UI Test Loc",
            1.0,
            1.0,
            10f,
            "id1"
        ) // Added ID
        dataSource.saveReminder(reminder)

        // WHEN - ReminderListFragment launches
        launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)

        // THEN - The reminder is displayed
        onView(withText(reminder.title)).check(matches(isDisplayed()))
        onView(withText(reminder.description)).check(matches(isDisplayed()))
        onView(withText(reminder.location)).check(matches(isDisplayed()))
        onView(withId(R.id.noDataTextView)).check(matches(withEffectiveVisibility(Visibility.GONE))) // Check no data is hidden
    }

    @Test
    fun remindersList_noReminders_showsNoData() {
        // GIVEN - Data source is empty (cleared in @Before)

        // WHEN - ReminderListFragment launches
        launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)

        // THEN - No data TextView is displayed
        onView(withId(R.id.noDataTextView)).check(matches(isDisplayed()))
        onView(withText(R.string.no_data)).check(matches(isDisplayed()))
    }

    @Test
    fun loadRemindersError_showsSnackbar() = runBlockingTest {
        // GIVEN - Datasource is set to return error
        (dataSource as FakeAndroidDataSource).setReturnError(true) // Cast to set error

        // WHEN - Fragment is launched
        launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)

        // THEN - A snackbar with the error message is shown
        // Check if the noData view is visible (which happens on error too)
        onView(withId(R.id.noDataTextView)).check(matches(isDisplayed()))
        // Check for snackbar text
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText("Instrumented Test Error"))) // Match the error from FakeAndroidDataSource
            .check(matches(isDisplayed()))
    }
}

// Definition for FakeAndroidDataSource (keep as provided before)
class FakeAndroidDataSource(var reminders: MutableList<ReminderDTO> = mutableListOf()) :
    ReminderDataSource {
    private var shouldReturnError = false

    fun setReturnError(value: Boolean) {
        shouldReturnError = value
    }

    override suspend fun getReminders(): com.udacity.project4.locationreminders.data.dto.Result<List<ReminderDTO>> {
        if (shouldReturnError) return com.udacity.project4.locationreminders.data.dto.Result.Error("Instrumented Test Error")
        return com.udacity.project4.locationreminders.data.dto.Result.Success(reminders.toList()) // Return a copy
    }

    override suspend fun saveReminder(reminder: ReminderDTO) {
        // Ensure no duplicate IDs if test logic depends on uniqueness
        reminders.removeAll { it.id == reminder.id }
        reminders.add(reminder)
    }

    override suspend fun getReminder(id: String): com.udacity.project4.locationreminders.data.dto.Result<ReminderDTO> {
        if (shouldReturnError) return com.udacity.project4.locationreminders.data.dto.Result.Error("Instrumented Test Error")
        return reminders.find { it.id == id }
            ?.let { com.udacity.project4.locationreminders.data.dto.Result.Success(it) }
            ?: com.udacity.project4.locationreminders.data.dto.Result.Error("Reminder not found!")
    }

    override suspend fun deleteAllReminders() {
        reminders.clear()
    }
}