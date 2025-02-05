package com.wboelens.polarrecorder.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.ui.LogView
import com.wboelens.polarrecorder.ui.theme.LocalExtendedColorScheme
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel

private const val STALLED_AFTER = 5000
private const val WARNING_AFTER = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
fun RecordingScreen(
    deviceViewModel: DeviceViewModel,
    logViewModel: LogViewModel,
    recordingManager: RecordingManager,
    dataSavers: DataSavers,
    onBackPressed: () -> Unit
) {
  val context = LocalContext.current
  val extendedColorScheme = LocalExtendedColorScheme.current

  val isRecording by recordingManager.isRecording.collectAsState(initial = false)
  val connectedDevices = deviceViewModel.connectedDevices.observeAsState(emptyList()).value
  val lastDataTimestamps by recordingManager.lastDataTimestamps.collectAsState()

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Recording") },
              navigationIcon = {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Back") }
              })
        }) { paddingValues ->
          Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Button(
                onClick = {
                  if (isRecording) recordingManager.stopRecording()
                  else recordingManager.startRecording()
                },
                modifier = Modifier.fillMaxWidth()) {
                  Text(if (isRecording) "Stop Recording" else "Start Recording")
                }

            if (!isRecording && dataSavers.fileSystem.isEnabled.value) {
              @Suppress("SwallowedException")
              dataSavers.fileSystem.recordingDir?.let { dir ->
                Button(
                    onClick = {
                      val intent = Intent(Intent.ACTION_VIEW).apply { data = dir.uri }
                      try {
                        context.startActivity(intent)
                      } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No file explorer app found", Toast.LENGTH_SHORT)
                            .show()
                      }
                    },
                    modifier = Modifier.fillMaxWidth()) {
                      Text("Open Recording Directory")
                    }
              }
            }

            // Show connected devices and their status
            if (isRecording) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                  "Connected Devices:",
                  style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier.padding(bottom = 8.dp))

              connectedDevices.forEach { device ->
                val lastTimestamp = lastDataTimestamps[device.info.deviceId]
                val timeSinceLastData =
                    if (lastTimestamp != null) System.currentTimeMillis() - lastTimestamp else null

                val statusColor =
                    when {
                      timeSinceLastData == null -> MaterialTheme.colorScheme.error
                      timeSinceLastData > STALLED_AFTER -> MaterialTheme.colorScheme.error
                      timeSinceLastData > WARNING_AFTER -> extendedColorScheme.warning.onColor
                      else -> MaterialTheme.colorScheme.primary
                    }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors =
                        CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))) {
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
                              if (lastTimestamp != null) {
                                val lastTimestampFormatted =
                                    java.text.DateFormat.getTimeInstance(
                                            java.text.DateFormat.MEDIUM)
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
            }

            Spacer(modifier = Modifier.height(16.dp))
            LogView(logViewModel.logMessages.observeAsState(emptyList()).value)
          }
        }
  }
}
