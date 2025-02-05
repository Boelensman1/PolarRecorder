package com.wboelens.polarrecorder.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

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

  private fun add(message: String, type: LogType) {
    val entry = LogEntry(message, type, System.currentTimeMillis())

    // use postValue as this function can be called from outside the main thread
    _logMessages.postValue((_logMessages.value.orEmpty() + entry).takeLast(MAX_LOG_MESSAGES))
  }

  fun addLogMessage(message: String) {
    Log.d(TAG, message)
    this.add(message, LogType.NORMAL)
  }

  fun addLogError(message: String) {
    Log.e(TAG, message)
    this.add(message, LogType.ERROR)
  }

  fun addLogSuccess(message: String) {
    Log.e(TAG, message)
    this.add(message, LogType.SUCCESS)
  }

  fun clearLogs() {
    _logMessages.value = emptyList()
  }
}
