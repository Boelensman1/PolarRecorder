package com.wboelens.polarrecorder.viewModels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogViewModelTest {

  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

  private lateinit var viewModel: LogViewModel

  @Before
  fun setup() {
    viewModel = LogViewModel()
  }

  @Test
  fun `addLogMessage adds normal log entry`() {
    // When
    viewModel.addLogMessage("Test message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = viewModel.logMessages.value
    assertEquals(1, logs?.size)
    assertEquals("Test message", logs?.get(0)?.message)
    assertEquals(LogViewModel.LogType.NORMAL, logs?.get(0)?.type)
  }

  @Test
  fun `addLogError adds error log entry`() {
    // When
    viewModel.addLogError("Error message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = viewModel.logMessages.value
    assertEquals(1, logs?.size)
    assertEquals("Error message", logs?.get(0)?.message)
    assertEquals(LogViewModel.LogType.ERROR, logs?.get(0)?.type)
  }

  @Test
  fun `addLogSuccess adds success log entry`() {
    // When
    viewModel.addLogSuccess("Success message")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = viewModel.logMessages.value
    assertEquals(1, logs?.size)
    assertEquals("Success message", logs?.get(0)?.message)
    assertEquals(LogViewModel.LogType.SUCCESS, logs?.get(0)?.type)
  }

  @Test
  fun `clearLogs removes all log entries`() {
    // Given
    viewModel.addLogMessage("Message 1")
    viewModel.addLogError("Error 1")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
    assertEquals(2, viewModel.logMessages.value?.size)

    // When
    viewModel.clearLogs()

    // Then
    assertEquals(0, viewModel.logMessages.value?.size)
  }

  @Test
  fun `log entries are limited to MAX_LOG_MESSAGES`() {
    // Add more than MAX_LOG_MESSAGES entries
    for (i in 1..300) {
      viewModel.addLogMessage("Message $i")
    }
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = viewModel.logMessages.value
    assertEquals(250, logs?.size) // MAX_LOG_MESSAGES is 250
    assertEquals("Message 51", logs?.first()?.message) // First message should be 51
    assertEquals("Message 300", logs?.last()?.message) // Last message should be 300
  }

  @Test
  fun `multiple log entries are batched together`() {
    // When
    viewModel.addLogMessage("Message 1")
    viewModel.addLogMessage("Message 2")
    viewModel.addLogMessage("Message 3")
    ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)

    // Then
    val logs = viewModel.logMessages.value
    assertEquals(3, logs?.size)
    assertEquals("Message 1", logs?.get(0)?.message)
    assertEquals("Message 2", logs?.get(1)?.message)
    assertEquals("Message 3", logs?.get(2)?.message)
  }
}
