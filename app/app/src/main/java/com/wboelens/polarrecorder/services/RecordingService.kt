package com.wboelens.polarrecorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarTemperatureData
import com.wboelens.polarrecorder.PolarRecorderApplication
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.getDataFragment
import com.wboelens.polarrecorder.state.Device
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.state.LogEntry
import com.wboelens.polarrecorder.state.LogState
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class RecordingService : Service() {
  companion object {
    const val ACTION_START_RECORDING = "com.wboelens.polarrecorder.START_RECORDING"
    const val ACTION_STOP_RECORDING = "com.wboelens.polarrecorder.STOP_RECORDING"
    const val EXTRA_RECORDING_NAME = "recording_name"
    const val EXTRA_DEVICE_IDS = "device_ids"
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "RecordingServiceChannel"
    private const val RETRY_COUNT = 3L
  }

  private val executor = Executors.newSingleThreadScheduledExecutor()

  // Dependencies (initialized in onCreate)
  private lateinit var polarManager: PolarManager
  private lateinit var deviceState: DeviceState
  private lateinit var logState: LogState
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var dataSavers: DataSavers

  // Recording state
  private val _recordingState = MutableStateFlow(RecordingState())
  private val _lastData =
      MutableStateFlow<Map<String, Map<PolarBleApi.PolarDeviceDataType, Float?>>>(emptyMap())
  private val _lastDataTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())

  // RxJava disposables for streams
  private val disposables = mutableMapOf<String, MutableMap<String, Disposable>>()
  private val messagesLock = Any()
  private var lastSavedLogSize = 0

  // Coroutine scope for state observation
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private var connectedDevicesJob: Job? = null
  private var logMessagesJob: Job? = null

  // Binder
  private val binder = LocalBinder()

  inner class LocalBinder : Binder() {
    val recordingState: StateFlow<RecordingState> = _recordingState
    val lastData: StateFlow<Map<String, Map<PolarBleApi.PolarDeviceDataType, Float?>>> = _lastData
    val lastDataTimestamps: StateFlow<Map<String, Long>> = _lastDataTimestamps

    fun startRecording(recordingName: String) {
      this@RecordingService.doStartRecording(recordingName)
    }

    fun stopRecording() {
      this@RecordingService.doStopRecording()
    }

    fun getService(): RecordingService = this@RecordingService
  }

  private val app: PolarRecorderApplication
    get() = application as PolarRecorderApplication

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    initializeDependencies()
    startObservingDeviceChanges()
    startObservingLogMessages()
  }

  private fun initializeDependencies() {
    app.ensureManagersInitialized()
    polarManager = app.polarManager!!
    deviceState = app.deviceState
    logState = app.logState
    preferencesManager = app.preferencesManager
    dataSavers = app.dataSavers!!
  }

  private fun startObservingDeviceChanges() {
    connectedDevicesJob =
        scope.launch {
          deviceState.connectedDevices.collect { devices -> handleConnectedDevicesChange(devices) }
        }
  }

  private fun startObservingLogMessages() {
    logMessagesJob = scope.launch { logState.logMessages.collect { handleLogMessagesChange(it) } }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_RECORDING -> {
        val name = intent.getStringExtra(EXTRA_RECORDING_NAME)
        if (name != null) {
          doStartRecording(name)
        }
      }
      ACTION_STOP_RECORDING -> doStopRecording()
      else -> {
        // Service started without action - show notification if recording
        if (_recordingState.value.isRecording) {
          val notification = createNotification()
          startForeground(NOTIFICATION_ID, notification)
        }
      }
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder = binder

  @Suppress("NestedBlockDepth")
  private fun handleConnectedDevicesChange(devices: List<Device>) {
    if (!_recordingState.value.isRecording) {
      return
    }

    if (devices.isEmpty() && preferencesManager.recordingStopOnDisconnect) {
      logState.addLogError("No devices connected, stopping recording")
      doStopRecording()
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
  }

  private fun handleLogMessagesChange(messages: List<LogEntry>) {
    if (messages.isNotEmpty() && messages.size > lastSavedLogSize) {
      saveUnsavedLogMessages(messages)
    }
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

  @Suppress("ReturnCount")
  private fun doStartRecording(recordingName: String) {
    if (recordingName.isEmpty()) {
      logState.addLogError("Recording name cannot be the empty string")
      return
    }

    if (_recordingState.value.isRecording) {
      logState.addLogError("Recording already in progress")
      return
    }

    val selectedDevices = deviceState.selectedDevices.value
    if (selectedDevices.isEmpty()) {
      logState.addLogError("Cannot start recording: No devices selected")
      return
    }

    val connectedDevices = deviceState.connectedDevices.value
    val connectedDeviceIds = connectedDevices.map { it.info.deviceId }
    val disconnectedDevices =
        selectedDevices.filter { !connectedDeviceIds.contains(it.info.deviceId) }
    if (disconnectedDevices.isNotEmpty()) {
      val disconnectedNames = disconnectedDevices.map { it.info.name }.joinToString(", ")
      logState.addLogError(
          "Cannot start recording: Some selected devices are not connected: $disconnectedNames")
      return
    }

    // Check if datasavers are initialized
    val enabledDataSavers = dataSavers.asList().filter { it.isEnabled.value }
    if (enabledDataSavers.isEmpty()) {
      logState.addLogError("Cannot start recording: No data savers are enabled")
      return
    }

    val uninitializedSavers =
        enabledDataSavers.filter { it.isInitialized.value != InitializationState.SUCCESS }
    if (uninitializedSavers.isNotEmpty()) {
      logState.addLogError(
          "Cannot start recording: Data savers are not initialized. " +
              "Please go through the initialization process first.")
      return
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
    val startTime = System.currentTimeMillis()
    _recordingState.value =
        RecordingState(
            isRecording = true,
            currentRecordingName = recordingName,
            recordingStartTime = startTime,
        )

    // Start foreground notification
    val notification = createNotification()
    startForeground(NOTIFICATION_ID, notification)
    scheduleNotificationUpdates()

    // Start streams
    selectedDevices.forEach { device -> startStreamsForDevice(device) }
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
      dataType: PolarBleApi.PolarDeviceDataType,
  ): Disposable {
    val selectedSensorSettings = deviceState.getDeviceSensorSettingsForDataType(deviceId, dataType)
    val currentRecordingName = _recordingState.value.currentRecordingName

    return polarManager
        .startStreaming(deviceId, dataType, selectedSensorSettings)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.computation())
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
              val phoneTimestamp = System.currentTimeMillis()

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
                    PolarBleApi.PolarDeviceDataType.HR -> (data as PolarHrData).samples
                    PolarBleApi.PolarDeviceDataType.PPI -> (data as PolarPpiData).samples
                    PolarBleApi.PolarDeviceDataType.ACC -> (data as PolarAccelerometerData).samples
                    PolarBleApi.PolarDeviceDataType.PPG -> (data as PolarPpgData).samples
                    PolarBleApi.PolarDeviceDataType.ECG -> (data as PolarEcgData).samples
                    PolarBleApi.PolarDeviceDataType.GYRO -> (data as PolarGyroData).samples
                    PolarBleApi.PolarDeviceDataType.TEMPERATURE ->
                        (data as PolarTemperatureData).samples
                    PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE ->
                        (data as PolarTemperatureData).samples
                    PolarBleApi.PolarDeviceDataType.MAGNETOMETER ->
                        (data as PolarMagnetometerData).samples
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
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          packageInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
    logState.addLogMessage("App version: $versionName (code: $versionCode)")

    val polarSdkVersion = polarManager.getSdkVersion()
    logState.addLogMessage("Polar SDK version: $polarSdkVersion")

    val androidVersion =
        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    logState.addLogMessage("OS version: $androidVersion")

    val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    logState.addLogMessage("Phone: $deviceInfo")
  }

  private fun doStopRecording() {
    if (!_recordingState.value.isRecording) {
      logState.addLogError("Trying to stop recording while no recording in progress")
      return
    }

    logState.addLogMessage("Recording stopped")
    logState.requestFlushQueue()

    Handler(Looper.getMainLooper()).post {
      saveUnsavedLogMessages(logState.logMessages.value)

      // Dispose all streams
      disposables.forEach { (_, deviceDisposables) ->
        deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
      }
      disposables.clear()

      // Tell dataSavers to stop saving
      dataSavers.asList().filter { it.isEnabled.value }.forEach { saver -> saver.stopSaving() }

      // Update state
      _recordingState.value = RecordingState(isRecording = false)

      // Clear timestamps
      _lastDataTimestamps.value = emptyMap()

      // Stop foreground and service
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    }
  }

  private fun scheduleNotificationUpdates() {
    executor.scheduleWithFixedDelay(
        {
          val notification = createNotification()
          val notificationManager =
              getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        },
        1,
        1,
        TimeUnit.MINUTES,
    )
  }

  private fun createNotificationChannel() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
              CHANNEL_ID,
              "Recording Service Channel",
              NotificationManager.IMPORTANCE_LOW,
          )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    val durationMs = System.currentTimeMillis() - _recordingState.value.recordingStartTime
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationText =
        if (minutes == 1L) {
          "1 minute"
        } else {
          "$minutes minutes"
        }

    val pendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Recording in progress")
        .setContentText("Recording for $durationText")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .build()
  }

  override fun onDestroy() {
    // Cancel coroutine jobs
    connectedDevicesJob?.cancel()
    logMessagesJob?.cancel()

    // Dispose any active streams
    disposables.forEach { (_, deviceDisposables) ->
      deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
    }
    disposables.clear()

    executor.shutdownNow()
    super.onDestroy()
  }
}
