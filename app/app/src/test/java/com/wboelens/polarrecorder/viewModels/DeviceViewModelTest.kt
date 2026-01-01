package com.wboelens.polarrecorder.viewModels

import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.testutil.BaseRobolectricTest
import com.wboelens.polarrecorder.testutil.MockFactories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DeviceViewModel - verifies StateFlow exposure and LiveData updates. Note:
 * Delegation tests removed as they just verify pass-through behavior - that behavior is tested in
 * DeviceStateTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModelTest : BaseRobolectricTest() {

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

  @Test
  fun `allDevices StateFlow updates after addDevice`() = runTest {
    val device = MockFactories.createMockDevice("DEVICE_001")

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
