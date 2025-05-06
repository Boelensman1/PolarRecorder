package com.wboelens.polarrecorder.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.MQTTConfig

@Composable
fun MQTTSettingsDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean, String?, String?, String, String) -> Unit,
    initialConfig: MQTTConfig
) {
  var host by remember { mutableStateOf(initialConfig.host) }
  var port by remember { mutableStateOf((initialConfig.port.takeIf { it > 0 } ?: 1883).toString()) }
  var useSSL by remember { mutableStateOf(initialConfig.useSSL) }
  var username by remember { mutableStateOf(initialConfig.username ?: "") }
  var password by remember { mutableStateOf(initialConfig.password ?: "") }
  var topicPrefix by remember { mutableStateOf(initialConfig.topicPrefix) }
  var clientId by remember { mutableStateOf(initialConfig.clientId) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("MQTT Settings") },
      text = {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
          TextField(
              value = host,
              onValueChange = { host = it },
              label = { Text("Broker Host") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None,
                      keyboardType = KeyboardType.Uri))
          Spacer(modifier = Modifier.height(8.dp))

          TextField(
              value = port,
              onValueChange = { port = it },
              label = { Text("Port") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
          Spacer(modifier = Modifier.height(8.dp))

          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = useSSL, onCheckedChange = { useSSL = it })
            Text("Use SSL")
          }
          Spacer(modifier = Modifier.height(8.dp))

          TextField(
              value = username,
              onValueChange = { username = it },
              label = { Text("Username (optional)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None, autoCorrect = false))
          Spacer(modifier = Modifier.height(8.dp))
          TextField(
              value = password,
              onValueChange = { password = it },
              label = { Text("Password (optional)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None, autoCorrect = false),
              visualTransformation = PasswordVisualTransformation())
          Spacer(modifier = Modifier.height(8.dp))
          TextField(
              value = topicPrefix,
              onValueChange = { topicPrefix = it },
              label = { Text("Topic Prefix") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None, autoCorrect = false))
          Text(
              text =
                  "Messages will be published under topics: $topicPrefix/[data_type]/[device_ID]",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 4.dp))
          Spacer(modifier = Modifier.height(8.dp))
          TextField(
              value = clientId,
              onValueChange = { clientId = it },
              label = { Text("Client ID") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None, autoCorrect = false))
          Text(
              text =
                  "If no Client ID is set, a random ID in the format 'PolarRecorder_[UUID]' will be used",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 4.dp))
        }
      },
      confirmButton = {
        Button(
            onClick = {
              onSave(
                  host,
                  port.toIntOrNull() ?: 1883,
                  useSSL,
                  username.takeIf { it.isNotEmpty() },
                  password.takeIf { it.isNotEmpty() },
                  topicPrefix,
                  clientId)
              onDismiss()
            }) {
              Text("Save")
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
