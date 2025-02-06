package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.viewModels.DeviceViewModel

private const val STALLED_AFTER = 5000
private const val WARNING_AFTER = 2000

@Composable
fun DeviceStatusCard(
    device: DeviceViewModel.Device,
    timeSinceLastData: Long?,
    lastTimestamp: Long?,
    batteryLevel: Int?,
    statusColor: Color
) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.info.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = statusColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    when {
                      timeSinceLastData == null -> "No data received"
                      timeSinceLastData > STALLED_AFTER -> "Data stalled"
                      else -> "Receiving data"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor)
                if (batteryLevel != null) {
                  Text(
                      "Battery: $batteryLevel%",
                      style = MaterialTheme.typography.bodySmall,
                      color = statusColor.copy(alpha = 0.7f))
                }
                if (lastTimestamp != null) {
                  val lastTimestampFormatted =
                      java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                          .format(lastTimestamp)
                  Text(
                      "Last update: $lastTimestampFormatted",
                      style = MaterialTheme.typography.bodySmall,
                      color = statusColor.copy(alpha = 0.7f))
                }
              }
              Icon(
                  imageVector =
                      when {
                        timeSinceLastData == null -> Icons.Default.Error
                        timeSinceLastData > STALLED_AFTER -> Icons.Default.Error
                        timeSinceLastData > WARNING_AFTER -> Icons.Default.Warning
                        else -> Icons.Default.CheckCircle
                      },
                  contentDescription = null,
                  tint = statusColor,
                  modifier = Modifier.size(24.dp))
            }
      }
}
