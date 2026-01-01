package com.wboelens.polarrecorder.state

import app.cash.turbine.test
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.testutil.TestDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for DeviceState - the application-scoped state holder for device information. Tests
 * cover device CRUD operations, connection state transitions, selection/filtering, and derived
 * StateFlows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceStateTest {

  @get:Rule val testDispatcherRule = TestDispatcherRule()

  private lateinit var deviceState: DeviceState

  @Before
  fun setup() {
    deviceState = DeviceState()
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
  fun `addDevice adds new device to list`() {
    val device = createMockDevice("DEVICE_001")

    deviceState.addDevice(device)

    val devices = deviceState.allDevices.value
    assertEquals(1, devices.size)
    assertEquals("DEVICE_001", devices[0].info.deviceId)
  }

  @Test
  fun `addDevice with connectable device sets DISCONNECTED state`() {
    val device = createMockDevice("DEVICE_001", isConnectable = true)

    deviceState.addDevice(device)

    val devices = deviceState.allDevices.value
    assertEquals(ConnectionState.DISCONNECTED, devices[0].connectionState)
  }

  @Test
  fun `addDevice with non-connectable device sets NOT_CONNECTABLE state`() {
    val device = createMockDevice("DEVICE_001", isConnectable = false)

    deviceState.addDevice(device)

    val devices = deviceState.allDevices.value
    assertEquals(ConnectionState.NOT_CONNECTABLE, devices[0].connectionState)
  }

  @Test
  fun `addDevice ignores duplicate deviceId`() {
    val device1 = createMockDevice("DEVICE_001", name = "First")
    val device2 = createMockDevice("DEVICE_001", name = "Second")

    deviceState.addDevice(device1)
    deviceState.addDevice(device2)

    val devices = deviceState.allDevices.value
    assertEquals(1, devices.size)
    assertEquals("First", devices[0].info.name)
  }

  @Test
  fun `updateConnectionState changes device state`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)

    deviceState.updateConnectionState("DEVICE_001", ConnectionState.CONNECTING)
    assertEquals(ConnectionState.CONNECTING, deviceState.allDevices.value[0].connectionState)

    deviceState.updateConnectionState("DEVICE_001", ConnectionState.CONNECTED)
    assertEquals(ConnectionState.CONNECTED, deviceState.allDevices.value[0].connectionState)

    deviceState.updateConnectionState("DEVICE_001", ConnectionState.FAILED)
    assertEquals(ConnectionState.FAILED, deviceState.allDevices.value[0].connectionState)
  }

  @Test
  fun `getConnectionState returns correct state`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)

    assertEquals(ConnectionState.DISCONNECTED, deviceState.getConnectionState("DEVICE_001"))

    deviceState.updateConnectionState("DEVICE_001", ConnectionState.CONNECTED)
    assertEquals(ConnectionState.CONNECTED, deviceState.getConnectionState("DEVICE_001"))
  }

  @Test
  fun `getConnectionState returns NOT_CONNECTABLE for unknown device`() {
    assertEquals(ConnectionState.NOT_CONNECTABLE, deviceState.getConnectionState("UNKNOWN"))
  }

  @Test
  fun `toggleIsSelected toggles selection status`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)
    assertFalse(deviceState.allDevices.value[0].isSelected)

    deviceState.toggleIsSelected("DEVICE_001")
    assertTrue(deviceState.allDevices.value[0].isSelected)

    deviceState.toggleIsSelected("DEVICE_001")
    assertFalse(deviceState.allDevices.value[0].isSelected)
  }

  @Test
  fun `allDevices contains selected devices after selection`() {
    val device1 = createMockDevice("DEVICE_001")
    val device2 = createMockDevice("DEVICE_002")
    val device3 = createMockDevice("DEVICE_003")
    deviceState.addDevice(device1)
    deviceState.addDevice(device2)
    deviceState.addDevice(device3)

    deviceState.toggleIsSelected("DEVICE_001")
    deviceState.toggleIsSelected("DEVICE_003")

    val allDevices = deviceState.allDevices.value
    val selectedCount = allDevices.count { it.isSelected }
    assertEquals(2, selectedCount)
    assertTrue(allDevices.find { it.info.deviceId == "DEVICE_001" }?.isSelected == true)
    assertTrue(allDevices.find { it.info.deviceId == "DEVICE_003" }?.isSelected == true)
  }

  @Test
  fun `allDevices contains connected devices after connection`() {
    val device1 = createMockDevice("DEVICE_001")
    val device2 = createMockDevice("DEVICE_002")
    val device3 = createMockDevice("DEVICE_003")
    deviceState.addDevice(device1)
    deviceState.addDevice(device2)
    deviceState.addDevice(device3)

    deviceState.updateConnectionState("DEVICE_001", ConnectionState.CONNECTED)
    deviceState.updateConnectionState("DEVICE_002", ConnectionState.CONNECTING)
    deviceState.updateConnectionState("DEVICE_003", ConnectionState.CONNECTED)

    val allDevices = deviceState.allDevices.value
    val connectedCount = allDevices.count { it.connectionState == ConnectionState.CONNECTED }
    assertEquals(2, connectedCount)
  }

  @Test
  fun `updateDeviceDataTypes sets data types`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)
    val dataTypes = setOf(PolarDeviceDataType.HR, PolarDeviceDataType.ACC)

    deviceState.updateDeviceDataTypes("DEVICE_001", dataTypes)

    val deviceDataTypes = deviceState.allDevices.value[0].dataTypes
    assertEquals(2, deviceDataTypes.size)
    assertTrue(deviceDataTypes.contains(PolarDeviceDataType.HR))
    assertTrue(deviceDataTypes.contains(PolarDeviceDataType.ACC))
  }

  @Test
  fun `getDeviceDataTypes returns correct set`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)
    val dataTypes = setOf(PolarDeviceDataType.ECG, PolarDeviceDataType.PPG)
    deviceState.updateDeviceDataTypes("DEVICE_001", dataTypes)

    val result = deviceState.getDeviceDataTypes("DEVICE_001")

    assertEquals(dataTypes, result)
  }

  @Test
  fun `getDeviceDataTypes returns empty set for unknown device`() {
    val result = deviceState.getDeviceDataTypes("UNKNOWN")

    assertTrue(result.isEmpty())
  }

  @Test
  fun `updateDeviceSensorSettings creates PolarSensorSetting`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)
    val settings =
        mapOf(
            PolarDeviceDataType.ACC to
                mapOf(
                    PolarSensorSetting.SettingType.SAMPLE_RATE to 50,
                    PolarSensorSetting.SettingType.RESOLUTION to 16),
        )

    deviceState.updateDeviceSensorSettings("DEVICE_001", settings)

    val deviceSettings = deviceState.allDevices.value[0].sensorSettings
    assertTrue(deviceSettings.containsKey(PolarDeviceDataType.ACC))
  }

  @Test
  fun `getDeviceSensorSettingsForDataType returns empty for unknown device`() {
    val result = deviceState.getDeviceSensorSettingsForDataType("UNKNOWN", PolarDeviceDataType.ACC)

    assertTrue(result.settings.isEmpty())
  }

  @Test
  fun `updateBatteryLevel updates battery map`() {
    deviceState.updateBatteryLevel("DEVICE_001", 75)

    val batteryLevels = deviceState.batteryLevels.value
    assertEquals(75, batteryLevels["DEVICE_001"])
  }

  @Test
  fun `updateBatteryLevel updates multiple devices`() {
    deviceState.updateBatteryLevel("DEVICE_001", 75)
    deviceState.updateBatteryLevel("DEVICE_002", 50)

    val batteryLevels = deviceState.batteryLevels.value
    assertEquals(2, batteryLevels.size)
    assertEquals(75, batteryLevels["DEVICE_001"])
    assertEquals(50, batteryLevels["DEVICE_002"])
  }

  @Test
  fun `updateFirmwareVersion sets firmware version`() {
    val device = createMockDevice("DEVICE_001")
    deviceState.addDevice(device)

    deviceState.updateFirmwareVersion("DEVICE_001", "1.2.3")

    assertEquals("1.2.3", deviceState.allDevices.value[0].firmwareVersion)
  }

  @Test
  fun `allDevices StateFlow emits updates`() = runTest {
    deviceState.allDevices.test {
      assertEquals(emptyList<Device>(), awaitItem())

      val device = createMockDevice("DEVICE_001")
      deviceState.addDevice(device)

      val updated = awaitItem()
      assertEquals(1, updated.size)
      assertEquals("DEVICE_001", updated[0].info.deviceId)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `batteryLevels StateFlow emits updates`() = runTest {
    deviceState.batteryLevels.test {
      assertEquals(emptyMap<String, Int>(), awaitItem())

      deviceState.updateBatteryLevel("DEVICE_001", 80)

      val updated = awaitItem()
      assertEquals(80, updated["DEVICE_001"])

      cancelAndIgnoreRemainingEvents()
    }
  }
}
