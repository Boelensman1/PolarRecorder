package com.wboelens.polarrecorder.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.RecordingManager

@Composable
fun RecordingControls(
    isRecording: Boolean,
    isFileSystemEnabled: Boolean,
    recordingManager: RecordingManager,
    dataSavers: DataSavers
) {
  val context = LocalContext.current

  Button(
      onClick = {
        if (isRecording) recordingManager.stopRecording() else recordingManager.startRecording()
      },
      modifier = Modifier.fillMaxWidth()) {
        Text(if (isRecording) "Stop Recording" else "Start Recording")
      }

  if (!isRecording && isFileSystemEnabled) {
    @Suppress("SwallowedException")
    dataSavers.fileSystem.recordingDir?.let { dir ->
      Button(
          onClick = {
            val intent = Intent(Intent.ACTION_VIEW).apply { data = dir.uri }
            try {
              context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
              Toast.makeText(context, "No file explorer app found", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier.fillMaxWidth()) {
            Text("Open Recording Directory")
          }
    }
  }
}
