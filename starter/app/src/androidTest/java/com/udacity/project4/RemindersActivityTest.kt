package com.udacity.project4

import android.Manifest
import android.app.Application
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.Root
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import com.udacity.project4.locationreminders.RemindersActivity
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.util.DataBindingIdlingResource
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
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

@RunWith(AndroidJUnit4::class)
@LargeTest
class RemindersActivityTest : KoinTest {

    private val dataSource: ReminderDataSource by inject()
    private lateinit var appContext: Application
    private val dataBindingIdlingResource = DataBindingIdlingResource()

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(RemindersActivity::class.java)

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    // Registers Idling Resource & handles initial permissions
    @Before
    fun setupAndHandleInitialPermissions() {
        IdlingRegistry.getInstance().register(dataBindingIdlingResource)
        activityScenarioRule.scenario.onActivity { activity ->
            dataBindingIdlingResource.activity = activity
        }
        handlePermissionDialogs()
    }

    // Clears Koin, sets up dependencies, clears data
    @Before
    fun initDependenciesAndClearData() {
        stopKoin()
        appContext = getApplicationContext()
        val myModule = module {
            viewModel { RemindersListViewModel(appContext, get() as ReminderDataSource) }
            single { SaveReminderViewModel(appContext, get() as ReminderDataSource) }
            single<ReminderDataSource> { RemindersLocalRepository(get()) }
            single { LocalDB.createRemindersDao(appContext) }
        }
        startKoin { modules(listOf(myModule)) }
        runBlocking { dataSource.deleteAllReminders() }
    }

    // Unregisters Idling Resource
    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(dataBindingIdlingResource)
    }

    // Uses UI Automator to dismiss permission dialogs
    private fun handlePermissionDialogs(timeoutMillis: Long = 3000L) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            var dialogHandled = false
            try {
                // Verify these texts against your API 29 device
                val btnTextsToTry = listOf(
                    "While using the app",
                    "Allow only while using the app",
                    "Allow all the time",
                    "ALLOW",
                    "Allow"
                )
                for (btnText in btnTextsToTry) {
                    val button = device.findObject(UiSelector().text(btnText))
                    if (button.exists() && button.isEnabled) {
                        button.click()
                        Log.d("PermissionHandler", "Clicked button with text: $btnText")
                        dialogHandled = true
                        Thread.sleep(1000)
                        break
                    }
                    val buttonContains = device.findObject(UiSelector().textContains(btnText))
                    if (!dialogHandled && buttonContains.exists() && buttonContains.isEnabled) {
                        buttonContains.click()
                        Log.d("PermissionHandler", "Clicked button containing text: $btnText")
                        dialogHandled = true
                        Thread.sleep(1000)
                        break
                    }
                }
            } catch (e: UiObjectNotFoundException) {
                Log.d("PermissionHandler", "Dialog element not found, continuing check...")
            }
            if (!dialogHandled) {
                Thread.sleep(500)
            } else {
                // Keep checking if multiple dialogs might appear
            }
        }
        Log.d("PermissionHandler", "Finished checking for permission dialogs.")
    }

    @Test
    fun addReminder_happyPath_savesAndDisplaysReminder() {
        runBlocking {
            onView(withId(R.id.addReminderFAB)).perform(click())

            val title = "E2E Test Title"
            val description = "E2E Test Description"
            onView(withId(R.id.reminderTitleInput)).perform(typeText(title))
            onView(withId(R.id.reminderDescriptionInput)).perform(
                typeText(description),
                closeSoftKeyboard()
            )

            onView(withId(R.id.selectLocation)).perform(click())

            waitForView(withId(R.id.map_fragment), timeoutMillis = 3000L)
            onView(withId(R.id.map_fragment)).perform(click())

            onView(withId(R.id.confirm_button)).perform(click())

            onView(withId(R.id.saveReminder)).perform(click())

            waitForView(withText(title), timeoutMillis = 2000L)
            onView(withText(R.string.reminder_saved)).inRoot(ToastMatcher())
                .check(matches(isDisplayed()))

            onView(withText(title)).check(matches(isDisplayed()))
            onView(withText(description)).check(matches(isDisplayed()))
        }
    }

    // Tests saving with empty title
    @Test
    fun addReminder_emptyTitle_showsSnackbarError() {
        onView(withId(R.id.addReminderFAB)).perform(click())
        onView(withId(R.id.reminderDescriptionInput)).perform(
            typeText("Only Desc"),
            closeSoftKeyboard()
        )
        onView(withId(R.id.saveReminder)).perform(click())

        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.err_enter_title)))
            .check(matches(isDisplayed()))
    }

    // Tests saving with empty location
    @Test
    fun addReminder_emptyLocation_showsSnackbarError() {
        onView(withId(R.id.addReminderFAB)).perform(click())
        onView(withId(R.id.reminderTitleInput)).perform(typeText("Title Only"), closeSoftKeyboard())
        onView(withId(R.id.reminderDescriptionInput)).perform(
            typeText("Desc Only"),
            closeSoftKeyboard()
        )
        onView(withId(R.id.saveReminder)).perform(click())

        // Check ViewModel logic if this still fails
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.err_select_location)))
            .check(matches(isDisplayed()))
    }

    // Utility function to wait for a view to be displayed
    fun waitForView(
        viewMatcher: Matcher<View>,
        timeoutMillis: Long = 5000L,
        checkIntervalMillis: Long = 100L
    ): ViewInteraction {
        val endTime = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < endTime) {
            try {
                val interaction = onView(viewMatcher)
                interaction.check(matches(isDisplayed()))
                return interaction // View found and displayed
            } catch (e: NoMatchingViewException) {
                Thread.sleep(checkIntervalMillis) // View not found yet
            } catch (e: AssertionFailedError) {
                Thread.sleep(checkIntervalMillis) // View found but not displayed yet
            } catch (e: Exception) {
                Thread.sleep(checkIntervalMillis) // Catch other potential Espresso errors during check
            }
        }
        // Timeout reached, attempt final check to throw descriptive error
        return onView(viewMatcher).check(matches(isDisplayed()))
    }
}

// Custom Matcher for Toast messages
class ToastMatcher : TypeSafeMatcher<Root>() {
    override fun describeTo(description: Description?) {
        description?.appendText("is toast")
    }

    override fun matchesSafely(item: Root?): Boolean {
        val type: Int? = item?.windowLayoutParams?.get()?.type
        if (type == WindowManager.LayoutParams.TYPE_TOAST || type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            val windowToken: IBinder = item.decorView.windowToken
            val appToken: IBinder = item.decorView.applicationWindowToken
            if (windowToken === appToken && item.decorView.visibility == View.VISIBLE) {
                return true
            }
        }
        return false
    }
}