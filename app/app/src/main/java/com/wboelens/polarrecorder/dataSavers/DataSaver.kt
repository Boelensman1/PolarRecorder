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

  // Property to check if a saver is enabled
  @Suppress("VariableNaming") protected val _isEnabled = MutableStateFlow(false)
  val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

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
