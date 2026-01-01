package com.wboelens.polarrecorder.managers

import android.content.Context
import com.wboelens.polarrecorder.dataSavers.FileSystemDataSaverConfig
import com.wboelens.polarrecorder.dataSavers.MQTTConfig
import com.wboelens.polarrecorder.testutil.BaseRobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for PreferencesManager - verifies persistence and retrieval of all preference types
 * including MQTT config, file system config, and recording settings.
 */
class PreferencesManagerTest : BaseRobolectricTest() {

  private lateinit var context: Context
  private lateinit var preferencesManager: PreferencesManager

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
    preferencesManager = PreferencesManager(context)
  }

  // ==================== MQTT Config Tests ====================

  @Test
  fun `mqttConfig returns default values initially`() {
    val config = preferencesManager.mqttConfig

    assertEquals("", config.host)
    assertEquals(MQTTConfig.DEFAULT_MQTT_PORT, config.port)
    assertFalse(config.useSSL)
    assertEquals("", config.username)
    assertEquals("", config.password)
  }

  @Test
  fun `mqttConfig setter persists all fields`() {
    val config =
        MQTTConfig(
            host = "broker.example.com",
            port = 8883,
            useSSL = true,
            username = "user",
            password = "pass",
            clientId = "test-client",
            topicPrefix = "test/prefix",
        )

    preferencesManager.mqttConfig = config

    // Re-create manager to verify persistence
    val newManager = PreferencesManager(context)
    val retrieved = newManager.mqttConfig

    assertEquals("broker.example.com", retrieved.host)
    assertEquals(8883, retrieved.port)
    assertTrue(retrieved.useSSL)
    assertEquals("user", retrieved.username)
    assertEquals("pass", retrieved.password)
    assertEquals("test-client", retrieved.clientId)
    assertEquals("test/prefix", retrieved.topicPrefix)
  }

  @Test
  fun `mqttEnabled default is false`() {
    assertFalse(preferencesManager.mqttEnabled)
  }

  @Test
  fun `mqttEnabled setter persists value`() {
    preferencesManager.mqttEnabled = true

    val newManager = PreferencesManager(context)
    assertTrue(newManager.mqttEnabled)
  }

  @Test
  fun `mqttConfig handles empty username and password`() {
    val config =
        MQTTConfig(
            host = "broker.example.com",
            port = 1883,
            useSSL = false,
            username = "",
            password = "",
        )

    preferencesManager.mqttConfig = config

    val retrieved = preferencesManager.mqttConfig
    assertEquals("", retrieved.username)
    assertEquals("", retrieved.password)
  }

  // ==================== FileSystem Config Tests ====================

  @Test
  fun `fileSystemDataSaverConfig returns defaults initially`() {
    val config = preferencesManager.fileSystemDataSaverConfig

    assertEquals("", config.baseDirectory)
    assertEquals(20, config.splitAtSizeMb) // DEFAULT_SPLIT_SIZE_MB
  }

  @Test
  fun `fileSystemDataSaverConfig setter persists all fields`() {
    val config = FileSystemDataSaverConfig(baseDirectory = "content://test/dir", splitAtSizeMb = 50)

    preferencesManager.fileSystemDataSaverConfig = config

    val newManager = PreferencesManager(context)
    val retrieved = newManager.fileSystemDataSaverConfig

    assertEquals("content://test/dir", retrieved.baseDirectory)
    assertEquals(50, retrieved.splitAtSizeMb)
  }

  @Test
  fun `fileSystemEnabled default is false`() {
    assertFalse(preferencesManager.fileSystemEnabled)
  }

  @Test
  fun `fileSystemEnabled setter persists value`() {
    preferencesManager.fileSystemEnabled = true

    val newManager = PreferencesManager(context)
    assertTrue(newManager.fileSystemEnabled)
  }

  // ==================== Recording Settings Tests ====================

  @Test
  fun `recordingName default is PolarRecording`() {
    assertEquals("PolarRecording", preferencesManager.recordingName)
  }

  @Test
  fun `recordingName setter persists value`() {
    preferencesManager.recordingName = "CustomRecording"

    val newManager = PreferencesManager(context)
    assertEquals("CustomRecording", newManager.recordingName)
  }

  @Test
  fun `recordingNameAppendTimestamp default is true`() {
    assertTrue(preferencesManager.recordingNameAppendTimestamp)
  }

  @Test
  fun `recordingNameAppendTimestamp setter persists value`() {
    preferencesManager.recordingNameAppendTimestamp = false

    val newManager = PreferencesManager(context)
    assertFalse(newManager.recordingNameAppendTimestamp)
  }

  @Test
  fun `recordingStopOnDisconnect default is false`() {
    assertFalse(preferencesManager.recordingStopOnDisconnect)
  }

  @Test
  fun `recordingStopOnDisconnect setter persists value`() {
    preferencesManager.recordingStopOnDisconnect = true

    val newManager = PreferencesManager(context)
    assertTrue(newManager.recordingStopOnDisconnect)
  }

  // ==================== Edge Cases ====================

  @Test
  fun `preferences persist across manager instances`() {
    preferencesManager.recordingName = "TestName"
    preferencesManager.mqttEnabled = true
    preferencesManager.fileSystemEnabled = true

    val newManager = PreferencesManager(context)

    assertEquals("TestName", newManager.recordingName)
    assertTrue(newManager.mqttEnabled)
    assertTrue(newManager.fileSystemEnabled)
  }

  @Test
  fun `setting and getting values in sequence works correctly`() {
    preferencesManager.recordingName = "First"
    assertEquals("First", preferencesManager.recordingName)

    preferencesManager.recordingName = "Second"
    assertEquals("Second", preferencesManager.recordingName)

    preferencesManager.recordingName = "Third"
    assertEquals("Third", preferencesManager.recordingName)
  }
}
