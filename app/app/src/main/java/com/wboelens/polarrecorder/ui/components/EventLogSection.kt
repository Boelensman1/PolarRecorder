package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.recording.EventLogEntry
import com.wboelens.polarrecorder.ui.dialogs.EditEventLabelDialog
import java.util.concurrent.TimeUnit

@Composable
fun EventLogSection(
    events: List<EventLogEntry>,
    recordingStartTime: Long,
    isRecording: Boolean,
    onMarkEvent: () -> Unit,
    onUpdateLabel: (Int, String) -> Unit,
) {
  var editingEvent by remember { mutableStateOf<EventLogEntry?>(null) }

  Column(modifier = Modifier.fillMaxWidth()) {
    if (isRecording) {
      Button(onClick = onMarkEvent, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Mark Event")
      }
    }

    if (events.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text("Events", style = MaterialTheme.typography.titleSmall)
      Spacer(modifier = Modifier.height(4.dp))
      events.forEach { event ->
        EventLogItem(
            event = event,
            recordingStartTime = recordingStartTime,
            onEditClick = { editingEvent = event },
        )
        HorizontalDivider()
      }
    }
  }

  editingEvent?.let { event ->
    EditEventLabelDialog(
        currentLabel = event.label,
        eventIndex = event.index,
        onConfirm = { newLabel ->
          onUpdateLabel(event.index, newLabel)
          editingEvent = null
        },
        onDismiss = { editingEvent = null },
    )
  }
}

@Composable
private fun EventLogItem(
    event: EventLogEntry,
    recordingStartTime: Long,
    onEditClick: () -> Unit,
) {
  val elapsed = event.timestamp - recordingStartTime
  val timeStr = formatElapsedTime(elapsed)

  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onEditClick).padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = event.label, style = MaterialTheme.typography.bodyLarge)
      Text(
          text = timeStr,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onEditClick) {
      Icon(Icons.Default.Edit, contentDescription = "Edit label")
    }
  }
}

private fun formatElapsedTime(elapsedMs: Long): String {
  val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
  val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % TimeUnit.HOURS.toMinutes(1)
  val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % TimeUnit.MINUTES.toSeconds(1)
  return if (hours > 0) {
    String.format("%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format("%02d:%02d", minutes, seconds)
  }
}
