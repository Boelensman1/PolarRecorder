package com.wboelens.polarrecorder.viewModels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.state.ConnectionState
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.testutil.TestDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for DeviceViewModel - verifies delegation to DeviceState and LiveData exposure. Tests
 * cover all ViewModel methods and their correct delegation behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceViewModelTest {

  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
  @get:Rule val testDispatcherRule = TestDispatcherRule()

  private lateinit var deviceState: DeviceState
  private lateinit var viewModel: DeviceViewModel

  @Before
  fun setup() {
    deviceState = DeviceState()
    viewModel = DeviceViewModel(deviceState)
  }

  @After
  fun tearDown() {
    deviceState.cleanup()
  }

  private fun createMockDevice(
      deviceId: String,
      name: String = "Test Device",
      isConnectable: Boolean = true,
  ): PolarDeviceInfo = mockk {
    every { this@mockk.deviceId } returns deviceId
    every { this@mockk.name } returns name
    every { this@mockk.isConnectable } returns isConnectable
  }

  @Test
  fun `addDevice delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")

    viewModel.addDevice(device)

    assertEquals(1, deviceState.allDevices.value.size)
  }

  @Test
  fun `updateConnectionState delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)

    viewModel.updateConnectionState("DEVICE_001", ConnectionState.CONNECTING)

    assertEquals(ConnectionState.CONNECTING, deviceState.allDevices.value[0].connectionState)
  }

  @Test
  fun `updateFirmwareVersion delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)

    viewModel.updateFirmwareVersion("DEVICE_001", "2.0.0")

    assertEquals("2.0.0", deviceState.allDevices.value[0].firmwareVersion)
  }

  @Test
  fun `getConnectionState delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)
    deviceState.updateConnectionState("DEVICE_001", ConnectionState.CONNECTED)

    val state = viewModel.getConnectionState("DEVICE_001")

    assertEquals(ConnectionState.CONNECTED, state)
  }

  @Test
  fun `toggleIsSelected delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)

    viewModel.toggleIsSelected("DEVICE_001")

    assertTrue(deviceState.allDevices.value[0].isSelected)
  }

  @Test
  fun `updateDeviceDataTypes delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)
    val dataTypes = setOf(PolarDeviceDataType.HR, PolarDeviceDataType.ACC)

    viewModel.updateDeviceDataTypes("DEVICE_001", dataTypes)

    assertEquals(dataTypes, deviceState.allDevices.value[0].dataTypes)
  }

  @Test
  fun `updateDeviceSensorSettings delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)
    val settings =
        mapOf(PolarDeviceDataType.ACC to mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 50))

    viewModel.updateDeviceSensorSettings("DEVICE_001", settings)

    assertTrue(deviceState.allDevices.value[0].sensorSettings.containsKey(PolarDeviceDataType.ACC))
  }

  @Test
  fun `getDeviceDataTypes delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)
    val dataTypes = setOf(PolarDeviceDataType.ECG)
    deviceState.updateDeviceDataTypes("DEVICE_001", dataTypes)

    val result = viewModel.getDeviceDataTypes("DEVICE_001")

    assertEquals(dataTypes, result)
  }

  @Test
  fun `getDeviceSensorSettingsForDataType delegates to DeviceState`() {
    val device = createMockDevice("DEVICE_001")
    viewModel.addDevice(device)
    val settings =
        mapOf(PolarDeviceDataType.HR to mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 100))
    deviceState.updateDeviceSensorSettings("DEVICE_001", settings)

    val result = viewModel.getDeviceSensorSettingsForDataType("DEVICE_001", PolarDeviceDataType.HR)

    assertTrue(result.settings.isNotEmpty())
  }

  @Test
  fun `updateBatteryLevel delegates to DeviceState`() {
    viewModel.updateBatteryLevel("DEVICE_001", 50)

    assertEquals(50, deviceState.batteryLevels.value["DEVICE_001"])
  }

  @Test
  fun `allDevices StateFlow updates after addDevice`() = runTest {
    val device = createMockDevice("DEVICE_001")

    viewModel.addDevice(device)
    advanceUntilIdle()

    assertEquals(1, deviceState.allDevices.value.size)
    assertEquals("DEVICE_001", deviceState.allDevices.value[0].info.deviceId)
  }

  @Test
  fun `batteryLevels StateFlow updates after updateBatteryLevel`() = runTest {
    viewModel.updateBatteryLevel("DEVICE_001", 75)
    advanceUntilIdle()

    assertEquals(75, deviceState.batteryLevels.value["DEVICE_001"])
  }
}
