package com.wboelens.polarrecorder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.ui.components.CheckboxWithLabel
import com.wboelens.polarrecorder.ui.components.SaveToOptions
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(
    deviceViewModel: DeviceViewModel,
    fileSystemSettingsViewModel: FileSystemSettingsViewModel,
    recordingManager: RecordingManager,
    dataSavers: DataSavers,
    preferencesManager: PreferencesManager,
    onBackPressed: () -> Unit,
    onContinue: () -> Unit
) {
  val connectedDevices = deviceViewModel.connectedDevices.observeAsState(emptySet()).value
  var recordingName by remember { mutableStateOf(preferencesManager.recordingName) }
  var appendTimestamp by remember {
    mutableStateOf(preferencesManager.recordingNameAppendTimestamp)
  }

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Recording Settings") },
              navigationIcon = {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Back") }
              })
        },
    ) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
        SaveToOptions(
            dataSavers = dataSavers,
            preferencesManager = preferencesManager,
            fileSystemSettingsViewModel = fileSystemSettingsViewModel)

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = recordingName,
            onValueChange = {
              recordingName = it
              preferencesManager.recordingName = it
            },
            label = { Text("Recording Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = recordingName.isEmpty(),
            supportingText = {
              if (recordingName.isEmpty()) {
                Text("Recording name is required", color = MaterialTheme.colorScheme.error)
              }
            })

        CheckboxWithLabel(
            label = "Add timestamp to recording name",
            checked = appendTimestamp,
            fullWidth = true,
            onCheckedChange = {
              appendTimestamp = it
              preferencesManager.recordingNameAppendTimestamp = it
            })

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
              recordingManager.configure(recordingName, appendTimestamp)
              recordingManager.startRecording()
              onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = connectedDevices.isNotEmpty() && recordingName.isNotEmpty()) {
              Text("Start Recording")
            }
      }
    }
  }
}
