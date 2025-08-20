package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.viewModels.Device
import com.wboelens.polarrecorder.viewModels.DeviceViewModel

@Composable
fun DeviceList(deviceViewModel: DeviceViewModel, isBLEEnabled: Boolean) {
  if (!isBLEEnabled) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
          text = "Please enable Bluetooth to scan for devices",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.error,
      )
      return
    }
  }

  val devices by deviceViewModel.allDevices.observeAsState(emptyList())

  Text(
      text = "List of available devices:",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
  )
  Spacer(modifier = Modifier.height(4.dp))

  LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(8.dp),
  ) {
    items(devices) { device ->
      DeviceItem(
          device = device,
          onSelect = { deviceViewModel.toggleIsSelected(device.info.deviceId) },
      )
    }
  }
}

@Composable
private fun DeviceItem(device: Device, onSelect: () -> Unit) {
  Card(
      modifier = Modifier
          .fillMaxWidth()
          .height(72.dp),
      onClick = onSelect,
  ) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
          text = device.info.name,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f),
      )

      Checkbox(checked = device.isSelected, onCheckedChange = { onSelect() })
    }
  }
}
