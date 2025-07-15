package com.wboelens.polarrecorder.viewModels

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting

enum class ConnectionState {
  DISCONNECTED,
  DISCONNECTING,
  CONNECTING,
  FETCHING_CAPABILITIES,
  FETCHING_SETTINGS,
  CONNECTED,
  FAILED,
  NOT_CONNECTABLE
}

class DeviceViewModel : ViewModel() {
  data class Device(
      val info: PolarDeviceInfo,
      val isSelected: Boolean = false,
      val connectionState: ConnectionState,
      val dataTypes: Set<PolarDeviceDataType> = emptySet(),
      val sensorSettings: Map<PolarDeviceDataType, PolarSensorSetting> = emptyMap(),
      val firmwareVersion: String? = null
  )

  private val _devices = MutableLiveData<List<Device>>(emptyList())

  val allDevices: LiveData<List<Device>> = _devices

  val selectedDevices: LiveData<List<Device>> =
      _devices.map { devices -> devices.filter { device -> device.isSelected } }

  val connectedDevices: LiveData<List<Device>> =
      _devices.map { devices ->
        devices.filter { device -> device.connectionState == ConnectionState.CONNECTED }
      }

  private val _batteryLevels = mutableStateMapOf<String, Int>()
  val batteryLevels: LiveData<Map<String, Int>> = MutableLiveData(_batteryLevels)

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
      sensorSettings: Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>
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
      dataType: PolarDeviceDataType
  ): PolarSensorSetting {
    return _devices.value?.find { it.info.deviceId == deviceId }?.sensorSettings?.get(dataType)
        ?: PolarSensorSetting(emptyMap())
  }

  fun updateBatteryLevel(deviceId: String, level: Int) {
    _batteryLevels[deviceId] = level
  }
}
