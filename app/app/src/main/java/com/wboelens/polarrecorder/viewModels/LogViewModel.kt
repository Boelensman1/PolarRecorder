package com.wboelens.polarrecorder.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.wboelens.polarrecorder.repository.LogRepository
import kotlinx.coroutines.flow.StateFlow

data class LogEntry(val message: String, val type: LogType, val timestamp: Long)

enum class LogType {
  SUCCESS,
  NORMAL,
  ERROR,
}

class LogViewModel : ViewModel() {
  companion object {
    private const val TAG = "LogViewModel"
  }

  private lateinit var logRepository: LogRepository

  private lateinit var _logMessages: LiveData<List<LogEntry>>
  val logMessages: LiveData<List<LogEntry>>
    get() = _logMessages

  // Property to hold the snackbar messages queue, used to display snackbars with log messages
  private lateinit var _snackbarMessagesQueue: StateFlow<List<LogEntry>>

  val snackbarMessagesQueue: StateFlow<List<LogEntry>>
    get() = _snackbarMessagesQueue

  init {}

  fun setup(logRepository: LogRepository) {
    this.logRepository = logRepository

    _logMessages = logRepository.logMessages.asLiveData()
    _snackbarMessagesQueue = logRepository.snackbarMessagesQueue
  }

  // Method to pop the first snackbar message from the queue
  fun popSnackbarMessage(): LogEntry? {
    return logRepository.popSnackbarMessage()
  }

  fun addLogMessage(message: String, withSnackbar: Boolean = false) {
    Log.d(TAG, message)

    logRepository.addLogMessage(message, withSnackbar)
  }

  fun addLogError(message: String, withSnackbar: Boolean = true) {
    Log.e(TAG, message)
    logRepository.addLogError(message, withSnackbar)
  }

  fun addLogSuccess(message: String, withSnackbar: Boolean = false) {
    Log.d(TAG, message)
    logRepository.addLogSuccess(message, withSnackbar)
  }

  fun requestFlushQueue() {
    logRepository.requestFlushQueue()
  }

  fun clearLogs() {
    logRepository.clearLogs()
  }
}
