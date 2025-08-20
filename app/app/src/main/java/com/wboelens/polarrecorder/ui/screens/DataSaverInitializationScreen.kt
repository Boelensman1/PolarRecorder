package com.wboelens.polarrecorder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.DataSaver
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.viewModels.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSaverInitializationScreen(
  dataSavers: DataSavers,
  deviceViewModel: DeviceViewModel,
  recordingManager: RecordingManager,
  preferencesManager: PreferencesManager,
  onBackPressed: () -> Unit,
  onContinue: () -> Unit
) {
  val selectedDevices by deviceViewModel.selectedDevices.observeAsState(emptyList())
  val enabledSavers = dataSavers.asList().filter { it.isEnabled.collectAsState().value }

  // Create device info map for data savers
  val deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver> =
      selectedDevices.associate { device ->
        val dataTypesWithLog =
            deviceViewModel.getDeviceDataTypes(device.info.deviceId).map { it.name }.toMutableList()
        dataTypesWithLog.add("LOG")

        device.info.deviceId to DeviceInfoForDataSaver(device.info.name, dataTypesWithLog.toSet())
      }

  // Initialize data savers & set recording name
  LaunchedEffect(Unit) {
    // set recording name
    recordingManager.currentRecordingName =
        if (preferencesManager.recordingNameAppendTimestamp) {
          val timestamp =
              java.text
                  .SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                  .format(java.util.Date())
          "${preferencesManager.recordingName}_$timestamp"
        } else preferencesManager.recordingName

    enabledSavers.forEach { saver ->
      saver.initSaving(recordingManager.currentRecordingName, deviceIdsWithInfo)
    }
  }

  // Check if all savers are initialized
  val allInitializationStates =
      enabledSavers.map { it.isInitialized.collectAsState(InitializationState.NOT_STARTED) }

  val allSuccess = allInitializationStates.all { it.value == InitializationState.SUCCESS }

  // Auto-continue when all savers are initialized
  LaunchedEffect(allSuccess) {
    if (allSuccess) {
      recordingManager.startRecording()
      onContinue()
    }
  }

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Initializing Data Savers") },
              navigationIcon = {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Back") }
              },
          )
        },
    ) { paddingValues ->
      Column(modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(16.dp)) {
        enabledSavers.forEach { saver ->
          DataSaverInitializationItem(saver = saver)
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}

@Composable
private fun DataSaverInitializationItem(saver: DataSaver) {
  val initState by saver.isInitialized.collectAsState()

  Row(
      modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = saver.javaClass.simpleName.replace("DataSaver", ""),
          style = MaterialTheme.typography.bodyLarge,
      )
      Text(
          text =
              when (initState) {
                InitializationState.NOT_STARTED -> "Initializing..."
                InitializationState.SUCCESS -> "Ready"
                InitializationState.FAILED -> "Failed"
              },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Box(modifier = Modifier
        .size(24.dp)
        .padding(4.dp), contentAlignment = Alignment.Center) {
      when (initState) {
        InitializationState.NOT_STARTED -> {
          CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 2.dp)
        }

        InitializationState.SUCCESS -> {
          Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Initialized",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.fillMaxSize(),
          )
        }

        InitializationState.FAILED -> {
          Icon(
              imageVector = Icons.Default.Error,
              contentDescription = "Failed",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}
