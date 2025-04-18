package com.udacity.project4.locationreminders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@ExperimentalCoroutinesApi
class MainCoroutineRule : TestWatcher() {

    // Create a StandardTestDispatcher.
    val testDispatcher = StandardTestDispatcher()
    // Create a TestScope using the StandardTestDispatcher.
    val testScope = TestScope(testDispatcher)


    override fun starting(description: Description) {
        super.starting(description)
        // Set the main dispatcher to the StandardTestDispatcher before the test runs.
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        // Reset the main dispatcher to the original one after the test finishes.
        Dispatchers.resetMain()
    }
}


@ExperimentalCoroutinesApi
fun MainCoroutineRule.runTest(block: suspend TestScope.() -> Unit) = testScope.runTest { block() }

