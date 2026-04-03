package com.wboelens.polarrecorder.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun EditEventLabelDialog(
    currentLabel: String,
    eventIndex: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var label by remember { mutableStateOf(currentLabel) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Edit Event $eventIndex") },
      text = {
        TextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
      },
      confirmButton = { TextButton(onClick = { onConfirm(label) }) { Text("Save") } },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
