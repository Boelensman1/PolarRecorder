package com.wboelens.polarrecorder.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.wboelens.polarrecorder.state.LogEntry
import com.wboelens.polarrecorder.state.LogState
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel that delegates to an Application-scoped LogState. This allows the UI to observe log
 * state via LiveData while the actual state survives Activity restarts.
 */
class LogViewModel(private val logState: LogState) : ViewModel() {

  val logMessages: LiveData<List<LogEntry>> = logState.logMessages.asLiveData()

  val snackbarMessagesQueue: StateFlow<List<LogEntry>> = logState.snackbarMessagesQueue

  fun popSnackbarMessage(): LogEntry? = logState.popSnackbarMessage()

  fun addLogMessage(message: String, withSnackbar: Boolean = false) =
      logState.addLogMessage(message, withSnackbar)

  fun addLogError(message: String, withSnackbar: Boolean = true) =
      logState.addLogError(message, withSnackbar)

  fun addLogSuccess(message: String, withSnackbar: Boolean = false) =
      logState.addLogSuccess(message, withSnackbar)

  fun requestFlushQueue() = logState.requestFlushQueue()

  fun clearLogs() = logState.clearLogs()
}
