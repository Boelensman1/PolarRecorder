package com.wboelens.polarrecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CheckboxWithLabel(
    label: String,
    checked: Boolean = false,
    fullWidth: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.let { if (fullWidth) it.fillMaxWidth() else it }
              .clickable { onCheckedChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
      }
}
