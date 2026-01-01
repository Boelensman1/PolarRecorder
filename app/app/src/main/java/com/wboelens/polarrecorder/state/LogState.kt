package com.wboelens.polarrecorder.state

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(val message: String, val type: LogType, val timestamp: Long)

enum class LogType {
  SUCCESS,
  NORMAL,
  ERROR,
}

/**
 * Application-scoped state holder for log messages. This class survives Activity restarts and holds
 * the source of truth for log state. ViewModels delegate to this class.
 */
class LogState {
  companion object {
    private const val MAX_LOG_MESSAGES = 250
    private const val TAG = "LogState"
  }

  private val _logMessages = MutableStateFlow<List<LogEntry>>(emptyList())
  val logMessages: StateFlow<List<LogEntry>> = _logMessages.asStateFlow()

  private val logQueue = java.util.concurrent.ConcurrentLinkedQueue<LogEntry>()

  private val _snackbarMessagesQueue = MutableStateFlow<List<LogEntry>>(emptyList())
  val snackbarMessagesQueue: StateFlow<List<LogEntry>> = _snackbarMessagesQueue.asStateFlow()

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

  private fun flushQueue() {
    val newEntries = mutableListOf<LogEntry>()
    while (true) {
      val entry = logQueue.poll() ?: break
      newEntries.add(entry)
    }
    if (newEntries.isNotEmpty()) {
      val currentList = _logMessages.value
      val updatedList = (currentList + newEntries).takeLast(MAX_LOG_MESSAGES)
      _logMessages.value = updatedList
    }
  }

  fun clearLogs() {
    logQueue.clear()
    _logMessages.value = emptyList()
  }
}
