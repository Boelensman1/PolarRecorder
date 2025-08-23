package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.wboelens.polarrecorder.viewModels.ConnectionState
import com.wboelens.polarrecorder.viewModels.Device

@Composable
fun SelectedDevicesSection(
    selectedDevices: List<Device>,
    lastDataTimestamps: Map<String, Long>,
    batteryLevels: Map<String, Int>,
    lastData: Map<String, Map<PolarDeviceDataType, Float?>>,
) {
  Text(
      "Devices:",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(bottom = 8.dp),
  )

  selectedDevices.forEach { device ->
    val lastTimestamp = lastDataTimestamps[device.info.deviceId]
    val timeSinceLastData = lastTimestamp?.let { System.currentTimeMillis() - it }
    val batteryLevel = batteryLevels[device.info.deviceId]
    val connected = device.connectionState == ConnectionState.CONNECTED

    val deviceLastData = lastData[device.info.deviceId] ?: emptyMap()

    DeviceStatusCard(
        deviceName = device.info.name,
        connected = connected,
        timeSinceLastData = timeSinceLastData,
        lastTimestamp = lastTimestamp,
        batteryLevel = batteryLevel,
        deviceLastData = deviceLastData,
    )
  }
}
