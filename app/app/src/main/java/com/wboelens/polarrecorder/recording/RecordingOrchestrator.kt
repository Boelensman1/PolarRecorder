package com.wboelens.polarrecorder.recording

import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.PolarTemperatureData
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.getDataFragment
import com.wboelens.polarrecorder.services.RecordingState
import com.wboelens.polarrecorder.state.Device
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.state.LogEntry
import com.wboelens.polarrecorder.state.LogState
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrates recording operations, extracted from RecordingService for testability. This class
 * contains all the business logic for managing recordings, while RecordingService handles Android
 * lifecycle and notification concerns.
 */
@Suppress("TooManyFunctions")
class RecordingOrchestrator(
    private val polarManager: PolarManager,
    private val deviceState: DeviceState,
    private val logState: LogState,
    private val preferencesManager: PreferencesManager,
    private val dataSavers: DataSavers,
    private val clock: Clock = SystemClock(),
    private val schedulerProvider: SchedulerProvider = RxSchedulerProvider(),
    private val appInfoProvider: AppInfoProvider? = null,
) {
  companion object {
    private const val RETRY_COUNT = 3L
  }

  // Recording state (exposed as read-only)
  private val _recordingState = MutableStateFlow(RecordingState())
  val recordingState: StateFlow<RecordingState> = _recordingState

  private val _lastData =
      MutableStateFlow<Map<String, Map<PolarDeviceDataType, Float?>>>(emptyMap())
  val lastData: StateFlow<Map<String, Map<PolarDeviceDataType, Float?>>> = _lastData

  private val _lastDataTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
  val lastDataTimestamps: StateFlow<Map<String, Long>> = _lastDataTimestamps

  // RxJava disposables for streams
  private val disposables = mutableMapOf<String, MutableMap<String, Disposable>>()
  private val messagesLock = Any()
  private var lastSavedLogSize = 0

  /** Starts recording for the selected devices. */
  @Suppress("ReturnCount")
  fun startRecording(recordingName: String): StartRecordingResult {
    if (recordingName.isEmpty()) {
      logState.addLogError("Recording name cannot be the empty string")
      return StartRecordingResult.EmptyRecordingName("Recording name cannot be the empty string")
    }

    if (_recordingState.value.isRecording) {
      logState.addLogError("Recording already in progress")
      return StartRecordingResult.AlreadyRecording("Recording already in progress")
    }

    val selectedDevices = deviceState.selectedDevices.value
    if (selectedDevices.isEmpty()) {
      logState.addLogError("Cannot start recording: No devices selected")
      return StartRecordingResult.NoDevicesSelected("Cannot start recording: No devices selected")
    }

    val connectedDevices = deviceState.connectedDevices.value
    val connectedDeviceIds = connectedDevices.map { it.info.deviceId }
    val disconnectedDevices =
        selectedDevices.filter { !connectedDeviceIds.contains(it.info.deviceId) }
    if (disconnectedDevices.isNotEmpty()) {
      val disconnectedNames = disconnectedDevices.map { it.info.name }.joinToString(", ")
      logState.addLogError(
          "Cannot start recording: Some selected devices are not connected: $disconnectedNames")
      return StartRecordingResult.DevicesNotConnected(disconnectedNames)
    }

    // Check if datasavers are initialized
    val enabledDataSavers = dataSavers.asList().filter { it.isEnabled.value }
    if (enabledDataSavers.isEmpty()) {
      logState.addLogError("Cannot start recording: No data savers are enabled")
      return StartRecordingResult.NoDataSaversEnabled(
          "Cannot start recording: No data savers are enabled")
    }

    val uninitializedSavers =
        enabledDataSavers.filter { it.isInitialized.value != InitializationState.SUCCESS }
    if (uninitializedSavers.isNotEmpty()) {
      logState.addLogError(
          "Cannot start recording: Data savers are not initialized. " +
              "Please go through the initialization process first.")
      return StartRecordingResult.DataSaversNotInitialized(
          "Cannot start recording: Data savers are not initialized. " +
              "Please go through the initialization process first.")
    }

    // Clear last data and last data timestamps when starting new recording
    _lastData.value =
        selectedDevices.associate { device ->
          device.info.deviceId to device.dataTypes.associateWith { null }
        }
    _lastDataTimestamps.value = emptyMap()

    // Log app version information
    logDeviceAndAppInfo()

    logState.addLogSuccess(
        "Recording $recordingName started, saving to ${dataSavers.enabledCount} data saver(s)",
    )

    // Update state
    val startTime = clock.currentTimeMillis()
    _recordingState.value =
        RecordingState(
            isRecording = true,
            currentRecordingName = recordingName,
            recordingStartTime = startTime,
        )

    // Start streams
    selectedDevices.forEach { device -> startStreamsForDevice(device) }

    return StartRecordingResult.Success
  }

  /** Stops the current recording session. */
  fun stopRecording() {
    if (!_recordingState.value.isRecording) {
      logState.addLogError("Trying to stop recording while no recording in progress")
      return
    }

    logState.addLogMessage("Recording stopped")

    // Flush the log queue synchronously to ensure the "Recording stopped" message
    // is in the StateFlow before we save
    logState.flushQueueSync()

    // Save any remaining log messages
    saveUnsavedLogMessages(logState.logMessages.value)

    // Dispose all streams
    disposeAllStreams()

    // Tell dataSavers to stop saving
    dataSavers.asList().filter { it.isEnabled.value }.forEach { saver -> saver.stopSaving() }

    // Update state
    _recordingState.value = RecordingState(isRecording = false)

    // Clear timestamps
    _lastDataTimestamps.value = emptyMap()
  }

  /**
   * Called when connected devices change. Handles stream cleanup for disconnected devices and
   * restarts streams for reconnected devices.
   */
  @Suppress("NestedBlockDepth")
  fun handleDevicesChanged(devices: List<Device>): Boolean {
    if (!_recordingState.value.isRecording) {
      return false
    }

    if (devices.isEmpty() && preferencesManager.recordingStopOnDisconnect) {
      logState.addLogError("No devices connected, stopping recording")
      stopRecording()
      return true // Indicates recording was stopped
    } else {
      val selectedDevices = deviceState.selectedDevices.value
      val connectedDeviceIds = devices.map { it.info.deviceId }

      // Process devices that were selected but are no longer connected
      selectedDevices.forEach { selectedDevice ->
        if (!connectedDeviceIds.contains(selectedDevice.info.deviceId)) {
          // Clean up by disposing all active streams for this device
          disposables[selectedDevice.info.deviceId]?.forEach { (_, disposable) ->
            disposable.dispose()
          }
          // Remove the device from our tracking map to prevent memory leaks
          disposables.remove(selectedDevice.info.deviceId)
        }
      }

      // Handle devices that have reconnected
      devices.forEach { device ->
        // Check if this device has no active streams (it was disconnected previously)
        if (disposables[device.info.deviceId]?.isEmpty() != false) {
          // Restart data streams for this device
          startStreamsForDevice(device)
        }
      }
    }
    return false // Recording continues
  }

  /**
   * Called when new log messages are added. Persists unsaved log entries to enabled data savers.
   */
  fun handleLogMessagesChanged(messages: List<LogEntry>) {
    if (messages.isNotEmpty() && messages.size > lastSavedLogSize) {
      saveUnsavedLogMessages(messages)
    }
  }

  /** Cleanup resources (call on service destroy). */
  fun cleanup() {
    disposeAllStreams()
  }

  private fun startStreamsForDevice(device: Device) {
    val deviceId = device.info.deviceId

    disposables[deviceId] = mutableMapOf()
    disposables[deviceId]?.let { deviceDisposables ->
      val selectedDataTypes = deviceState.getDeviceDataTypes(deviceId)
      selectedDataTypes.forEach { dataType ->
        deviceDisposables[dataType.name.lowercase()] = startStreamForDevice(deviceId, dataType)
      }
    }
  }

  private fun startStreamForDevice(
      deviceId: String,
      dataType: PolarDeviceDataType,
  ): Disposable {
    val selectedSensorSettings = deviceState.getDeviceSensorSettingsForDataType(deviceId, dataType)
    val currentRecordingName = _recordingState.value.currentRecordingName

    return polarManager
        .startStreaming(deviceId, dataType, selectedSensorSettings)
        .subscribeOn(schedulerProvider.io())
        .observeOn(schedulerProvider.computation())
        .retry(RETRY_COUNT)
        .doOnSubscribe { logState.addLogMessage("Starting $dataType stream for $deviceId") }
        .doOnError { error ->
          logState.addLogError("Stream error for $deviceId - $dataType: ${error.message}")
        }
        .doOnComplete {
          logState.addLogError("Stream completed unexpectedly for $deviceId - $dataType")
        }
        .subscribe(
            { data ->
              val phoneTimestamp = clock.currentTimeMillis()

              // Update last data timestamp for this device
              _lastDataTimestamps.value += (deviceId to phoneTimestamp)

              // Update last data for this device
              _lastData.value =
                  _lastData.value.toMutableMap().apply {
                    val deviceData = this[deviceId]?.toMutableMap() ?: mutableMapOf()
                    deviceData[dataType] = getDataFragment(dataType, data)
                    this[deviceId] = deviceData
                  }

              val batchData =
                  when (dataType) {
                    PolarDeviceDataType.HR -> (data as PolarHrData).samples
                    PolarDeviceDataType.PPI -> (data as PolarPpiData).samples
                    PolarDeviceDataType.ACC -> (data as PolarAccelerometerData).samples
                    PolarDeviceDataType.PPG -> (data as PolarPpgData).samples
                    PolarDeviceDataType.ECG -> (data as PolarEcgData).samples
                    PolarDeviceDataType.GYRO -> (data as PolarGyroData).samples
                    PolarDeviceDataType.TEMPERATURE -> (data as PolarTemperatureData).samples
                    PolarDeviceDataType.SKIN_TEMPERATURE -> (data as PolarTemperatureData).samples
                    PolarDeviceDataType.MAGNETOMETER -> (data as PolarMagnetometerData).samples
                    else -> throw IllegalArgumentException("Unsupported data type: $dataType")
                  }

              dataSavers
                  .asList()
                  .filter { it.isEnabled.value }
                  .forEach { saver ->
                    saver.saveData(
                        phoneTimestamp,
                        deviceId,
                        currentRecordingName,
                        dataType.name,
                        batchData,
                    )
                  }
            },
            { error ->
              logState.addLogError(
                  "${dataType.name} recording failed for device $deviceId: ${error.message}",
              )
            },
        )
  }

  private fun logDeviceAndAppInfo() {
    appInfoProvider?.let { provider ->
      logState.addLogMessage("App version: ${provider.versionName} (code: ${provider.versionCode})")
      logState.addLogMessage("OS version: ${provider.androidVersion}")
      logState.addLogMessage("Phone: ${provider.deviceInfo}")
    }

    val polarSdkVersion = polarManager.getSdkVersion()
    logState.addLogMessage("Polar SDK version: $polarSdkVersion")

    // Log sensor settings for each selected device
    deviceState.selectedDevices.value.forEach { device ->
      logState.addLogMessage("Device: ${device.info.name} (${device.info.deviceId})")
      device.dataTypes.forEach { dataType ->
        val settings = device.sensorSettings[dataType]
        val settingsStr = formatSensorSettings(settings)
        logState.addLogMessage("  $dataType: $settingsStr")
      }
    }
  }

  private fun formatSensorSettings(settings: PolarSensorSetting?): String {
    if (settings == null || settings.settings.isEmpty()) {
      return "(no configurable settings)"
    }
    val parts = mutableListOf<String>()
    settings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.let { parts.add("$it Hz") }
    settings.settings[PolarSensorSetting.SettingType.RESOLUTION]?.let { parts.add("$it bits") }
    settings.settings[PolarSensorSetting.SettingType.RANGE]?.let { parts.add("±${it}g") }
    settings.settings[PolarSensorSetting.SettingType.CHANNELS]?.let { parts.add("$it ch") }
    return if (parts.isEmpty()) "(no configurable settings)" else parts.joinToString(", ")
  }

  private fun saveUnsavedLogMessages(messages: List<LogEntry>) {
    val enabledDataSavers = dataSavers.asList().filter { it.isEnabled.value }
    val selectedDevices = deviceState.selectedDevices.value
    val currentRecordingName = _recordingState.value.currentRecordingName

    if (!_recordingState.value.isRecording ||
        selectedDevices.isEmpty() ||
        enabledDataSavers.isEmpty()) {
      return
    }

    synchronized(messagesLock) {
      for (i in lastSavedLogSize until messages.size) {
        val entry = messages[i]
        val data = listOf(mapOf("type" to entry.type.name, "message" to entry.message))

        selectedDevices.forEach { device ->
          enabledDataSavers.forEach { saver ->
            saver.saveData(
                entry.timestamp,
                device.info.deviceId,
                currentRecordingName,
                "LOG",
                data,
            )
          }
        }
      }
      lastSavedLogSize = messages.size
    }
  }

  private fun disposeAllStreams() {
    disposables.forEach { (_, deviceDisposables) ->
      deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
    }
    disposables.clear()
  }
}
