package com.wboelens.polarrecorder.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.state.ConnectionState
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.state.LogState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext

data class DeviceStreamCapabilities(
    val availableTypes: Set<PolarDeviceDataType>,
    val settings: Map<PolarDeviceDataType, Pair<PolarSensorSetting, PolarSensorSetting>>,
)

data class PolarDeviceSettings(val deviceTimeOnConnect: Calendar?, val sdkModeEnabled: Boolean?)

sealed class PolarApiResult<out T> {
  data class Success<out R>(val value: R? = null) : PolarApiResult<R>()

  data class Failure(val message: String, val throwable: Throwable?) : PolarApiResult<Nothing>()
}

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class PolarManager(
    private val context: Context,
    private val deviceState: DeviceState,
    private val logState: LogState,
) {
  companion object {
    private const val TAG = "PolarManager"
    private const val SCAN_INTERVAL = 30000L // 30 seconds between scans
    private const val SCAN_DURATION = 10000L // 10 seconds per scan
    private const val MAX_RETRY_ERRORS = 4L
    private const val FEATURE_POLL_MAX_WAIT = 5000L // 5 seconds max wait for features
    private const val FEATURE_POLL_INTERVAL = 500L // Poll every 500ms

    /** Model prefixes where getAvailableOnlineStreamDataTypes() is known to fail. */
    private val FALLBACK_ONLY_MODEL_PREFIXES = setOf("H7")

    /** Data types that this app knows how to stream and process. */
    val SUPPORTED_DATA_TYPES =
        setOf(
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI,
            PolarDeviceDataType.ACC,
            PolarDeviceDataType.PPG,
            PolarDeviceDataType.ECG,
            PolarDeviceDataType.GYRO,
            PolarDeviceDataType.TEMPERATURE,
            PolarDeviceDataType.SKIN_TEMPERATURE,
            PolarDeviceDataType.MAGNETOMETER,
        )
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
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
        ),
    )
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
  private val deviceModelNumbers = mutableMapOf<String, String>()

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
            logState.addLogMessage("Fetching capabilities for device ${polarDeviceInfo.deviceId}")
            deviceState.updateConnectionState(
                polarDeviceInfo.deviceId,
                ConnectionState.FETCHING_CAPABILITIES,
            )

            val disposable =
                Single.just(Unit)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ _ -> // Explicitly using Consumer<Unit> overload
                      MainScope().launch {
                        // Poll for feature readiness
                        var waitTime = 0L
                        val maxWaitTime = FEATURE_POLL_MAX_WAIT
                        val pollInterval = FEATURE_POLL_INTERVAL

                        while (waitTime < maxWaitTime) {
                          kotlinx.coroutines.delay(pollInterval)
                          waitTime += pollInterval

                          val readyFeatures = deviceFeatureReadiness[polarDeviceInfo.deviceId]
                          if (readyFeatures?.contains(
                              PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO) == true ||
                              readyFeatures?.contains(
                                  PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) ==
                                  true) {
                            break
                          }
                        }

                        var capabilities: DeviceStreamCapabilities?
                        try {
                          capabilities = fetchDeviceCapabilities(polarDeviceInfo.deviceId).await()
                        } catch (error: Throwable) {
                          Log.e(TAG, "Failed to fetch device capabilities", error)
                          logState.addLogError(
                              "Failed to fetch device capabilities for " +
                                  "${polarDeviceInfo.deviceId} (${error}), " +
                                  "falling back to alternative method",
                              false,
                          )
                          capabilities =
                              fetchDeviceCapabilitiesViaFallback(polarDeviceInfo.deviceId)
                        }

                        logState.addLogMessage(
                            "Fetching settings for device ${polarDeviceInfo.deviceId}")
                        deviceState.updateConnectionState(
                            polarDeviceInfo.deviceId,
                            ConnectionState.FETCHING_SETTINGS,
                        )

                        val settings = fetchDeviceSettings(polarDeviceInfo.deviceId).await()
                        if (capabilities !== null && capabilities.availableTypes.isNotEmpty()) {
                          finishConnectDevice(polarDeviceInfo, capabilities, settings)
                        } else {
                          // alternate method also failed, disconnect
                          deviceState.updateConnectionState(
                              polarDeviceInfo.deviceId,
                              ConnectionState.FAILED,
                          )
                          logState.addLogMessage(
                              "Failed to connect to device, could not fetch capabilities.")
                          api.disconnectFromDevice(polarDeviceInfo.deviceId)
                        }
                      }
                    })
            disposables.add(disposable)
          }

          override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
            deviceState.updateConnectionState(
                polarDeviceInfo.deviceId,
                ConnectionState.CONNECTING,
            )
            logState.addLogMessage("Connecting to device ${polarDeviceInfo.deviceId}")
          }

          override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            deviceCapabilities.remove(polarDeviceInfo.deviceId)
            deviceModelNumbers.remove(polarDeviceInfo.deviceId)
            if (deviceState.getConnectionState(polarDeviceInfo.deviceId) ===
                ConnectionState.DISCONNECTING) {
              // a disconnect was requested, so this disconnect is expected
              logState.addLogMessage("Device ${polarDeviceInfo.deviceId} disconnected")
            } else {
              logState.addLogError("Device ${polarDeviceInfo.deviceId} disconnected")
            }

            deviceState.updateConnectionState(
                polarDeviceInfo.deviceId,
                ConnectionState.DISCONNECTED,
            )
          }

          override fun bleSdkFeatureReady(
              identifier: String,
              feature: PolarBleApi.PolarBleSdkFeature,
          ) {
            Log.d(TAG, "Feature $feature ready for device $identifier")
            deviceFeatureReadiness.getOrPut(identifier) { mutableSetOf() }.add(feature)
          }

          override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
            when (uuid) {
              BleDisClient.SOFTWARE_REVISION_STRING -> {
                logState.addLogMessage(
                    "DIS info received for device $identifier: [FirmwareVersion]: $value")
                deviceState.updateFirmwareVersion(identifier, value)
              }
              BleDisClient.FIRMWARE_REVISION_STRING -> {
                logState.addLogMessage(
                    "DIS info received for device $identifier: [FirmwareRevision]: $value")
              }
              BleDisClient.HARDWARE_REVISION_STRING -> {
                logState.addLogMessage(
                    "DIS info received for device $identifier: [HardwareRevision]: $value")
              }
              BleDisClient.MODEL_NUMBER_STRING -> {
                logState.addLogMessage(
                    "DIS info received for device $identifier: [ModelNumber]: $value")
                deviceModelNumbers[identifier] = value
              }
              BleDisClient.SERIAL_NUMBER_STRING -> {
                logState.addLogMessage(
                    "DIS info received for device $identifier: [SerialNumber]: $value")
              }
              BleDisClient.MANUFACTURER_NAME_STRING -> {
                logState.addLogMessage(
                    "DIS info received for device $identifier: [ManufacturerName]: $value")
              }
              else -> {
                Log.d(TAG, "DIS info received for device $identifier: [$uuid]: $value")
              }
            }
          }

          override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            Log.d(TAG, "DIS info 2 received for device $identifier: $disInfo")
          }

          override fun htsNotificationReceived(
              identifier: String,
              data: PolarHealthThermometerData,
          ) {
            Log.d(TAG, "PolarHealthThermometer Data info received for device $identifier: $data")
          }

          override fun batteryLevelReceived(identifier: String, level: Int) {
            Log.d(TAG, "Battery level for device $identifier: $level")
            deviceBatteryLevels[identifier] = level
            deviceState.updateBatteryLevel(identifier, level)
          }
        })
  }

  private fun fetchDeviceCapabilities(deviceId: String): Single<DeviceStreamCapabilities> {
    return Single.create { emitter ->
          // Check if FEATURE_DEVICE_INFO is available
          if (isFeatureAvailable(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)) {
            emitter.onSuccess(Unit)
          } else {
            emitter.onError(IllegalStateException("Device info feature not ready"))
          }
        }
        .flatMap { getAvailableOnlineStreamDataTypes(deviceId) }
        .retryWhen { errors ->
          errors
              .zipWith(Flowable.range(1, MAX_RETRY_ERRORS.toInt())) { error, attempt ->
                Pair(error, attempt)
              }
              .flatMap { (error, attempt) ->
                // Check if model number is now available and indicates fallback-only device
                if (shouldUseFallbackCapabilities(deviceId)) {
                  logState.addLogMessage(
                      "Model number received indicates fallback-only device, skipping retries")
                  return@flatMap Flowable.error<Long>(error)
                }

                logState.addLogError(
                    "Failed to fetch stream capabilities (attempt $attempt/$MAX_RETRY_ERRORS): ${error.message}",
                    false,
                )
                // Wait 2 seconds before retrying
                Flowable.timer(2, TimeUnit.SECONDS)
              }
        }
        .flatMap { types ->
          // Filter out unsupported data types to prevent crashes
          val supportedTypes = types.filter { it in SUPPORTED_DATA_TYPES }
          val unsupportedTypes = types.filterNot { it in SUPPORTED_DATA_TYPES }
          if (unsupportedTypes.isNotEmpty()) {
            logState.addLogMessage(
                "Ignoring unsupported data types: ${unsupportedTypes.joinToString { it.name }}",
                withSnackbar = true,
            )
          }

          val settingsRequests =
              supportedTypes.map { dataType ->
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
                              triple.third as PolarSensorSetting,
                          )
                    }
            DeviceStreamCapabilities(supportedTypes.toSet(), settings)
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
            deviceId,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
        )) {
          try {
            deviceTime = getTime(deviceId)
          } catch (e: Exception) {
            logState.addLogError("Failed to fetch device time (${e.message})", false)
          }
        }

        if (isFeatureAvailable(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)) {
          try {
            deviceSdkMode = getSdkMode(deviceId)
          } catch (e: Exception) {
            logState.addLogError("Failed to fetch device sdk mode (${e.message})", false)
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
      settings: PolarDeviceSettings,
  ) {
    deviceCapabilities[polarDeviceInfo.deviceId] = capabilities
    deviceSettings[polarDeviceInfo.deviceId] = settings
    logState.addLogMessage(
        "Device ${polarDeviceInfo.deviceId} Connected",
    )
    deviceState.updateConnectionState(polarDeviceInfo.deviceId, ConnectionState.CONNECTED)
  }

  fun getDeviceCapabilities(deviceId: String): DeviceStreamCapabilities? {
    return deviceCapabilities[deviceId]
  }

  fun getDeviceSettings(deviceId: String): PolarDeviceSettings? {
    return deviceSettings[deviceId]
  }

  private fun isFeatureAvailable(
      deviceId: String,
      feature: PolarBleApi.PolarBleSdkFeature,
  ): Boolean {
    return deviceFeatureReadiness[deviceId]?.contains(feature) == true
  }

  private fun shouldUseFallbackCapabilities(deviceId: String): Boolean {
    val modelNumber = deviceModelNumbers[deviceId] ?: return false
    return FALLBACK_ONLY_MODEL_PREFIXES.any { prefix ->
      modelNumber.startsWith(prefix, ignoreCase = true)
    }
  }

  fun isTimeManagementAvailable(deviceId: String): Boolean {
    return isFeatureAvailable(
        deviceId,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
    )
  }

  fun connectToDevice(deviceId: String) {
    try {
      api.connectToDevice(deviceId)
    } catch (e: PolarInvalidArgument) {
      Log.e(TAG, "Connection failed: ${e.message}", e)
      deviceState.updateConnectionState(deviceId, ConnectionState.FAILED)
    }
  }

  fun disconnectDevice(deviceId: String) {
    try {
      api.disconnectFromDevice(deviceId)
      deviceState.updateConnectionState(deviceId, ConnectionState.DISCONNECTING)
    } catch (e: PolarInvalidArgument) {
      Log.e(TAG, "Disconnect failed: ${e.message}", e)
    }
  }

  fun disconnectAllDevices() {
    deviceState.connectedDevices.value.forEach { device -> disconnectDevice(device.info.deviceId) }
  }

  fun scanForDevices() {
    Log.d(TAG, "Starting scan")
    _isRefreshing.value = true
    scanDisposable?.dispose()

    scanDisposable =
        api.searchForDevice()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { deviceInfo -> deviceState.addDevice(deviceInfo) },
                { error ->
                  logState.addLogMessage("Scan error: ${error.message}")

                  Log.d(TAG, "Stopping scan")
                  _isRefreshing.value = false
                },
                {
                  Log.d(TAG, "Stopping scan")
                  _isRefreshing.value = false
                },
            )

    Handler(Looper.getMainLooper())
        .postDelayed(
            {
              scanDisposable?.dispose()
              Log.d(TAG, "Stopping scan")
              _isRefreshing.value = false
            },
            SCAN_DURATION,
        )
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
        SCAN_INTERVAL,
    )
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
        PolarDeviceDataType.PPG,
        PolarDeviceDataType.TEMPERATURE,
        PolarDeviceDataType.SKIN_TEMPERATURE -> {
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
        logState.addLogMessage("Setting time for $deviceId to ${calendar.time}")
        return@withContext try {
          api.setLocalTime(deviceId, calendar).await()
          logState.addLogSuccess("Setting time for $deviceId succeeded")
          PolarApiResult.Success()
        } catch (e: Exception) {
          logState.addLogError("Setting time of $deviceId failed: ${e.message}")
          PolarApiResult.Failure("Set time failed", e)
        }
      }

  private suspend fun getSdkMode(deviceId: String): Boolean {
    return withContext(Dispatchers.IO) { api.isSDKModeEnabled(deviceId).await() }
  }

  suspend fun setSdkMode(deviceId: String, newSdkMode: Boolean): PolarApiResult<Nothing> =
      withContext(Dispatchers.IO) {
        logState.addLogMessage("Setting sdk mode for $deviceId to $newSdkMode")
        return@withContext try {
          if (newSdkMode) {
            api.enableSDKMode(deviceId).await()
          } else {
            api.disableSDKMode(deviceId).await()
          }
          logState.addLogSuccess("Setting sdk mode for $deviceId succeeded")
          PolarApiResult.Success()
        } catch (e: Exception) {
          logState.addLogError("Setting sdk mode of $deviceId failed: ${e.message}")
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
      sensorSettings: PolarSensorSetting,
  ): Flowable<*> {
    return when (dataType) {
      PolarDeviceDataType.HR -> api.startHrStreaming(deviceId)
      PolarDeviceDataType.PPI -> api.startPpiStreaming(deviceId)
      PolarDeviceDataType.ACC -> api.startAccStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.PPG -> api.startPpgStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.ECG -> api.startEcgStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.GYRO -> api.startGyroStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.TEMPERATURE -> api.startTemperatureStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.SKIN_TEMPERATURE ->
          api.startSkinTemperatureStreaming(deviceId, sensorSettings)
      PolarDeviceDataType.MAGNETOMETER -> api.startMagnetometerStreaming(deviceId, sensorSettings)
      else -> throw IllegalArgumentException("Unsupported data type: $dataType")
    }
  }

  fun getSdkVersion(): String {
    return PolarBleApiDefaultImpl.versionInfo()
  }
}
