package com.wboelens.polarrecorder.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit test rule that sets the Main dispatcher to a test dispatcher for coroutine testing. This
 * allows tests to control coroutine execution and avoid issues with Dispatchers.Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherRule(val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    TestWatcher() {

  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
