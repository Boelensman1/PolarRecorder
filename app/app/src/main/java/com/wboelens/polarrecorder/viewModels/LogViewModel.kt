package com.wboelens.polarrecorder.viewModels

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LogViewModel : ViewModel() {
  companion object {
    private const val MAX_LOG_MESSAGES = 250
    private const val TAG = "LogViewModel"
  }

  data class LogEntry(val message: String, val type: LogType, val timestamp: Long)

  enum class LogType {
    SUCCESS,
    NORMAL,
    ERROR,
  }

  private val _logMessages = MutableLiveData<List<LogEntry>>(emptyList())
  val logMessages: LiveData<List<LogEntry>> = _logMessages

  private val logQueue = java.util.concurrent.ConcurrentLinkedQueue<LogEntry>()

  // Property to hold the snackbar messages queue, used to display snackbars with log messages
  private val _snackbarMessagesQueue = MutableStateFlow<List<LogEntry>>(emptyList())
  val snackbarMessagesQueue: StateFlow<List<LogEntry>> = _snackbarMessagesQueue.asStateFlow()

  // Method to pop the first snackbar message from the queue
  fun popSnackbarMessage(): LogEntry? {
    return if (_snackbarMessagesQueue.value.isNotEmpty()) {
      val firstMessage = _snackbarMessagesQueue.value.first()
      _snackbarMessagesQueue.value = _snackbarMessagesQueue.value.drop(1)
      firstMessage
    } else null
  }

  private fun add(message: String, type: LogType, withSnackbar: Boolean) {
    val entry = LogEntry(message, type, System.currentTimeMillis())
    logQueue.offer(entry)
    requestFlushQueue()
    if (withSnackbar) {
      _snackbarMessagesQueue.value += entry
    }
  }

  fun addLogMessage(message: String, withSnackbar: Boolean = false) {
    Log.d(TAG, message)
    this.add(message, LogType.NORMAL, withSnackbar)
  }

  fun addLogError(message: String, withSnackbar: Boolean = true) {
    Log.e(TAG, message)
    this.add(message, LogType.ERROR, withSnackbar)
  }

  fun addLogSuccess(message: String, withSnackbar: Boolean = false) {
    Log.d(TAG, message)
    this.add(message, LogType.SUCCESS, withSnackbar)
  }

  fun requestFlushQueue() {
    Handler(Looper.getMainLooper()).post { flushQueue() }
  }

  // Merge all queued items into the LiveData once
  private fun flushQueue() {
    // Drain everything currently in the queue
    val newEntries = mutableListOf<LogEntry>()
    while (true) {
      val entry = logQueue.poll() ?: break
      newEntries.add(entry)
    }
    if (newEntries.isNotEmpty()) {
      val currentList = _logMessages.value.orEmpty()
      val updatedList = (currentList + newEntries).takeLast(MAX_LOG_MESSAGES)
      _logMessages.value = updatedList
    }
  }

  fun clearLogs() {
    logQueue.clear()
    _logMessages.value = emptyList()
  }
}
