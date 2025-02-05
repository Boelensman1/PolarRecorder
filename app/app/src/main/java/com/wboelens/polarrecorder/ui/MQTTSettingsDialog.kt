package com.wboelens.polarrecorder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.MQTTConfig

@Composable
fun MQTTSettingsDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String, String) -> Unit,
    initialConfig: MQTTConfig
) {
  var brokerUrl by remember { mutableStateOf(initialConfig.brokerUrl) }
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
              value = brokerUrl,
              onValueChange = { brokerUrl = it },
              label = { Text("Broker URL") },
              modifier =
                  Modifier.fillMaxWidth().onFocusChanged { focusState ->
                    if (!focusState.isFocused && !brokerUrl.contains("://")) {
                      brokerUrl = "mqtt://$brokerUrl"
                    }
                  },
              singleLine = true,
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None,
                      keyboardType = KeyboardType.Uri))
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
                  brokerUrl,
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
