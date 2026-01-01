package com.wboelens.polarrecorder.viewModels

import com.wboelens.polarrecorder.state.LogState
import com.wboelens.polarrecorder.state.LogType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Tests for LogState (the Application-scoped state holder). Since LogViewModel is now a thin
 * wrapper around LogState, we test the state directly using StateFlow.value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogViewModelTest {

  private lateinit var logState: LogState

  @Before
  fun setup() {
    logState = LogState()
  }

  @Test
  fun `addLogMessage adds normal log entry`() {
    // When
    logState.addLogMessage("Test message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Test message", logs[0].message)
    assertEquals(LogType.NORMAL, logs[0].type)
  }

  @Test
  fun `addLogError adds error log entry`() {
    // When
    logState.addLogError("Error message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Error message", logs[0].message)
    assertEquals(LogType.ERROR, logs[0].type)
  }

  @Test
  fun `addLogSuccess adds success log entry`() {
    // When
    logState.addLogSuccess("Success message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = logState.logMessages.value
    assertEquals(1, logs.size)
    assertEquals("Success message", logs[0].message)
    assertEquals(LogType.SUCCESS, logs[0].type)
  }

  @Test
  fun `clearLogs removes all log entries`() {
    // Given
    logState.addLogMessage("Message 1")
    logState.addLogError("Error 1")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
    assertEquals(2, logState.logMessages.value.size)

    // When
    logState.clearLogs()

    // Then
    assertEquals(0, logState.logMessages.value.size)
  }

  @Test
  fun `log entries are limited to MAX_LOG_MESSAGES`() {
    // Add more than MAX_LOG_MESSAGES entries
    for (i in 1..300) {
      logState.addLogMessage("Message $i")
    }
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = logState.logMessages.value
    assertEquals(250, logs.size) // MAX_LOG_MESSAGES is 250
    assertEquals("Message 51", logs.first().message) // First message should be 51
    assertEquals("Message 300", logs.last().message) // Last message should be 300
  }

  @Test
  fun `multiple log entries are batched together`() {
    // When
    logState.addLogMessage("Message 1")
    logState.addLogMessage("Message 2")
    logState.addLogMessage("Message 3")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = logState.logMessages.value
    assertEquals(3, logs.size)
    assertEquals("Message 1", logs[0].message)
    assertEquals("Message 2", logs[1].message)
    assertEquals("Message 3", logs[2].message)
  }
}
