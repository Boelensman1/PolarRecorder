package com.wboelens.polarrecorder.viewModels

import com.wboelens.polarrecorder.state.LogState
import com.wboelens.polarrecorder.testutil.BaseRobolectricTest
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowLooper

/**
 * Tests for LogViewModel - verifies ViewModel delegation to LogState. Note: LogState behavior tests
 * are in LogStateTest - this only tests ViewModel-specific functionality.
 */
class LogViewModelTest : BaseRobolectricTest() {

  private lateinit var logState: LogState
  private lateinit var viewModel: LogViewModel

  @Before
  fun setup() {
    logState = LogState()
    viewModel = LogViewModel(logState)
  }

  @Test
  fun `viewModel snackbarMessagesQueue exposes state flow`() {
    viewModel.addLogError("Error with snackbar")

    val queue = viewModel.snackbarMessagesQueue.value

    assertEquals(1, queue.size)
    assertEquals("Error with snackbar", queue[0].message)
  }

  @Test
  fun `viewModel addLogMessage delegates to LogState`() {
    viewModel.addLogMessage("Delegated message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Delegated message", logs[0].message)
  }

  @Test
  fun `viewModel popSnackbarMessage returns and removes first message`() {
    viewModel.addLogError("First error")
    viewModel.addLogError("Second error")

    val popped = viewModel.popSnackbarMessage()

    assertNotNull(popped)
    assertEquals("First error", popped!!.message)
    assertEquals(1, logState.snackbarMessagesQueue.value.size)
  }

  @Test
  fun `viewModel popSnackbarMessage returns null when empty`() {
    val popped = viewModel.popSnackbarMessage()

    assertNull(popped)
  }
}
