package com.wboelens.polarrecorder.state

import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ConnectionState {
  DISCONNECTED,
  DISCONNECTING,
  CONNECTING,
  FETCHING_CAPABILITIES,
  FETCHING_SETTINGS,
  CONNECTED,
  FAILED,
  NOT_CONNECTABLE,
}

data class Device(
    val info: PolarDeviceInfo,
    val isSelected: Boolean = false,
    val connectionState: ConnectionState,
    val dataTypes: Set<PolarDeviceDataType> = emptySet(),
    val sensorSettings: Map<PolarDeviceDataType, PolarSensorSetting> = emptyMap(),
    val firmwareVersion: String? = null,
)

/**
 * Application-scoped state holder for device information. This class survives Activity restarts and
 * holds the source of truth for device state. ViewModels delegate to this class.
 */
class DeviceState {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _devices = MutableStateFlow<List<Device>>(emptyList())
  val allDevices: StateFlow<List<Device>> = _devices.asStateFlow()

  val selectedDevices: StateFlow<List<Device>> =
      _devices
          .map { devices -> devices.filter { it.isSelected } }
          .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

  val connectedDevices: StateFlow<List<Device>> =
      _devices
          .map { devices -> devices.filter { it.connectionState == ConnectionState.CONNECTED } }
          .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _batteryLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
  val batteryLevels: StateFlow<Map<String, Int>> = _batteryLevels.asStateFlow()

  fun addDevice(device: PolarDeviceInfo) {
    val currentDevices = _devices.value.toMutableList()
    if (currentDevices.none { it.info.deviceId == device.deviceId }) {
      val connectionState =
          if (device.isConnectable) ConnectionState.DISCONNECTED
          else ConnectionState.NOT_CONNECTABLE
      currentDevices.add(Device(info = device, connectionState = connectionState))
      _devices.value = currentDevices
    }
  }

  private fun updateDevice(deviceId: String, update: (Device) -> Device) {
    val currentDevices = _devices.value.toMutableList()
    val deviceIndex = currentDevices.indexOfFirst { it.info.deviceId == deviceId }

    if (deviceIndex != -1) {
      currentDevices[deviceIndex] = update(currentDevices[deviceIndex])
      _devices.value = currentDevices
    }
  }

  fun updateConnectionState(deviceId: String, state: ConnectionState) {
    updateDevice(deviceId) { device -> device.copy(connectionState = state) }
  }

  fun updateFirmwareVersion(deviceId: String, firmwareVersion: String) {
    updateDevice(deviceId) { device -> device.copy(firmwareVersion = firmwareVersion) }
  }

  fun getConnectionState(deviceId: String): ConnectionState {
    return _devices.value.find { it.info.deviceId == deviceId }?.connectionState
        ?: ConnectionState.NOT_CONNECTABLE
  }

  fun toggleIsSelected(deviceId: String) {
    updateDevice(deviceId) { device -> device.copy(isSelected = !device.isSelected) }
  }

  fun updateDeviceDataTypes(deviceId: String, dataTypes: Set<PolarDeviceDataType>) {
    updateDevice(deviceId) { device -> device.copy(dataTypes = dataTypes) }
  }

  fun updateDeviceSensorSettings(
      deviceId: String,
      sensorSettings: Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
  ) {
    updateDevice(deviceId) { device ->
      val deviceSettings = mutableMapOf<PolarDeviceDataType, PolarSensorSetting>()
      sensorSettings.forEach { (dataType, settings) ->
        deviceSettings[dataType] = PolarSensorSetting(settings)
      }
      device.copy(sensorSettings = deviceSettings)
    }
  }

  fun getDeviceDataTypes(deviceId: String): Set<PolarDeviceDataType> {
    return _devices.value.find { it.info.deviceId == deviceId }?.dataTypes ?: emptySet()
  }

  fun getDeviceSensorSettingsForDataType(
      deviceId: String,
      dataType: PolarDeviceDataType,
  ): PolarSensorSetting {
    return _devices.value.find { it.info.deviceId == deviceId }?.sensorSettings?.get(dataType)
        ?: PolarSensorSetting(emptyMap())
  }

  fun updateBatteryLevel(deviceId: String, level: Int) {
    _batteryLevels.value = _batteryLevels.value + (deviceId to level)
  }

  fun cleanup() {
    scope.cancel()
  }
}
