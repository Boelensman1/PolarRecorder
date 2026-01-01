package com.wboelens.polarrecorder.viewModels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.wboelens.polarrecorder.state.LogState
import com.wboelens.polarrecorder.state.LogType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Tests for LogViewModel and LogState. Tests cover both the application-scoped LogState and the
 * ViewModel delegation to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogViewModelTest {

  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

  private lateinit var logState: LogState
  private lateinit var viewModel: LogViewModel

  @Before
  fun setup() {
    logState = LogState()
    viewModel = LogViewModel(logState)
  }

  // ==================== LogState Tests ====================

  @Test
  fun `addLogMessage adds normal log entry`() {
    logState.addLogMessage("Test message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Test message", logs[0].message)
    assertEquals(LogType.NORMAL, logs[0].type)
  }

  @Test
  fun `addLogError adds error log entry`() {
    logState.addLogError("Error message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Error message", logs[0].message)
    assertEquals(LogType.ERROR, logs[0].type)
  }

  @Test
  fun `addLogSuccess adds success log entry`() {
    logState.addLogSuccess("Success message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Success message", logs[0].message)
    assertEquals(LogType.SUCCESS, logs[0].type)
  }

  @Test
  fun `clearLogs removes all log entries`() {
    logState.addLogMessage("Message 1")
    logState.addLogError("Error 1")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
    assertEquals(2, logState.logMessages.value.size)

    logState.clearLogs()

    assertEquals(0, logState.logMessages.value.size)
  }

  @Test
  fun `log entries are limited to MAX_LOG_MESSAGES`() {
    for (i in 1..300) {
      logState.addLogMessage("Message $i")
    }
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(250, logs.size)
    assertEquals("Message 51", logs.first().message)
    assertEquals("Message 300", logs.last().message)
  }

  @Test
  fun `multiple log entries are batched together`() {
    logState.addLogMessage("Message 1")
    logState.addLogMessage("Message 2")
    logState.addLogMessage("Message 3")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(3, logs.size)
    assertEquals("Message 1", logs[0].message)
    assertEquals("Message 2", logs[1].message)
    assertEquals("Message 3", logs[2].message)
  }

  // ==================== LogViewModel Tests ====================

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
  fun `viewModel addLogError delegates with default snackbar true`() {
    viewModel.addLogError("Error message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val snackbarQueue = logState.snackbarMessagesQueue.value
    assertEquals(1, snackbarQueue.size)
    assertEquals(LogType.ERROR, snackbarQueue[0].type)
  }

  @Test
  fun `viewModel addLogSuccess delegates to LogState`() {
    viewModel.addLogSuccess("Success message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals(LogType.SUCCESS, logs[0].type)
  }

  @Test
  fun `viewModel clearLogs delegates to LogState`() {
    viewModel.addLogMessage("Message to clear")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
    assertEquals(1, logState.logMessages.value.size)

    viewModel.clearLogs()

    assertEquals(0, logState.logMessages.value.size)
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
