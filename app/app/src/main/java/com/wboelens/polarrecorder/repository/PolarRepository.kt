package com.wboelens.polarrecorder.repository

import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.viewModels.ConnectionState
import com.wboelens.polarrecorder.viewModels.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PolarRepository() : BaseRepository() {

  private val _devices = MutableStateFlow<List<Device>>(emptyList())

  val allDevices: StateFlow<List<Device>> = _devices

  val selectedDevices: StateFlow<List<Device>> =
      _devices
          .map { devices -> devices.filter { device -> device.isSelected } }
          .stateIn(
              scope = repositoryScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = emptyList(),
          )

  val connectedDevices: StateFlow<List<Device>> =
      _devices
          .map { devices ->
            devices.filter { device -> device.connectionState == ConnectionState.CONNECTED }
          }
          .stateIn(
              scope = repositoryScope,
              started = SharingStarted.WhileSubscribed(5000),
              initialValue = emptyList(),
          )

  private val _batteryLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
  val batteryLevels: StateFlow<Map<String, Int>> = _batteryLevels.asStateFlow()

  fun addDevice(device: PolarDeviceInfo) {
    val currentDevices = _devices.value?.toMutableList() ?: mutableListOf()
    if (currentDevices.none { it.info.deviceId == device.deviceId }) {
      val connectionState =
          if (device.isConnectable) ConnectionState.DISCONNECTED
          else ConnectionState.NOT_CONNECTABLE
      currentDevices.add(Device(info = device, connectionState = connectionState))
      _devices.value = currentDevices
    }
  }

  private fun updateDevice(deviceId: String, update: (Device) -> Device) {
    val currentDevices = _devices.value?.toMutableList() ?: mutableListOf()
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
    return _devices.value?.find { it.info.deviceId == deviceId }?.connectionState
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
    return _devices.value?.find { it.info.deviceId == deviceId }?.dataTypes ?: emptySet()
  }

  fun getDeviceSensorSettingsForDataType(
      deviceId: String,
      dataType: PolarDeviceDataType,
  ): PolarSensorSetting {
    return _devices.value?.find { it.info.deviceId == deviceId }?.sensorSettings?.get(dataType)
        ?: PolarSensorSetting(emptyMap())
  }

  fun updateBatteryLevel(deviceId: String, level: Int) {
    val current = _batteryLevels.value
    val changed = current.toMutableMap().also { it[deviceId] = level }
    _batteryLevels.value = changed
  }
}
