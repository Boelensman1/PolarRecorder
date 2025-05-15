package com.wboelens.polarrecorder.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.managers.PolarApiResult
import com.wboelens.polarrecorder.managers.PolarDeviceSettings
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.ui.components.CheckboxWithLabel
import java.util.Calendar
import kotlinx.coroutines.launch

val emptyDeviceSettings = PolarDeviceSettings(deviceTimeOnConnect = null, sdkModeEnabled = false)

data class DeviceSettingState(
    val isLoading: Boolean = false,
    val resultMessage: String? = null,
    val isSuccess: Boolean = false
)

@Composable
fun DeviceSettingsDialog(
    deviceId: String,
    polarManager: PolarManager,
    onDismiss: () -> Unit,
    onDataTypeSettingsSelected:
        (
            Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
            Set<PolarDeviceDataType>) -> Unit,
    initialDataTypeSettings: Map<PolarDeviceDataType, PolarSensorSetting>? = emptyMap(),
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
        initialDataTypeSettings?.mapValues { (_, sensorSetting) ->
          sensorSetting.settings.mapValues { (_, values) -> values.firstOrNull() ?: 0 }
        } ?: emptyMap())
  }

  var errorMessage by remember { mutableStateOf<String?>(null) }
  var selectedDataTypes by remember { mutableStateOf(initialDataTypes) }
  var availableDataTypes by remember { mutableStateOf<Set<PolarDeviceDataType>>(emptySet()) }
  var deviceSettings by remember { mutableStateOf(emptyDeviceSettings) }
  var isLoading by remember { mutableStateOf(true) }
  var timeSetState by remember { mutableStateOf(DeviceSettingState()) }
  var sdkModeSetState by remember { mutableStateOf(DeviceSettingState()) }
  val coroutineScope = rememberCoroutineScope()

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
    } else {
      errorMessage = "Device capabilities not available. Please reconnect the device."
    }

    val pmDeviceSettings = polarManager.getDeviceSettings(deviceId)
    if (pmDeviceSettings != null) {
      deviceSettings = pmDeviceSettings
    } else {
      errorMessage = "Device settings not available. Please reconnect the device."
    }

    isLoading = false
  }

  // Add this function to handle device settings updates
  fun updateDeviceSettings(newTime: Calendar? = null, newSdkMode: Boolean? = null) {
    coroutineScope.launch {
      // Update time if requested
      if (newTime != null) {
        timeSetState = timeSetState.copy(isLoading = true, resultMessage = null)
        when (val timeResult = polarManager.setTime(deviceId, newTime)) {
          is PolarApiResult.Success -> {
            timeSetState =
                timeSetState.copy(
                    isLoading = false,
                    resultMessage = "Device time set successfully",
                    isSuccess = true)
          }
          is PolarApiResult.Failure -> {
            timeSetState =
                timeSetState.copy(
                    isLoading = false,
                    resultMessage = "Failed to set time: ${timeResult.message}",
                    isSuccess = false)
          }
        }
      }

      // Update SDK mode if requested
      if (newSdkMode != null) {
        sdkModeSetState = sdkModeSetState.copy(isLoading = true, resultMessage = null)
        when (val sdkModeResult = polarManager.setSdkMode(deviceId, newSdkMode)) {
          is PolarApiResult.Success -> {
            deviceSettings = deviceSettings.copy(sdkModeEnabled = newSdkMode)
            sdkModeSetState =
                sdkModeSetState.copy(
                    isLoading = false,
                    resultMessage =
                        "SDK mode ${if (newSdkMode) "enabled" else "disabled"} successfully",
                    isSuccess = true)
          }
          is PolarApiResult.Failure -> {
            sdkModeSetState =
                sdkModeSetState.copy(
                    isLoading = false,
                    resultMessage = "Failed to set SDK mode: ${sdkModeResult.message}",
                    isSuccess = false)
          }
        }
      }
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 600.dp)) {
      Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text(text = "Settings - Device $deviceId", style = MaterialTheme.typography.titleLarge)

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
                  deviceSettings = deviceSettings,
                  onSetTime = { updateDeviceSettings(newTime = Calendar.getInstance()) },
                  onToggleSdkMode = {
                    updateDeviceSettings(newSdkMode = deviceSettings.sdkModeEnabled == false)
                  },
                  timeSetState = timeSetState,
                  sdkModeSetState = sdkModeSetState,
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
                onDataTypeSettingsSelected(selectedSettingsMap, selectedDataTypes)
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
    deviceSettings: PolarDeviceSettings,
    onSetTime: () -> Unit,
    onToggleSdkMode: () -> Unit,
    timeSetState: DeviceSettingState,
    sdkModeSetState: DeviceSettingState,
    availableDataTypes: Set<PolarDeviceDataType>,
    selectedDataTypes: Set<PolarDeviceDataType>,
    onDataTypesChanged: (Set<PolarDeviceDataType>) -> Unit,
    availableSettingsMap: Map<PolarDeviceDataType, PolarSensorSetting>,
    selectedSettingsMap: Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>,
    onSettingsChanged: (Map<PolarDeviceDataType, Map<PolarSensorSetting.SettingType, Int>>) -> Unit
) {
  DeviceSettingsSection(
      deviceSettings = deviceSettings,
      onSetTime = onSetTime,
      onToggleSdkMode = onToggleSdkMode,
      timeSetState = timeSetState,
      sdkModeSetState = sdkModeSetState)

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
private fun DeviceSettingsSection(
    deviceSettings: PolarDeviceSettings,
    onSetTime: () -> Unit,
    onToggleSdkMode: () -> Unit,
    timeSetState: DeviceSettingState,
    sdkModeSetState: DeviceSettingState
) {
  Column {
    deviceSettings.deviceTimeOnConnect?.let { calendar ->
      SettingItem(
          title = "Time",
          content = {
            val dateFormat =
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val deviceTimeText = dateFormat.format(calendar.time)

            Text(
                text = "Device time on connect: $deviceTimeText",
                style = MaterialTheme.typography.bodyMedium)
          },
          buttonText = "Set device time to phone time",
          isLoading = timeSetState.isLoading,
          loadingText = "Setting time...",
          result = timeSetState.resultMessage,
          isSuccess = timeSetState.isSuccess,
          onAction = onSetTime)

      Spacer(modifier = Modifier.height(16.dp))
    }

    deviceSettings.sdkModeEnabled?.let { sdkModeEnabled ->
      SettingItem(
          title = "SDK Mode",
          content = {
            Text(
                text = "Current status: ${if (sdkModeEnabled) "Enabled" else "Disabled"}",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                text =
                    "The SDK mode is the mode of the device in which a wider range of stream capabilities are offered, i.e higher sampling rates, wider (or narrow) ranges etc.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
          },
          buttonText = if (sdkModeEnabled) "Disable SDK Mode" else "Enable SDK Mode",
          isLoading = sdkModeSetState.isLoading,
          loadingText = "Updating SDK mode...",
          result = sdkModeSetState.resultMessage,
          isSuccess = sdkModeSetState.isSuccess,
          onAction = onToggleSdkMode)

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun SettingItem(
    title: String,
    content: @Composable () -> Unit,
    buttonText: String,
    isLoading: Boolean,
    loadingText: String,
    result: String?,
    isSuccess: Boolean,
    onAction: () -> Unit
) {
  Column {
    Text(text = title, style = MaterialTheme.typography.titleMedium)

    Spacer(modifier = Modifier.height(8.dp))

    content()

    Spacer(modifier = Modifier.height(12.dp))

    Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
      if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(loadingText)
      } else {
        Text(buttonText)
      }
    }

    result?.let { resultText ->
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          text = resultText,
          style = MaterialTheme.typography.bodyMedium,
          color =
              if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
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
