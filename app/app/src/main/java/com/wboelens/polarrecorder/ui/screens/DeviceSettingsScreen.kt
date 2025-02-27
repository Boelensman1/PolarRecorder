package com.wboelens.polarrecorder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.ui.dialogs.DeviceSettingsDialog
import com.wboelens.polarrecorder.viewModels.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    deviceViewModel: DeviceViewModel,
    polarManager: PolarManager,
    onBackPressed: () -> Unit,
    onContinue: () -> Unit
) {
  val connectedDevices by deviceViewModel.connectedDevices.observeAsState(emptyList())
  var currentDeviceIndex by remember { mutableIntStateOf(0) }
  var showSettingsDialog by remember { mutableStateOf(false) }

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Device Settings") },
              navigationIcon = {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Back") }
              })
        }) { paddingValues ->
          Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(
                text = "Configure device settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp))

            connectedDevices.forEachIndexed { index, device ->
              Card(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                  onClick = {
                    currentDeviceIndex = index
                    showSettingsDialog = true
                  }) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                          Column {
                            Text(
                                text = device.info.name,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                text =
                                    if (device.dataTypes.isEmpty()) "Click to configure settings"
                                    else "Device configured",
                                style = MaterialTheme.typography.bodyMedium)
                          }
                          Icon(
                              imageVector =
                                  if (device.dataTypes.isEmpty()) Icons.Default.ArrowForward
                                  else Icons.Default.CheckCircle,
                              contentDescription =
                                  if (device.dataTypes.isEmpty()) "Configure device"
                                  else "Device configured",
                              tint =
                                  if (device.dataTypes.isEmpty()) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.secondary)
                        }
                  }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onContinue,
                enabled =
                    connectedDevices.isNotEmpty() &&
                        connectedDevices.all { it.dataTypes.isNotEmpty() },
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                  Text("Continue")
                }
          }
        }
  }

  if (showSettingsDialog && currentDeviceIndex < connectedDevices.size) {
    val device = connectedDevices[currentDeviceIndex]
    DeviceSettingsDialog(
        deviceId = device.info.deviceId,
        polarManager = polarManager,
        onDismiss = { showSettingsDialog = false },
        onSettingsSelected = { settings, dataTypes ->
          deviceViewModel.updateDeviceSensorSettings(device.info.deviceId, settings)
          deviceViewModel.updateDeviceDataTypes(device.info.deviceId, dataTypes)
        },
        initialSettings = device.sensorSettings,
        initialDataTypes = device.dataTypes)
  }
}
