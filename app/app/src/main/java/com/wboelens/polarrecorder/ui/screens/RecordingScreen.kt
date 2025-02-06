package com.wboelens.polarrecorder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.ui.components.ConnectedDevicesSection
import com.wboelens.polarrecorder.ui.components.LogView
import com.wboelens.polarrecorder.ui.components.RecordingControls
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    deviceViewModel: DeviceViewModel,
    logViewModel: LogViewModel,
    recordingManager: RecordingManager,
    dataSavers: DataSavers,
    onBackPressed: () -> Unit
) {
  val isRecording by recordingManager.isRecording.collectAsState(initial = false)
  val connectedDevices = deviceViewModel.connectedDevices.observeAsState(emptyList()).value
  val lastDataTimestamps by recordingManager.lastDataTimestamps.collectAsState()
  val batteryLevels by deviceViewModel.batteryLevels.observeAsState(emptyMap())
  val isFileSystemEnabled by dataSavers.fileSystem.isEnabled.collectAsState()

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
            RecordingControls(
                isRecording = isRecording,
                isFileSystemEnabled = isFileSystemEnabled,
                recordingManager = recordingManager,
                dataSavers = dataSavers)

            if (isRecording) {
              Spacer(modifier = Modifier.height(8.dp))
              ConnectedDevicesSection(
                  connectedDevices = connectedDevices,
                  lastDataTimestamps = lastDataTimestamps,
                  batteryLevels = batteryLevels)
            }

            Spacer(modifier = Modifier.height(16.dp))
            LogView(logViewModel.logMessages.observeAsState(emptyList()).value)
          }
        }
  }
}
