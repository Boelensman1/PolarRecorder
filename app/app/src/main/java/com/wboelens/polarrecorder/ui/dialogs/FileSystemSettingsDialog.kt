package com.wboelens.polarrecorder.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.FileSystemDataSaverConfig
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel

@Composable
fun FileSystemSettingsDialog(
  onDismiss: () -> Unit,
  onSave: (String, Int) -> Unit,
  initialConfig: FileSystemDataSaverConfig,
  fileSystemSettingsViewModel: FileSystemSettingsViewModel,
) {
  // Get the context at the Composable level
  val context = LocalContext.current

  var baseDirectory by remember { mutableStateOf(initialConfig.baseDirectory) }
  var splitAtSizeMb by remember { mutableStateOf((initialConfig.splitAtSizeMb).toString()) }

  // Collect the directory from ViewModel
  val selectedDir by fileSystemSettingsViewModel.selectedDirectory.collectAsState()

  // Update baseDirectory when selectedDir changes
  LaunchedEffect(selectedDir) {
    if (selectedDir.isNotEmpty()) {
      baseDirectory = selectedDir
    }
  }

  val directoryPickerLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
        ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
          fileSystemSettingsViewModel.handleDirectoryResult(context, result.data?.data)
        }
      }

  val onDirectoryPick: () -> Unit = {
    directoryPickerLauncher.launch(fileSystemSettingsViewModel.createDirectoryIntent())
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("File System Settings") },
      text = {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)) {
          Button(onClick = onDirectoryPick, modifier = Modifier.fillMaxWidth()) {
            Text("Select Directory")
          }
          if (baseDirectory.isNotEmpty()) {
            Text(
                text = "Selected: $baseDirectory",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
          }
          @Suppress("MaxLineLength")
          Text(
              text =
                  "Directory where recordings will be saved, they will be saved as [recording_name]/[device_id]/[data_type].jsonl",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(bottom = 16.dp),
          )

          Spacer(modifier = Modifier.height(8.dp))

          TextField(
              value = splitAtSizeMb,
              onValueChange = { newValue ->
                // Only allow positive numeric input
                if (newValue.isEmpty() ||
                  (newValue.all { it.isDigit() } &&
                      newValue.toIntOrNull()?.let { it >= 0 } == true)) {
                  splitAtSizeMb = newValue
                }
              },
              label = { Text("Split Recording Size (MB)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          )
          Text(
              text =
                  "Recording will be split into new files when reaching this size. Set to 0 to disable splitting.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 4.dp),
          )
          Text(
              text = "Note: The actual split may occur slightly after the specified size.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 4.dp),
          )
        }
      },
      confirmButton = {
        Button(
            onClick = {
              onSave(baseDirectory, splitAtSizeMb.toIntOrNull() ?: 0)
              onDismiss()
            },
        ) {
          Text("Save")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
