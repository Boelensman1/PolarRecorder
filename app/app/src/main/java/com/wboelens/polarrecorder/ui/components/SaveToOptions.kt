package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.FileSystemDataSaverConfig
import com.wboelens.polarrecorder.dataSavers.MQTTConfig
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.ui.dialogs.FileSystemSettingsDialog
import com.wboelens.polarrecorder.ui.dialogs.MQTTSettingsDialog
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel

@Composable
fun SaveToOptions(
  dataSavers: DataSavers,
  preferencesManager: PreferencesManager,
  fileSystemSettingsViewModel: FileSystemSettingsViewModel
) {
  var showMqttSettings by remember { mutableStateOf(false) }
  var mqttConfig by remember { mutableStateOf(preferencesManager.mqttConfig) }
  val isMqttEnabled by dataSavers.mqtt.isEnabled.collectAsState()

  var showFilesystemSettings by remember { mutableStateOf(false) }
  var fileSystemConfig by remember { mutableStateOf(preferencesManager.fileSystemDataSaverConfig) }
  val isFileSystemEnabled by dataSavers.fileSystem.isEnabled.collectAsState()

  Column(horizontalAlignment = Alignment.Start) {
    Text(
        text = "Save to:",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(4.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      CheckboxWithLabel(
          "File",
          checked = isFileSystemEnabled,
          onCheckedChange = { checked ->
            if (checked && !dataSavers.fileSystem.isConfigured) {
              // Don't allow enabling if not yet configured
              showFilesystemSettings = true
              return@CheckboxWithLabel
            }

            if (checked) {
              dataSavers.fileSystem.enable()
            } else {
              dataSavers.fileSystem.disable()
            }
          },
      )
      IconButton(onClick = { showFilesystemSettings = true }) {
        Icon(Icons.Default.Settings, contentDescription = "File Settings")
      }
      Spacer(modifier = Modifier.width(16.dp))
      CheckboxWithLabel(
          label = "MQTT",
          checked = isMqttEnabled,
          onCheckedChange = { checked ->
            if (checked && !dataSavers.mqtt.isConfigured) {
              // Don't allow enabling if not yet configured
              showMqttSettings = true
              return@CheckboxWithLabel
            }

            if (checked) {
              dataSavers.mqtt.enable()
            } else {
              dataSavers.mqtt.disable()
            }
          },
      )
      IconButton(onClick = { showMqttSettings = true }) {
        Icon(Icons.Default.Settings, contentDescription = "MQTT Settings")
      }
    }
  }

  if (showMqttSettings) {
    MQTTSettingsDialog(
        onDismiss = { showMqttSettings = false },
        onSave = { host, port, useSsl, username, password, topic, clientId ->
          mqttConfig = MQTTConfig(host, port, useSsl, username, password, clientId, topic)
          dataSavers.mqtt.configure(mqttConfig)

          if (dataSavers.mqtt.isConfigured) {
            dataSavers.mqtt.enable()
          }
          showMqttSettings = false
        },
        initialConfig = mqttConfig,
    )
  }

  if (showFilesystemSettings) {
    FileSystemSettingsDialog(
        onDismiss = { showFilesystemSettings = false },
        onSave = { baseDirectory, splitAtSizeMb ->
          fileSystemConfig = FileSystemDataSaverConfig(baseDirectory, splitAtSizeMb)
          dataSavers.fileSystem.configure(fileSystemConfig)

          if (dataSavers.fileSystem.isConfigured) {
            dataSavers.fileSystem.enable()
          }
          showFilesystemSettings = false
        },
        initialConfig = fileSystemConfig,
        fileSystemSettingsViewModel = fileSystemSettingsViewModel,
    )
  }
}
