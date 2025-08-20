package com.wboelens.polarrecorder.dataSavers

import com.google.gson.Gson
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InitializationState {
  NOT_STARTED,
  SUCCESS,
  FAILED
}

abstract class DataSaver(
  protected val logRepository: LogRepository,
  protected val preferencesManager: PreferencesManager
) {
  // Track first message status
  var firstMessageSaved = mutableMapOf<String, Boolean>()

  // Property to check if a saver is configured
  abstract val isConfigured: Boolean

  // Property to check if a saver is enabled
  @Suppress("VariableNaming")
  protected val _isEnabled = MutableStateFlow(false)
  val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

  protected val _isInitialized = MutableStateFlow(InitializationState.NOT_STARTED)
  val isInitialized: StateFlow<InitializationState> = _isInitialized

  private val gson = Gson()

  // Abstract methods that must be implemented by children
  abstract fun enable()

  abstract fun disable()

  // Method to handle incoming data
  abstract fun saveData(
    phoneTimestamp: Long,
    deviceId: String,
    recordingName: String,
    dataType: String,
    data: Any
  )

  open fun createJSONPayload(
    phoneTimestamp: Long,
    deviceId: String,
    recordingName: String,
    dataType: String,
    data: Any
  ): String {
    return gson.toJson(
        mapOf(
            "phoneTimestamp" to phoneTimestamp,
            "deviceId" to deviceId,
            "recordingName" to recordingName,
            "dataType" to dataType,
            "data" to data,
        ),
    )
  }

  // Initialise if needed
  open fun initSaving(
    recordingName: String,
    deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>
  ) {
    // Reset initialization state when starting a new initialization
    _isInitialized.value = InitializationState.NOT_STARTED
    firstMessageSaved.clear()
    for ((deviceId, info) in deviceIdsWithInfo) {
      for (dataType in info.dataTypes) {
        firstMessageSaved["$deviceId/$dataType"] = false
      }
    }
  }

  // Cleanup resources (if needed) when recording is stopped
  open fun stopSaving() {
    _isInitialized.value = InitializationState.NOT_STARTED
  }

  // Cleanup resources (if needed) when app is closed
  open fun cleanup() {}
}
