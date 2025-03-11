package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.ui.theme.LocalExtendedColorScheme
import com.wboelens.polarrecorder.viewModels.ConnectionState
import com.wboelens.polarrecorder.viewModels.DeviceViewModel

private const val STALLED_AFTER = 5000
private const val WARNING_AFTER = 2000

@Composable
fun SelectedDevicesSection(
    selectedDevices: List<DeviceViewModel.Device>,
    lastDataTimestamps: Map<String, Long>,
    batteryLevels: Map<String, Int>
) {
  val extendedColorScheme = LocalExtendedColorScheme.current

  Text(
      "Selected Devices:",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(bottom = 8.dp))

  selectedDevices.forEach { device ->
    val lastTimestamp = lastDataTimestamps[device.info.deviceId]
    val timeSinceLastData = lastTimestamp?.let { System.currentTimeMillis() - it }
    val batteryLevel = batteryLevels[device.info.deviceId]
    val connected = device.connectionState == ConnectionState.CONNECTED

    val statusColor =
        when {
          !connected -> MaterialTheme.colorScheme.error
          timeSinceLastData == null -> MaterialTheme.colorScheme.error
          timeSinceLastData > STALLED_AFTER -> MaterialTheme.colorScheme.error
          timeSinceLastData > WARNING_AFTER -> extendedColorScheme.warning.onColor
          else -> MaterialTheme.colorScheme.primary
        }

    DeviceStatusCard(
        deviceName = device.info.name,
        connected = connected,
        timeSinceLastData = timeSinceLastData,
        lastTimestamp = lastTimestamp,
        batteryLevel = batteryLevel,
        statusColor = statusColor)
  }
}
