package com.wboelens.polarrecorder.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.state.ConnectionState
import com.wboelens.polarrecorder.state.Device
import com.wboelens.polarrecorder.state.DeviceState

/**
 * ViewModel that delegates to an Application-scoped DeviceState. This allows the UI to observe
 * device state via LiveData while the actual state survives Activity restarts.
 */
class DeviceViewModel(private val deviceState: DeviceState) : ViewModel() {

  val allDevices: LiveData<List<Device>> = deviceState.allDevices.asLiveData()

  val selectedDevices: LiveData<List<Device>> = deviceState.selectedDevices.asLiveData()

  val connectedDevices: LiveData<List<Device>> = deviceState.connectedDevices.asLiveData()

  val batteryLevels: LiveData<Map<String, Int>> = deviceState.batteryLevels.asLiveData()

  fun addDevice(device: PolarDeviceInfo) = deviceState.addDevice(device)

  fun updateConnectionState(deviceId: String, state: ConnectionState) =
      deviceState.updateConnectionState(deviceId, state)

  fun updateFirmwareVersion(deviceId: String, firmwareVersion: String) =
      deviceState.updateFirmwareVersion(deviceId, firmwareVersion)

  fun getConnectionState(deviceId: String): ConnectionState =
      deviceState.getConnectionState(deviceId)

  fun toggleIsSelected(deviceId: String) = deviceState.toggleIsSelected(deviceId)

  fun updateDeviceDataTypes(deviceId: String, dataTypes: Set<PolarDeviceDataType>) =
      deviceState.updateDeviceDataTypes(deviceId, dataTypes)

  fun updateDeviceSensorSettings(
      deviceId: String,
      sensorSettings: Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
  ) = deviceState.updateDeviceSensorSettings(deviceId, sensorSettings)

  fun getDeviceDataTypes(deviceId: String): Set<PolarDeviceDataType> =
      deviceState.getDeviceDataTypes(deviceId)

  fun getDeviceSensorSettingsForDataType(
      deviceId: String,
      dataType: PolarDeviceDataType,
  ): PolarSensorSetting = deviceState.getDeviceSensorSettingsForDataType(deviceId, dataType)

  fun updateBatteryLevel(deviceId: String, level: Int) =
      deviceState.updateBatteryLevel(deviceId, level)
}
