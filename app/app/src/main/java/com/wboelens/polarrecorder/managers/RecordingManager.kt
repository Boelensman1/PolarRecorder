package com.wboelens.polarrecorder.managers

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarTemperatureData
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.services.RecordingService
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DeviceInfoForDataSaver(val deviceName: String, val dataTypes: Set<String>)

class RecordingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val polarManager: PolarManager,
    private val logViewModel: LogViewModel,
    private val deviceViewModel: DeviceViewModel,
    private val preferencesManager: PreferencesManager,
    private val dataSavers: DataSavers
) {
  companion object {
    private const val RETRY_COUNT = 3L
  }

  private val gson = Gson()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording

  var currentRecordingName: String = ""

  private val connectedDevicesObserver =
      Observer<List<DeviceViewModel.Device>> { devices ->
        if (!_isRecording.value) {
          return@Observer
        }

        if (devices.isEmpty() && preferencesManager.recordingStopOnDisconnect) {
          logViewModel.addLogError("No devices connected, stopping recording")
          stopRecording()
        } else {
          val selectedDevices = deviceViewModel.selectedDevices.value ?: emptyList()
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
            // Note: isEmpty() != false checks for null, empty, or non-existent map
            if (disposables[device.info.deviceId]?.isEmpty() != false) {
              // Restart data streams for this device
              startStreamsForDevice(device)
            }
          }
        }
      }

  private val logMessagesObserver =
      Observer<List<LogViewModel.LogEntry>> { messages ->
        if (messages.isNotEmpty() && messages.size > lastSavedLogSize) {
          saveUnsavedLogMessages(messages)
        }
      }

  private val disposables = mutableMapOf<String, MutableMap<String, Disposable>>()
  private val messagesLock = Any()

  // Track how many log messages we've processed
  private var lastSavedLogSize = 0

  private val _lastDataTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
  val lastDataTimestamps: StateFlow<Map<String, Long>> = _lastDataTimestamps

  init {
    deviceViewModel.connectedDevices.observeForever(connectedDevicesObserver)
    logViewModel.logMessages.observeForever(logMessagesObserver)
  }

  private fun saveUnsavedLogMessages(messages: List<LogViewModel.LogEntry>) {
    val enabledDataSavers = dataSavers.asList().filter { it.isEnabled.value }
    val selectedDevices = deviceViewModel.selectedDevices.value

    if (!_isRecording.value || selectedDevices.isNullOrEmpty() || enabledDataSavers.isEmpty()) {
      // Recording is not in progress, or no devices or data savers are enabled
      // So we can't save the messages
      return
    }

    synchronized(messagesLock) {
      for (i in lastSavedLogSize until messages.size) {
        val entry = messages[i]
        val payload = gson.toJson(mapOf("type" to entry.type.name, "message" to entry.message))

        selectedDevices.forEach { device ->
          enabledDataSavers.forEach { saver ->
            saver.saveData(
                entry.timestamp,
                device.info.deviceId,
                currentRecordingName,
                "LOG",
                payload,
            )
          }
        }
      }
      lastSavedLogSize = messages.size
    }
  }

  fun startRecording() {
    if (preferencesManager.recordingName === "") {
      logViewModel.addLogError("Recording name cannot be the empty string")
      return
    }

    if (_isRecording.value) {
      logViewModel.addLogError("Recording already in progress")
      return
    }

    val selectedDevices = deviceViewModel.selectedDevices.value
    if (selectedDevices.isNullOrEmpty()) {
      logViewModel.addLogError("Cannot start recording: No devices connected")
      return
    }

    // Clear timestamps when starting new recording
    _lastDataTimestamps.value = emptyMap()

    // Log app version information
    logDeviceAndAppInfo()

    logViewModel.addLogSuccess(
        "Recording $currentRecordingName started, saving to ${
          dataSavers.enabledCount
        } data saver(s)",
    )

    // Start the foreground service
    val serviceIntent = Intent(context, RecordingService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }

    _isRecording.value = true
    selectedDevices.forEach { device -> startStreamsForDevice(device) }
  }

  private fun startStreamsForDevice(device: DeviceViewModel.Device) {
    val deviceId = device.info.deviceId

    disposables[deviceId] = mutableMapOf()
    disposables[deviceId]?.let { deviceDisposables ->
      val selectedDataTypes = deviceViewModel.getDeviceDataTypes(deviceId)
      selectedDataTypes.forEach { dataType ->
        deviceDisposables[dataType.name.lowercase()] = startStreamForDevice(deviceId, dataType)
      }
    }
  }

  private fun startStreamForDevice(
      deviceId: String,
      dataType: PolarBleApi.PolarDeviceDataType
  ): Disposable {
    val selectedSensorSettings =
        deviceViewModel.getDeviceSensorSettingsForDataType(deviceId, dataType)

    return polarManager
        .startStreaming(deviceId, dataType, selectedSensorSettings)
        .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
        .observeOn(io.reactivex.rxjava3.schedulers.Schedulers.computation())
        .retry(RETRY_COUNT)
        .doOnSubscribe { logViewModel.addLogMessage("Starting $dataType stream for $deviceId") }
        .doOnError { error ->
          logViewModel.addLogError("Stream error for $deviceId - $dataType: ${error.message}")
        }
        .doOnComplete {
          logViewModel.addLogError("Stream completed unexpectedly for $deviceId - $dataType")
        }
        .subscribe(
            { data ->
              val phoneTimestamp = System.currentTimeMillis()

              // Update last data timestamp for this device
              _lastDataTimestamps.value += (deviceId to phoneTimestamp)

              val batchData =
                  when (dataType) {
                    PolarBleApi.PolarDeviceDataType.HR -> (data as PolarHrData).samples
                    PolarBleApi.PolarDeviceDataType.PPI -> (data as PolarPpiData).samples
                    PolarBleApi.PolarDeviceDataType.ACC -> (data as PolarAccelerometerData).samples
                    PolarBleApi.PolarDeviceDataType.PPG -> (data as PolarPpgData).samples
                    PolarBleApi.PolarDeviceDataType.ECG -> (data as PolarEcgData).samples
                    PolarBleApi.PolarDeviceDataType.GYRO -> (data as PolarGyroData).samples
                    PolarBleApi.PolarDeviceDataType.TEMPERATURE ->
                        (data as PolarTemperatureData).samples

                    PolarBleApi.PolarDeviceDataType.MAGNETOMETER ->
                        (data as PolarMagnetometerData).samples

                    else -> throw IllegalArgumentException("Unsupported data type: $dataType")
                  }

              val payload =
                  gson.toJson(
                      mapOf(
                          "phoneTimeStamp" to phoneTimestamp,
                          "deviceId" to deviceId,
                          "recordingName" to currentRecordingName,
                          "dataType" to dataType,
                          "data" to batchData,
                      ),
                  )

              dataSavers
                  .asList()
                  .filter { it.isEnabled.value }
                  .forEach { saver ->
                    saver.saveData(
                        phoneTimestamp,
                        deviceId,
                        currentRecordingName,
                        dataType.name,
                        payload,
                    )
                  }
            },
            { error ->
              logViewModel.addLogError(
                  "${dataType.name} recording failed for device $deviceId: ${error.message}",
              )
            },
        )
  }

  private fun logDeviceAndAppInfo() {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          packageInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
    logViewModel.addLogMessage("App version: $versionName (code: $versionCode)")

    // Add Polar SDK version information
    val polarSdkVersion = polarManager.getSdkVersion()
    logViewModel.addLogMessage("Polar SDK version: $polarSdkVersion")

    // Add Android version information
    val androidVersion =
        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    logViewModel.addLogMessage("OS version: $androidVersion")

    // Add device information
    val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    logViewModel.addLogMessage("Phone: $deviceInfo")
  }

  fun stopRecording() {
    if (!_isRecording.value) {
      logViewModel.addLogError("Trying to stop recording while no recording in progress")
      return
    }

    logViewModel.addLogMessage("Recording stopped")
    // Force save the final log message (pt. 1)
    logViewModel.requestFlushQueue()

    // Wait for the log to be flushed before continuing by posting to the main thread, just like
    // requestFlushQueue does.
    Handler(Looper.getMainLooper()).post {
      // Force save the final log message (pt. 2)
      saveUnsavedLogMessages(logViewModel.logMessages.value?.toList() ?: emptyList())

      // Stop the foreground service
      context.stopService(Intent(context, RecordingService::class.java))

      // Dispose all streams
      disposables.forEach { (_, deviceDisposables) ->
        deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
      }
      disposables.clear()

      // tell dataSavers to stop saving
      dataSavers.asList().filter { it.isEnabled.value }.forEach { saver -> saver.stopSaving() }

      _isRecording.value = false

      // Clear timestamps when stopping recording
      _lastDataTimestamps.value = emptyMap()
    }
  }

  fun cleanup() {
    // Dispose any active streams
    disposables.forEach { (_, deviceDisposables) ->
      deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
    }
    disposables.clear()

    // cleanup dataSavers
    dataSavers.asList().forEach { saver -> saver.cleanup() }

    deviceViewModel.connectedDevices.removeObserver(connectedDevicesObserver)
    logViewModel.logMessages.removeObserver(logMessagesObserver)
  }
}
