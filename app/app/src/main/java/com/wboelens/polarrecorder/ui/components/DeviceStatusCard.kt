package com.wboelens.polarrecorder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import java.util.Locale

private const val STALLED_AFTER = 5000

@Composable
fun DeviceStatusCard(
    deviceName: String,
    connected: Boolean,
    timeSinceLastData: Long?,
    lastTimestamp: Long?,
    batteryLevel: Int?,
    deviceLastData: Map<PolarDeviceDataType, Float?>,
) {
  var isExpanded by remember { mutableStateOf(false) }

  val statusColor =
      when {
        !connected -> MaterialTheme.colorScheme.error
        timeSinceLastData == null -> MaterialTheme.colorScheme.error
        timeSinceLastData > STALLED_AFTER -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
      }

  Card(
      modifier =
          Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { isExpanded = !isExpanded },
      colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Collapsed state - always visible
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(deviceName, style = MaterialTheme.typography.titleSmall, color = statusColor)
          Spacer(modifier = Modifier.height(4.dp))
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
                imageVector =
                    when {
                      !connected -> Icons.Default.Error
                      timeSinceLastData == null -> Icons.Default.Error
                      timeSinceLastData > STALLED_AFTER -> Icons.Default.Warning
                      else -> Icons.Default.CheckCircle
                    },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp),
            )

            Text(
                when {
                  !connected -> "Disconnected"
                  timeSinceLastData == null -> "No data received"
                  timeSinceLastData > STALLED_AFTER -> "Data stalled"
                  else ->
                      lastTimestamp?.let { "Last data received: ${formatRelativeTime(it)}" }
                          ?: "Receiving data"
                },
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
          }
          if (batteryLevel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
              Icon(
                  imageVector = getBatteryIcon(batteryLevel),
                  contentDescription = null,
                  tint = statusColor,
                  modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.size(4.dp))
              Text(
                  "Battery: $batteryLevel%",
                  style = MaterialTheme.typography.bodySmall,
                  color = statusColor,
              )
            }
          }
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = statusColor.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
      }

      // Expanded state - animated visibility
      AnimatedVisibility(
          visible = isExpanded,
          enter = expandVertically(animationSpec = tween(300)),
          exit = shrinkVertically(animationSpec = tween(300)),
      ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {

          // Data chips section
          if (deviceLastData.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
              Text(
                  "Live Data Preview:",
                  style = MaterialTheme.typography.bodySmall,
                  color = statusColor.copy(alpha = 0.8f),
              )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              deviceLastData.forEach { (type, value) ->
                SensorDataChip(type = type, value = value, color = statusColor)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SensorDataChip(type: PolarDeviceDataType, value: Float?, color: Color) {
  Card(
      colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
      modifier = Modifier.padding(0.dp),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Icon(
          imageVector = getDataTypeIcon(type),
          contentDescription = null,
          tint = color,
          modifier = Modifier.size(14.dp),
      )
      Text(
          "${type.name}: ${value?.let { String.format(Locale.US, "%.1f", it) } ?: "-"}",
          style = MaterialTheme.typography.bodySmall,
          color = color,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun getBatteryIcon(batteryLevel: Int): ImageVector {
  return when {
    batteryLevel >= 90 -> Icons.Default.BatteryFull
    batteryLevel >= 75 -> Icons.Default.Battery6Bar
    batteryLevel >= 60 -> Icons.Default.Battery5Bar
    batteryLevel >= 45 -> Icons.Default.Battery4Bar
    batteryLevel >= 30 -> Icons.Default.Battery3Bar
    batteryLevel >= 15 -> Icons.Default.Battery2Bar
    batteryLevel >= 5 -> Icons.Default.Battery1Bar
    else -> Icons.Default.Battery0Bar
  }
}

private fun getDataTypeIcon(type: PolarDeviceDataType): ImageVector {
  return when (type) {
    PolarDeviceDataType.HR -> Icons.Default.Favorite
    PolarDeviceDataType.ACC -> Icons.Default.Sensors
    PolarDeviceDataType.GYRO -> Icons.Default.Sensors
    PolarDeviceDataType.MAGNETOMETER -> Icons.Default.Sensors
    PolarDeviceDataType.PPG -> Icons.Default.Favorite
    PolarDeviceDataType.PPI -> Icons.Default.Favorite
    PolarDeviceDataType.ECG -> Icons.Default.Favorite
    else -> Icons.Default.FitnessCenter
  }
}

private fun formatRelativeTime(timestamp: Long): String {
  val now = System.currentTimeMillis()
  val diff = now - timestamp

  return when {
    diff < 1000 -> "Just now"
    diff < 60000 -> "${diff / 1000}s ago"
    diff < 3600000 -> "${diff / 60000}m ago"
    diff < 86400000 -> "${diff / 3600000}h ago"
    else -> java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(timestamp)
  }
}
