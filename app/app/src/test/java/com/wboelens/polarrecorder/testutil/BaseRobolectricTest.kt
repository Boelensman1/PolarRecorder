package com.wboelens.polarrecorder.testutil

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base test class for Robolectric tests that provides common test infrastructure:
 * - Robolectric test runner for Android framework simulation
 * - InstantTaskExecutorRule for synchronous LiveData execution
 * - TestDispatcherRule for coroutine testing with controlled dispatchers
 *
 * Note: Uses JUnit 4 annotations for Robolectric compatibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
abstract class BaseRobolectricTest {

  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

  @get:Rule val testDispatcherRule = TestDispatcherRule()
}
