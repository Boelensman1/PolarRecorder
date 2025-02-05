package com.wboelens.polarrecorder.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.managers.PolarManager

@Composable
fun DeviceSettingsDialog(
    deviceId: String,
    polarManager: PolarManager,
    onDismiss: () -> Unit,
    onSettingsSelected:
        (
            Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
            Set<PolarDeviceDataType>) -> Unit,
    initialSettings: Map<PolarDeviceDataType, PolarSensorSetting>? = emptyMap(),
    initialDataTypes: Set<PolarDeviceDataType> = emptySet()
) {
  var availableSettingsMap by remember {
    mutableStateOf<Map<PolarDeviceDataType, PolarSensorSetting>>(emptyMap())
  }
  var allSettingsMap by remember {
    mutableStateOf<Map<PolarDeviceDataType, PolarSensorSetting>>(emptyMap())
  }

  var selectedSettingsMap by remember {
    mutableStateOf(
        initialSettings?.mapValues { (_, sensorSetting) ->
          sensorSetting.settings.mapValues { (_, values) -> values.firstOrNull() ?: 0 }
        } ?: emptyMap())
  }

  var errorMessage by remember { mutableStateOf<String?>(null) }
  var selectedDataTypes by remember { mutableStateOf(initialDataTypes) }
  var availableDataTypes by remember { mutableStateOf<Set<PolarDeviceDataType>>(emptySet()) }
  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(deviceId) {
    isLoading = true

    val capabilities = polarManager.getDeviceCapabilities(deviceId)
    if (capabilities != null) {
      availableDataTypes = capabilities.availableTypes
      availableSettingsMap = capabilities.settings.mapValues { it.value.first }
      allSettingsMap = capabilities.settings.mapValues { it.value.second }

      // Only update selectedSettingsMap if there are no initial settings
      selectedSettingsMap =
          selectedSettingsMap.toMutableMap().apply {
            capabilities.settings.forEach { (dataType, settings) ->
              if (!containsKey(dataType)) {
                put(
                    dataType,
                    settings.first.settings.mapValues { (_, values) -> values.firstOrNull() ?: 0 })
              }
            }
          }
      isLoading = false
    } else {
      errorMessage = "Device capabilities not available. Please reconnect the device."
      isLoading = false
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 600.dp)) {
      Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text(
            text = "Sensor Settings - Device $deviceId",
            style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
          Column {
            if (isLoading) {
              Box(
                  modifier = Modifier.fillMaxWidth().padding(32.dp),
                  contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                  }
            } else if (errorMessage != null) {
              Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            } else {
              DeviceSettingsContent(
                  availableDataTypes = availableDataTypes,
                  selectedDataTypes = selectedDataTypes,
                  onDataTypesChanged = { selectedDataTypes = it },
                  availableSettingsMap = availableSettingsMap,
                  selectedSettingsMap = selectedSettingsMap,
                  onSettingsChanged = { newSettings -> selectedSettingsMap = newSettings })
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
              onClick = {
                onSettingsSelected(selectedSettingsMap, selectedDataTypes)
                onDismiss()
              },
              enabled = selectedDataTypes.isNotEmpty()) {
                Text("Save")
              }
        }
      }
    }
  }
}

@Composable
private fun DeviceSettingsContent(
    availableDataTypes: Set<PolarDeviceDataType>,
    selectedDataTypes: Set<PolarDeviceDataType>,
    onDataTypesChanged: (Set<PolarDeviceDataType>) -> Unit,
    availableSettingsMap: Map<PolarDeviceDataType, PolarSensorSetting>,
    selectedSettingsMap: Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
    onSettingsChanged: (Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>) -> Unit
) {
  DataTypeSection(
      availableTypes = availableDataTypes,
      selectedTypes = selectedDataTypes,
      onSelectionChanged = onDataTypesChanged)

  Spacer(modifier = Modifier.height(16.dp))

  val dataTypesWithSettings =
      selectedDataTypes.filter { dataType ->
        availableSettingsMap[dataType]?.settings?.isNotEmpty() == true
      }

  dataTypesWithSettings.forEachIndexed { index, dataType ->
    Text(
        text = "Settings for ${getDataTypeDisplayText(dataType)}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp))

    availableSettingsMap[dataType]?.settings?.forEach { (settingType, values) ->
      if (values.isNotEmpty()) {
        SettingSection(
            settingType = settingType,
            options = values.toList(),
            selectedValue = selectedSettingsMap[dataType]?.get(settingType),
            onValueSelected = { newValue ->
              onSettingsChanged(
                  selectedSettingsMap.toMutableMap().apply {
                    this[dataType] =
                        (this[dataType] ?: emptyMap()).toMutableMap().apply {
                          this[settingType] = newValue
                        }
                  })
            })
        Spacer(modifier = Modifier.height(8.dp))
      }
    }

    if (index < dataTypesWithSettings.size - 1) {
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
  }
}

@Composable
private fun SettingSection(
    settingType: PolarSensorSetting.SettingType,
    options: List<Int>,
    selectedValue: Int?,
    onValueSelected: (Int) -> Unit
) {
  Column {
    Text(text = settingType.toString(), style = MaterialTheme.typography.titleSmall)
    options.forEach { value ->
      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = value == selectedValue, onClick = { onValueSelected(value) })
            Text(text = value.toString(), modifier = Modifier.padding(start = 8.dp))
          }
    }
  }
}

@Composable
private fun DataTypeSection(
    availableTypes: Set<PolarDeviceDataType>,
    selectedTypes: Set<PolarDeviceDataType>,
    onSelectionChanged: (Set<PolarDeviceDataType>) -> Unit
) {
  Column {
    Text(text = "Data Types", style = MaterialTheme.typography.titleMedium)

    Log.d("DeviceSettingsDialog", "Available types: $availableTypes")

    availableTypes.forEach { dataType ->
      CheckboxWithLabel(
          label = getDataTypeDisplayText(dataType),
          checked = selectedTypes.contains(dataType),
          fullWidth = true,
          onCheckedChange = { checked ->
            if (checked) {
              onSelectionChanged(selectedTypes + dataType)
            } else {
              onSelectionChanged(selectedTypes - dataType)
            }
          })
    }
  }
}

private fun getDataTypeDisplayText(dataType: PolarDeviceDataType): String {
  return when (dataType) {
    PolarDeviceDataType.TEMPERATURE -> "Temperature"
    PolarDeviceDataType.MAGNETOMETER -> "Magnetometer"
    PolarDeviceDataType.GYRO -> "Gyroscope"
    PolarDeviceDataType.PPI -> "PPI"
    PolarDeviceDataType.PPG -> "PPG"
    PolarDeviceDataType.ACC -> "Accelerometer"
    PolarDeviceDataType.ECG -> "ECG"
    PolarDeviceDataType.HR -> "HR & R-R"
    else -> dataType.toString()
  }
}
