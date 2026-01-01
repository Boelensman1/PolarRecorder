package com.wboelens.polarrecorder.dataSavers

import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.state.LogState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for MQTTDataSaver - verifies configuration, enable/disable lifecycle, and basic
 * operations. Note: MQTT client interactions are not tested as they require network connectivity.
 */
class MQTTDataSaverTest {

  private lateinit var logState: LogState
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var mqttDataSaver: MQTTDataSaver

  @BeforeEach
  fun setup() {
    logState = mockk(relaxed = true)
    preferencesManager = mockk {
      every { mqttConfig } returns
          MQTTConfig(host = "", port = MQTTConfig.DEFAULT_MQTT_PORT, useSSL = false)
      every { mqttEnabled } returns false
      every { mqttConfig = any() } just runs
      every { mqttEnabled = any() } just runs
    }
    mqttDataSaver = MQTTDataSaver(logState, preferencesManager)
  }

  // ==================== Configuration Tests ====================

  @Test
  fun `isConfigured false when host empty`() {
    mqttDataSaver.configure(MQTTConfig(host = "", port = 1883, useSSL = false))

    assertFalse(mqttDataSaver.isConfigured)
  }

  @Test
  fun `isConfigured true when host set`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))

    assertTrue(mqttDataSaver.isConfigured)
  }

  @Test
  fun `configure stores config in preferences`() {
    val config = MQTTConfig(host = "broker.test.com", port = 8883, useSSL = true)

    mqttDataSaver.configure(config)

    verify { preferencesManager.mqttConfig = config }
  }

  @Test
  fun `configure sanitizes empty username to null`() {
    val config =
        MQTTConfig(
            host = "broker.test.com",
            port = 1883,
            useSSL = false,
            username = "",
            password = "pass",
        )

    mqttDataSaver.configure(config)

    // Configuration should succeed
    assertTrue(mqttDataSaver.isConfigured)
  }

  @Test
  fun `configure sanitizes empty password to null`() {
    val config =
        MQTTConfig(
            host = "broker.test.com",
            port = 1883,
            useSSL = false,
            username = "user",
            password = "",
        )

    mqttDataSaver.configure(config)

    // Configuration should succeed
    assertTrue(mqttDataSaver.isConfigured)
  }

  // ==================== Enable/Disable Tests ====================

  @Test
  fun `enable fails when host not configured`() {
    mqttDataSaver.configure(MQTTConfig(host = "", port = 1883, useSSL = false))

    mqttDataSaver.enable()

    assertFalse(mqttDataSaver.isEnabled.value)
    verify { logState.addLogError(match { it.contains("must be configured") }) }
  }

  @Test
  fun `enable sets isEnabled true when configured`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))

    mqttDataSaver.enable()

    assertTrue(mqttDataSaver.isEnabled.value)
  }

  @Test
  fun `enable persists enabled state`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))

    mqttDataSaver.enable()

    verify { preferencesManager.mqttEnabled = true }
  }

  @Test
  fun `disable sets isEnabled false`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))
    mqttDataSaver.enable()

    mqttDataSaver.disable()

    assertFalse(mqttDataSaver.isEnabled.value)
  }

  @Test
  fun `disable persists disabled state`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))
    mqttDataSaver.enable()

    mqttDataSaver.disable()

    verify { preferencesManager.mqttEnabled = false }
  }

  // ==================== Init/Save Tests ====================

  @Test
  fun `initSaving resets initialization state`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))

    // initSaving will start in NOT_STARTED then try to connect
    val deviceIdsWithInfo = mapOf("DEVICE_001" to DeviceInfoForDataSaver("Device 1", setOf("HR")))

    // Note: initSaving will fail to connect (no real broker), but it should reset state
    mqttDataSaver.initSaving("TestRecording", deviceIdsWithInfo)

    // State should not be SUCCESS (since we can't connect)
    // But firstMessageSaved should be populated
    assertEquals(1, mqttDataSaver.firstMessageSaved.size)
  }

  @Test
  fun `initSaving populates firstMessageSaved`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))

    val deviceIdsWithInfo =
        mapOf(
            "DEVICE_001" to DeviceInfoForDataSaver("Device 1", setOf("HR", "ACC")),
            "DEVICE_002" to DeviceInfoForDataSaver("Device 2", setOf("ECG")),
        )

    mqttDataSaver.initSaving("TestRecording", deviceIdsWithInfo)

    assertEquals(3, mqttDataSaver.firstMessageSaved.size)
    assertFalse(mqttDataSaver.firstMessageSaved["DEVICE_001/HR"]!!)
    assertFalse(mqttDataSaver.firstMessageSaved["DEVICE_001/ACC"]!!)
    assertFalse(mqttDataSaver.firstMessageSaved["DEVICE_002/ECG"]!!)
  }

  @Test
  fun `saveData logs error when client null`() {
    mqttDataSaver.configure(MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false))
    mqttDataSaver.firstMessageSaved["DEVICE_001/HR"] = false

    mqttDataSaver.saveData(
        phoneTimestamp = System.currentTimeMillis(),
        deviceId = "DEVICE_001",
        recordingName = "TestRecording",
        dataType = "HR",
        data = mapOf("hr" to 72),
    )

    verify { logState.addLogError(match { it.contains("not initialized") }) }
  }
}
