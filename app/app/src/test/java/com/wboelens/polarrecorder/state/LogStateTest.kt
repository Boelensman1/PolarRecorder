package com.wboelens.polarrecorder.state

import app.cash.turbine.test
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for LogState - the application-scoped state holder for log messages. Tests cover log
 * entry creation, snackbar queue management, message limits, and StateFlow emissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogStateTest {

  private lateinit var logState: LogState

  @Before
  fun setup() {
    logState = LogState()
  }

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
  fun `addLogMessage with snackbar adds to snackbar queue`() {
    logState.addLogMessage("Test message", withSnackbar = true)
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val snackbarQueue = logState.snackbarMessagesQueue.value
    assertEquals(1, snackbarQueue.size)
    assertEquals("Test message", snackbarQueue[0].message)
  }

  @Test
  fun `addLogMessage without snackbar does not add to snackbar queue`() {
    logState.addLogMessage("Test message", withSnackbar = false)
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val snackbarQueue = logState.snackbarMessagesQueue.value
    assertEquals(0, snackbarQueue.size)
  }

  @Test
  fun `addLogError with default withSnackbar=true adds to snackbar queue`() {
    logState.addLogError("Error message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val snackbarQueue = logState.snackbarMessagesQueue.value
    assertEquals(1, snackbarQueue.size)
    assertEquals("Error message", snackbarQueue[0].message)
    assertEquals(LogType.ERROR, snackbarQueue[0].type)
  }

  @Test
  fun `addLogError with withSnackbar=false does not add to snackbar queue`() {
    logState.addLogError("Error message", withSnackbar = false)
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val snackbarQueue = logState.snackbarMessagesQueue.value
    assertEquals(0, snackbarQueue.size)
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
  fun `log entries are limited to MAX_LOG_MESSAGES (250)`() {
    for (i in 1..300) {
      logState.addLogMessage("Message $i")
    }
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals(250, logs.size)
  }

  @Test
  fun `oldest entries are removed when exceeding limit`() {
    for (i in 1..300) {
      logState.addLogMessage("Message $i")
    }
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val logs = logState.logMessages.value
    assertEquals("Message 51", logs.first().message)
    assertEquals("Message 300", logs.last().message)
  }

  @Test
  fun `popSnackbarMessage returns and removes first message`() {
    logState.addLogMessage("First", withSnackbar = true)
    logState.addLogMessage("Second", withSnackbar = true)
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    val popped = logState.popSnackbarMessage()

    assertNotNull(popped)
    assertEquals("First", popped!!.message)
    assertEquals(1, logState.snackbarMessagesQueue.value.size)
    assertEquals("Second", logState.snackbarMessagesQueue.value[0].message)
  }

  @Test
  fun `popSnackbarMessage returns null when queue is empty`() {
    val popped = logState.popSnackbarMessage()

    assertNull(popped)
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

  @Test
  fun `log entries contain timestamps`() {
    val beforeTime = System.currentTimeMillis()
    logState.addLogMessage("Test message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
    val afterTime = System.currentTimeMillis()

    val logs = logState.logMessages.value
    assertTrue(logs[0].timestamp >= beforeTime)
    assertTrue(logs[0].timestamp <= afterTime)
  }

  @Test
  fun `logMessages StateFlow emits updates`() = runTest {
    logState.logMessages.test {
      assertEquals(emptyList<LogEntry>(), awaitItem())

      logState.addLogMessage("New message")
      ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

      val updated = awaitItem()
      assertEquals(1, updated.size)
      assertEquals("New message", updated[0].message)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `snackbarMessagesQueue StateFlow emits updates`() = runTest {
    logState.snackbarMessagesQueue.test {
      assertEquals(emptyList<LogEntry>(), awaitItem())

      logState.addLogMessage("Snackbar message", withSnackbar = true)

      val updated = awaitItem()
      assertEquals(1, updated.size)
      assertEquals("Snackbar message", updated[0].message)

      cancelAndIgnoreRemainingEvents()
    }
  }
}
