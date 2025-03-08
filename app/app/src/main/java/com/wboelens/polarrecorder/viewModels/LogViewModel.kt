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
    ERROR
  }

  private val _logMessages = MutableLiveData<List<LogEntry>>(emptyList())
  val logMessages: LiveData<List<LogEntry>> = _logMessages

  private val logQueue = java.util.concurrent.ConcurrentLinkedQueue<LogEntry>()

  // Property to hold the error messages queue, used to display snackbars with the errors
  private val _errorMessagesQueue = MutableStateFlow<List<String>>(emptyList())
  val errorMessagesQueue: StateFlow<List<String>> = _errorMessagesQueue.asStateFlow()

  // Method to pop the first error message from the queue
  fun popErrorMessage(): String? {
    return if (_errorMessagesQueue.value.isNotEmpty()) {
      val firstMessage = _errorMessagesQueue.value.first()
      _errorMessagesQueue.value = _errorMessagesQueue.value.drop(1)
      firstMessage
    } else null
  }

  private fun add(message: String, type: LogType) {
    val entry = LogEntry(message, type, System.currentTimeMillis())
    logQueue.offer(entry)
    requestFlushQueue()
  }

  fun addLogMessage(message: String) {
    Log.d(TAG, message)
    this.add(message, LogType.NORMAL)
  }

  fun addLogError(message: String) {
    Log.e(TAG, message)
    this.add(message, LogType.ERROR)
    _errorMessagesQueue.value += message
  }

  fun addLogSuccess(message: String) {
    Log.d(TAG, message)
    this.add(message, LogType.SUCCESS)
  }

  fun requestFlushQueue() {
    Handler(Looper.getMainLooper()).post { flushQueue() }
  }

  // Merge all queued items into the LiveData once
  fun flushQueue() {
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
