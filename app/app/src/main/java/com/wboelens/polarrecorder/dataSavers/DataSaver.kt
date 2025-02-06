package com.wboelens.polarrecorder.dataSavers

import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.viewModels.LogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class DataSaver(
    protected val logViewModel: LogViewModel,
    protected val preferencesManager: PreferencesManager
) {
  // Track first message status
  var firstMessageSaved = mutableMapOf<String, Boolean>()

  // Property to check if a saver is configured
  abstract val isConfigured: Boolean

  // Property to hold the error messages queue
  private val _errorMessagesQueue = MutableStateFlow<List<String>>(emptyList())
  val errorMessagesQueue: StateFlow<List<String>> = _errorMessagesQueue.asStateFlow()

  // Property to check if a saver is enabled
  @Suppress("VariableNaming") protected val _isEnabled = MutableStateFlow(false)
  val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

  // Method to add an error message to the queue
  protected fun addErrorMessage(errorMessage: String) {
    logViewModel.addLogError("Broker URL must be configured before starting")
    _errorMessagesQueue.value = _errorMessagesQueue.value + errorMessage
  }

  // Method to pop the first error message from the queue
  fun popErrorMessage(): String? {
    return if (_errorMessagesQueue.value.isNotEmpty()) {
      val firstMessage = _errorMessagesQueue.value.first()
      _errorMessagesQueue.value = _errorMessagesQueue.value.drop(1)
      firstMessage
    } else null
  }

  // Abstract methods that must be implemented by children
  abstract fun enable()

  abstract fun disable()

  // Method to handle incoming data
  abstract fun saveData(
      phoneTimestamp: Long,
      deviceId: String,
      recordingName: String,
      dataType: String,
      payload: String
  )

  // Initialise if needed
  open fun initSaving(
      recordingName: String,
      deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>
  ) {
    firstMessageSaved.clear()
    for ((deviceId, info) in deviceIdsWithInfo) {
      for (dataType in info.dataTypes) {
        firstMessageSaved["$deviceId/$dataType"] = false
      }
    }
  }

  // Cleanup resources (if needed) when recording is stopped
  open fun stopSaving() {}

  // Cleanup resources (if needed) when app is closed
  open fun cleanup() {}
}
