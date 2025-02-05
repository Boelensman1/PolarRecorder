package com.wboelens.polarrecorder.managers

import android.content.Context
import android.content.Intent
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecordingManager(
    private val context: Context,
    private val polarManager: PolarManager,
    private val logViewModel: LogViewModel,
    private val deviceViewModel: DeviceViewModel,
    private val dataSavers: DataSavers
) {
  companion object {
    private const val TAG = "RecordingManager"
    private const val RETRY_COUNT = 3L
  }

  private val gson = Gson()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording

  private var currentRecordingName: String = ""
  private var currentAppendTimestamp: Boolean = false

  private val connectedDevicesObserver =
      Observer<List<DeviceViewModel.Device>> { devices ->
        if (devices.isEmpty() && _isRecording.value) {
          stopRecording()
          logViewModel.addLogError("Recording stopped: No devices connected")
        }
      }

  private val logMessagesObserver =
      Observer<List<LogViewModel.LogEntry>> { messages ->
        if (messages.isNotEmpty()) {
          saveLogEntry(messages.last())
        }
      }

  private val disposables = mutableMapOf<String, MutableMap<String, Disposable>>()
  private val unsavedLogMessages = mutableListOf<LogViewModel.LogEntry>()

  private val _lastDataTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
  val lastDataTimestamps: StateFlow<Map<String, Long>> = _lastDataTimestamps

  init {
    deviceViewModel.connectedDevices.observeForever(connectedDevicesObserver)
    logViewModel.logMessages.observeForever(logMessagesObserver)
  }

  fun configure(recordingName: String, appendTimestamp: Boolean) {
    currentRecordingName = recordingName
    currentAppendTimestamp = appendTimestamp
  }

  private fun saveLogEntry(entry: LogViewModel.LogEntry) {
    val payload =
        gson.toJson(
            mapOf(
                "phoneTimeStamp" to entry.timestamp,
                "message" to entry.message,
                "type" to entry.type.name))

    val connectedDevices = deviceViewModel.connectedDevices.value
    if (_isRecording.value && !connectedDevices.isNullOrEmpty()) {
      connectedDevices.forEach { device ->
        dataSavers
            .asList()
            .filter { it.isEnabled.value }
            .forEach { saver ->
              saver.saveData(
                  System.currentTimeMillis(),
                  device.info.deviceId,
                  currentRecordingName,
                  "LOG",
                  payload)
            }
      }
    } else {
      unsavedLogMessages.add(entry)
    }
  }

  fun startRecording() {
    if (currentRecordingName === "") {
      logViewModel.addLogError("Recording name cannot be the empty string")
      return
    }

    if (_isRecording.value) {
      logViewModel.addLogError("Recording already in progress")
      return
    }

    val connectedDevices = deviceViewModel.connectedDevices.value
    if (connectedDevices.isNullOrEmpty()) {
      logViewModel.addLogError("Cannot start recording: No devices connected")
      return
    }

    // Save all queued log messages
    val messagesToSave =
        unsavedLogMessages.toList() // Create a copy of the list as saveLogEntry modifies the list
    messagesToSave.forEach { saveLogEntry(it) }
    unsavedLogMessages.clear()

    // Clear timestamps when starting new recording
    _lastDataTimestamps.value = emptyMap()

    val recordingNameWithTimestamp =
        if (currentAppendTimestamp) {
          val timestamp =
              java.text
                  .SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                  .format(java.util.Date())
          "${currentRecordingName}_$timestamp"
        } else currentRecordingName

    val deviceIdsWithDataTypes: Map<String, Set<String>> =
        connectedDevices.associate { device ->
          val dataTypesWithLog =
              deviceViewModel
                  .getDeviceDataTypes(device.info.deviceId)
                  .map { it.name }
                  .toMutableList()
          // add LOG datatype
          dataTypesWithLog.add("LOG")

          device.info.deviceId to dataTypesWithLog.toSet()
        }

    // tell dataSavers to initialise saving
    dataSavers
        .asList()
        .filter { it.isEnabled.value }
        .forEach { saver -> saver.initSaving(recordingNameWithTimestamp, deviceIdsWithDataTypes) }

    _isRecording.value = true

    logViewModel.addLogSuccess("Recording $recordingNameWithTimestamp started")
    Log.d(TAG, "Saving data to ${dataSavers.enabledCount} data saver(s)")

    // Start the foreground service
    val serviceIntent = Intent(context, RecordingService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }

    // Start streams for each connected device
    connectedDevices.forEach { device ->
      val deviceId = device.info.deviceId
      disposables[deviceId] = mutableMapOf()
      disposables[deviceId]?.let { deviceDisposables ->
        val selectedDataTypes = deviceViewModel.getDeviceDataTypes(deviceId)
        selectedDataTypes.forEach { dataType ->
          deviceDisposables[dataType.name.lowercase()] =
              startStreamForDevice(deviceId, recordingNameWithTimestamp, dataType)
        }
      }
    }
  }

  private fun startStreamForDevice(
      deviceId: String,
      recordingNameWithTimestamp: String,
      dataType: PolarBleApi.PolarDeviceDataType
  ): Disposable {
    val selectedDataTypes = deviceViewModel.getDeviceDataTypes(deviceId)
    val selectedSensorSettings =
        deviceViewModel.getDeviceSensorSettingsForDataType(deviceId, dataType)

    logViewModel.addLogMessage("Starting $dataType stream for $deviceId")

    if (!selectedDataTypes.contains(dataType)) {
      return Disposable.empty()
    }

    return polarManager
        .startStreaming(deviceId, dataType, selectedSensorSettings)
        .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
        .observeOn(io.reactivex.rxjava3.schedulers.Schedulers.computation())
        .retry(RETRY_COUNT)
        .doOnSubscribe { Log.d(TAG, "Starting stream for $deviceId - $dataType") }
        .doOnError { error ->
          Log.e(TAG, "Stream error for $deviceId - $dataType: ${error.message}", error)
        }
        .doOnComplete { Log.w(TAG, "Stream completed unexpectedly for $deviceId - $dataType") }
        .subscribe(
            { data ->
              val phoneTimestamp = System.currentTimeMillis()

              // Update last data timestamp for this device
              _lastDataTimestamps.value = _lastDataTimestamps.value + (deviceId to phoneTimestamp)

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
                          "recordingName" to recordingNameWithTimestamp,
                          "dataType" to dataType,
                          "data" to batchData))

              dataSavers
                  .asList()
                  .filter { it.isEnabled.value }
                  .forEach { saver ->
                    saver.saveData(
                        phoneTimestamp,
                        deviceId,
                        recordingNameWithTimestamp,
                        dataType.name,
                        payload)
                  }
            },
            { error ->
              logViewModel.addLogError(
                  "${dataType.name} recording failed for device $deviceId: ${error.message}")
            })
  }

  fun stopRecording() {
    if (!_isRecording.value) {
      logViewModel.addLogMessage("No recording in progress")
      return
    }

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
    logViewModel.addLogMessage("Recording stopped")

    // Clear timestamps when stopping recording
    _lastDataTimestamps.value = emptyMap()
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
