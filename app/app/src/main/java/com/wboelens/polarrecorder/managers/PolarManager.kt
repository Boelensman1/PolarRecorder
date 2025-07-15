package com.wboelens.polarrecorder.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.viewModels.ConnectionState
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext

data class DeviceStreamCapabilities(
    val availableTypes: Set<PolarDeviceDataType>,
    val settings: Map<PolarDeviceDataType, Pair<PolarSensorSetting, PolarSensorSetting>>
)

data class PolarDeviceSettings(val deviceTimeOnConnect: Calendar?, val sdkModeEnabled: Boolean?)

sealed class PolarApiResult<out T> {
  data class Success<out R>(val value: R? = null) : PolarApiResult<R>()

  data class Failure(val message: String, val throwable: Throwable?) : PolarApiResult<Nothing>()
}

@Suppress("TooManyFunctions")
class PolarManager(
    private val context: Context,
    private val deviceViewModel: DeviceViewModel,
    private val logViewModel: LogViewModel
) {
  companion object {
    private const val TAG = "PolarManager"
    private const val SCAN_INTERVAL = 30000L // 30 seconds between scans
    private const val SCAN_DURATION = 10000L // 10 seconds per scan
    private const val MAX_RETRY_ERRORS = 3L
  }

  private var scanDisposable: Disposable? = null
  private var scanTimer: Timer? = null

  private val api: PolarBleApi by lazy {
    PolarBleApiDefaultImpl.defaultImplementation(
        context,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
  }
  private val disposables = CompositeDisposable()

  private val deviceCapabilities = mutableMapOf<String, DeviceStreamCapabilities>()
  private val deviceFeatureReadiness =
      mutableMapOf<String, MutableSet<PolarBleApi.PolarBleSdkFeature>>()

  private val deviceSettings = mutableMapOf<String, PolarDeviceSettings>()

  private var _isRefreshing = mutableStateOf(false)
  val isRefreshing: State<Boolean> = _isRefreshing

  private var _isBLEEnabled = mutableStateOf(false)
  val isBLEEnabled: State<Boolean> = _isBLEEnabled

  private val deviceBatteryLevels = mutableMapOf<String, Int>()

  init {
    setupPolarApi()
  }

  private fun setupPolarApi() {
    api.setApiCallback(
        object : PolarBleApiCallback() {
          override fun blePowerStateChanged(powered: Boolean) {
            Log.d(TAG, "BLE power: $powered")
            _isBLEEnabled.value = powered
          }

          override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            logViewModel.addLogMessage(
                "Fetching capabilities for device ${polarDeviceInfo.deviceId}")
            deviceViewModel.updateConnectionState(
                polarDeviceInfo.deviceId, ConnectionState.FETCHING_CAPABILITIES)

            val disposable =
                Single.just(Unit)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ _ -> // Explicitly using Consumer<Unit> overload
                      MainScope().launch {
                        var capabilities: DeviceStreamCapabilities?
                        try {
                          capabilities = fetchDeviceCapabilities(polarDeviceInfo.deviceId).await()
                        } catch (error: Throwable) {
                          Log.e(TAG, "Failed to fetch device capabilities", error)
                          logViewModel.addLogError(
                              "Failed to fetch device capabilities for ${polarDeviceInfo.deviceId} (${error.message}), falling back to alternative method",
                              false)
                          capabilities =
                              fetchDeviceCapabilitiesViaFallback(polarDeviceInfo.deviceId)
                        }

                        logViewModel.addLogMessage(
                            "Fetching settings for device ${polarDeviceInfo.deviceId}")
                        deviceViewModel.updateConnectionState(
                            polarDeviceInfo.deviceId, ConnectionState.FETCHING_SETTINGS)

                        val settings = fetchDeviceSettings(polarDeviceInfo.deviceId).await()
                        if (capabilities !== null && capabilities.availableTypes.isNotEmpty()) {
                          finishConnectDevice(polarDeviceInfo, capabilities, settings)
                        } else {
                          // alternate method also failed, disconnect
                          deviceViewModel.updateConnectionState(
                              polarDeviceInfo.deviceId, ConnectionState.FAILED)
                          logViewModel.addLogMessage(
                              "Failed to connect to device, could not fetch capabilities.")
                          api.disconnectFromDevice(polarDeviceInfo.deviceId)
                        }
                      }
                    })
            disposables.add(disposable)
          }

          override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
            deviceViewModel.updateConnectionState(
                polarDeviceInfo.deviceId, ConnectionState.CONNECTING)
            logViewModel.addLogMessage("Connecting to device ${polarDeviceInfo.deviceId}")
          }

          override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            deviceCapabilities.remove(polarDeviceInfo.deviceId)
            if (deviceViewModel.getConnectionState(polarDeviceInfo.deviceId) ===
                ConnectionState.DISCONNECTING) {
              // a disconnect was requested, so this disconnect is expected
              logViewModel.addLogMessage("Device ${polarDeviceInfo.deviceId} disconnected")
            } else {
              logViewModel.addLogError("Device ${polarDeviceInfo.deviceId} disconnected")
            }

            deviceViewModel.updateConnectionState(
                polarDeviceInfo.deviceId, ConnectionState.DISCONNECTED)
          }

          override fun bleSdkFeatureReady(
              identifier: String,
              feature: PolarBleApi.PolarBleSdkFeature
          ) {
            Log.d(TAG, "Feature $feature ready for device $identifier")
            deviceFeatureReadiness.getOrPut(identifier) { mutableSetOf() }.add(feature)
          }

          override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            Log.d(TAG, "DIS info received for device $identifier: $disInfo")
          }

          override fun htsNotificationReceived(
              identifier: String,
              data: PolarHealthThermometerData
          ) {
            Log.d(TAG, "PolarHealthThermometer Data info received for device $identifier: $data")
          }

          override fun batteryLevelReceived(identifier: String, level: Int) {
            Log.d(TAG, "Battery level for device $identifier: $level")
            deviceBatteryLevels[identifier] = level
            deviceViewModel.updateBatteryLevel(identifier, level)
          }
        })
  }

  private fun fetchDeviceCapabilities(deviceId: String): Single<DeviceStreamCapabilities> {
    return Single.create { emitter ->
          // Wait for FEATURE_DEVICE_INFO to be ready
          if (isFeatureAvailable(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)) {
            emitter.onSuccess(Unit)
          } else {
            emitter.onError(IllegalStateException("Device info feature not ready"))
          }
        }
        .flatMap { getAvailableOnlineStreamDataTypes(deviceId) }
        .retryWhen { errors ->
          errors.take(MAX_RETRY_ERRORS).flatMap { _ ->
            // Wait 1 second before retrying
            Single.timer(1, TimeUnit.SECONDS).flatMapPublisher { Flowable.just(it) }
          }
        }
        .flatMap { types ->
          val settingsRequests =
              types.map { dataType ->
                getStreamSettings(deviceId, dataType).map { Triple(dataType, it.first, it.second) }
              }

          Single.zip(settingsRequests) { results ->
            val settings =
                results
                    .map { it as Triple<*, *, *> }
                    .associate { triple ->
                      (triple.first as PolarDeviceDataType) to
                          Pair(
                              triple.second as PolarSensorSetting,
                              triple.third as PolarSensorSetting)
                    }
            DeviceStreamCapabilities(types.toSet(), settings)
          }
        }
  }

  private fun fetchDeviceCapabilitiesViaFallback(deviceId: String): DeviceStreamCapabilities {
    val availableTypes = mutableSetOf<PolarDeviceDataType>()
    val settings = mutableMapOf<PolarDeviceDataType, Pair<PolarSensorSetting, PolarSensorSetting>>()

    deviceFeatureReadiness[deviceId]?.forEach { feature ->
      when (feature) {
        PolarBleApi.PolarBleSdkFeature.FEATURE_HR -> {
          availableTypes.add(PolarDeviceDataType.HR)
          settings[PolarDeviceDataType.HR] =
              Pair(PolarSensorSetting(emptyMap()), PolarSensorSetting(emptyMap()))
        }
        else -> {
          /* no other features seem related to capabilities */
        }
      }
    }

    return DeviceStreamCapabilities(availableTypes, settings)
  }

  private fun fetchDeviceSettings(deviceId: String): Single<PolarDeviceSettings> {
    return Single.create { emitter ->
      MainScope().launch {
        var deviceTime: Calendar? = null
        var deviceSdkMode: Boolean? = null

        if (isFeatureAvailable(
            deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP)) {
          try {
            deviceTime = getTime(deviceId)
          } catch (e: Exception) {
            logViewModel.addLogError("Failed to fetch device time (${e.message})", false)
          }
        }

        if (isFeatureAvailable(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)) {
          try {
            deviceSdkMode = getSdkMode(deviceId)
          } catch (e: Exception) {
            logViewModel.addLogError("Failed to fetch device sdk mode (${e.message})", false)
          }
        }

        val deviceSettings = PolarDeviceSettings(deviceTime, deviceSdkMode)
        emitter.onSuccess(deviceSettings)
      }
    }
  }

  private fun finishConnectDevice(
      polarDeviceInfo: PolarDeviceInfo,
      capabilities: DeviceStreamCapabilities,
      settings: PolarDeviceSettings
  ) {
    deviceCapabilities[polarDeviceInfo.deviceId] = capabilities
    deviceSettings[polarDeviceInfo.deviceId] = settings
    logViewModel.addLogMessage(
        "Device ${polarDeviceInfo.deviceId} Connected",
    )
    deviceViewModel.updateConnectionState(polarDeviceInfo.deviceId, ConnectionState.CONNECTED)
  }

  fun getDeviceCapabilities(deviceId: String): DeviceStreamCapabilities? {
    return deviceCapabilities[deviceId]
  }

  fun getDeviceSettings(deviceId: String): PolarDeviceSettings? {
    return deviceSettings[deviceId]
  }

  private fun isFeatureAvailable(
      deviceId: String,
      feature: PolarBleApi.PolarBleSdkFeature
  ): Boolean {
    return deviceFeatureReadiness[deviceId]?.contains(feature) == true
  }

  fun isTimeManagementAvailable(deviceId: String): Boolean {
    return isFeatureAvailable(
        deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP)
  }

  fun connectToDevice(deviceId: String) {
    try {
      api.connectToDevice(deviceId)
    } catch (e: PolarInvalidArgument) {
      Log.e(TAG, "Connection failed: ${e.message}", e)
      deviceViewModel.updateConnectionState(deviceId, ConnectionState.FAILED)
    }
  }

  fun disconnectDevice(deviceId: String) {
    try {
      api.disconnectFromDevice(deviceId)
      deviceViewModel.updateConnectionState(deviceId, ConnectionState.DISCONNECTING)
    } catch (e: PolarInvalidArgument) {
      Log.e(TAG, "Disconnect failed: ${e.message}", e)
    }
  }

  fun disconnectAllDevices() {
    deviceViewModel.connectedDevices.value?.forEach { device ->
      disconnectDevice(device.info.deviceId)
    }
  }

  fun scanForDevices() {
    Log.d(TAG, "Starting scan")
    _isRefreshing.value = true
    scanDisposable?.dispose()

    scanDisposable =
        api.searchForDevice()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { deviceInfo -> deviceViewModel.addDevice(deviceInfo) },
                { error ->
                  logViewModel.addLogMessage("Scan error: ${error.message}")

                  Log.d(TAG, "Stopping scan")
                  _isRefreshing.value = false
                },
                {
                  Log.d(TAG, "Stopping scan")
                  _isRefreshing.value = false
                })

    Handler(Looper.getMainLooper())
        .postDelayed(
            {
              scanDisposable?.dispose()
              Log.d(TAG, "Stopping scan")
              _isRefreshing.value = false
            },
            SCAN_DURATION)
  }

  fun startPeriodicScanning() {
    if (scanTimer !== null) {
      Log.w(TAG, "Requested to start periodic scanning while this was already enabled")
      return
    }
    scanTimer = Timer()

    scanTimer?.schedule(
        object : TimerTask() {
          override fun run() {
            scanForDevices()
          }
        },
        0,
        SCAN_INTERVAL)
  }

  fun stopPeriodicScanning() {
    scanTimer?.cancel()
    scanTimer = null
    scanDisposable?.dispose()
    scanDisposable = null
  }

  private fun getStreamSettings(deviceId: String, dataType: PolarDeviceDataType) =
      when (dataType) {
        PolarDeviceDataType.ECG,
        PolarDeviceDataType.ACC,
        PolarDeviceDataType.GYRO,
        PolarDeviceDataType.MAGNETOMETER,
        PolarDeviceDataType.PPG -> {
          Log.d(TAG, "Getting stream settings for $dataType")
          api.requestStreamSettings(deviceId, dataType).flatMap { availableSettings ->
            api.requestFullStreamSettings(deviceId, dataType)
                .onErrorReturn { PolarSensorSetting(emptyMap()) }
                .map { allSettings -> Pair(availableSettings, allSettings) }
          }
        }
        else -> Single.just(Pair(PolarSensorSetting(emptyMap()), PolarSensorSetting(emptyMap())))
      }

  private fun getAvailableOnlineStreamDataTypes(deviceId: String) =
      api.getAvailableOnlineStreamDataTypes(deviceId)

  private suspend fun getTime(deviceId: String): Calendar {
    return withContext(Dispatchers.IO) { api.getLocalTime(deviceId).await() }
  }

  suspend fun setTime(deviceId: String, calendar: Calendar): PolarApiResult<Nothing> =
      withContext(Dispatchers.IO) {
        logViewModel.addLogMessage("Setting time for $deviceId to ${calendar.time}")
        return@withContext try {
          api.setLocalTime(deviceId, calendar).await()
          logViewModel.addLogSuccess("Setting time for $deviceId succeeded")
          PolarApiResult.Success()
        } catch (e: Exception) {
          logViewModel.addLogError("Setting time of $deviceId failed: ${e.message}")
          PolarApiResult.Failure("Set time failed", e)
        }
      }

  private suspend fun getSdkMode(deviceId: String): Boolean {
    return withContext(Dispatchers.IO) { api.isSDKModeEnabled(deviceId).await() }
  }

  suspend fun setSdkMode(deviceId: String, newSdkMode: Boolean): PolarApiResult<Nothing> =
      withContext(Dispatchers.IO) {
        logViewModel.addLogMessage("Setting sdk mode for $deviceId to $newSdkMode")
        return@withContext try {
          if (newSdkMode) {
            api.enableSDKMode(deviceId).await()
          } else {
            api.disableSDKMode(deviceId).await()
          }
          logViewModel.addLogSuccess("Setting sdk mode for $deviceId succeeded")
          PolarApiResult.Success()
        } catch (e: Exception) {
          logViewModel.addLogError("Setting sdk mode of $deviceId failed: ${e.message}")
          PolarApiResult.Failure("Set sdk mode failed", e)
        }
      }

  fun cleanup() {
    stopPeriodicScanning()
    disposables.clear()
    api.cleanup()
  }

  fun startStreaming(
      deviceId: String,
      dataType: PolarDeviceDataType,
      sensorSettings: PolarSensorSetting
  ): Flowable<*> {
    return when (dataType) {
      PolarDeviceDataType.HR -> api.startHrStreaming(deviceId)
      PolarDeviceDataType.PPI -> api.startPpiStreaming(deviceId)
      PolarDeviceDataType.ACC -> api.startAccStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.PPG -> api.startPpgStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.ECG -> api.startEcgStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.GYRO -> api.startGyroStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.TEMPERATURE -> api.startTemperatureStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.MAGNETOMETER -> api.startMagnetometerStreaming(deviceId, sensorSettings)
      else -> throw IllegalArgumentException("Unsupported data type: $dataType")
    }
  }

  fun getSdkVersion(): String {
    return PolarBleApiDefaultImpl.versionInfo()
  }
}
