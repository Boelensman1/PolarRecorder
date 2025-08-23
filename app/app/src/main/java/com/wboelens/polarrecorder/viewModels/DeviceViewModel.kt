package com.wboelens.polarrecorder.viewModels

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.repository.PolarRepository

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

class DeviceViewModel : ViewModel() {

  private lateinit var polarRepository: PolarRepository

  private lateinit var _devices: LiveData<List<Device>>

  val allDevices: LiveData<List<Device>>
    get() = _devices

  private lateinit var _selectedDevices: LiveData<List<Device>>

  val selectedDevices: LiveData<List<Device>>
    get() = _selectedDevices

  private lateinit var _connectedDevices: LiveData<List<Device>>

  val connectedDevices: LiveData<List<Device>>
    get() = this._connectedDevices

  private val _batteryLevels = mutableStateMapOf<String, Int>()
  val batteryLevels: LiveData<Map<String, Int>> = MutableLiveData(_batteryLevels)

  fun setup(polarRepository: PolarRepository) {
    this.polarRepository = polarRepository
    this._devices = polarRepository.allDevices.asLiveData()
    this._selectedDevices = polarRepository.selectedDevices.asLiveData()
    this._connectedDevices = polarRepository.connectedDevices.asLiveData()
  }

  fun toggleIsSelected(deviceId: String) {
    polarRepository.toggleIsSelected(deviceId)
  }

  fun updateDeviceDataTypes(deviceId: String, dataTypes: Set<PolarDeviceDataType>) {
    polarRepository.updateDeviceDataTypes(deviceId, dataTypes)
  }

  fun updateDeviceSensorSettings(
      deviceId: String,
      sensorSettings: Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
  ) {
    polarRepository.updateDeviceSensorSettings(deviceId, sensorSettings)
  }

  fun getDeviceDataTypes(deviceId: String): Set<PolarDeviceDataType> {
    return polarRepository.getDeviceDataTypes(deviceId)
  }
}
