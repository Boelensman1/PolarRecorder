package com.wboelens.polarrecorder.dataSavers

import android.content.Context
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.state.LogState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for DataSavers container class - verifies initialization from preferences, iterator
 * access, and enabled count tracking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataSaversTest {

  private lateinit var context: Context
  private lateinit var logState: LogState
  private lateinit var preferencesManager: PreferencesManager

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
    logState = mockk(relaxed = true)
    preferencesManager = mockk {
      every { mqttConfig } returns
          MQTTConfig(host = "", port = MQTTConfig.DEFAULT_MQTT_PORT, useSSL = false)
      every { mqttEnabled } returns false
      every { mqttConfig = any() } just runs
      every { mqttEnabled = any() } just runs
      every { fileSystemDataSaverConfig } returns FileSystemDataSaverConfig()
      every { fileSystemEnabled } returns false
      every { fileSystemDataSaverConfig = any() } just runs
      every { fileSystemEnabled = any() } just runs
    }
  }

  @Test
  fun `mqtt saver is created and configured`() {
    val dataSavers = DataSavers(context, logState, preferencesManager)

    assertNotNull(dataSavers.mqtt)
  }

  @Test
  fun `fileSystem saver is created and configured`() {
    val dataSavers = DataSavers(context, logState, preferencesManager)

    assertNotNull(dataSavers.fileSystem)
  }

  @Test
  fun `mqtt saver enabled when mqttEnabled is true`() {
    every { preferencesManager.mqttEnabled } returns true
    every { preferencesManager.mqttConfig } returns
        MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false)

    val dataSavers = DataSavers(context, logState, preferencesManager)

    assertTrue(dataSavers.mqtt.isEnabled.value)
  }

  @Test
  fun `mqtt saver disabled when mqttEnabled is false`() {
    every { preferencesManager.mqttEnabled } returns false

    val dataSavers = DataSavers(context, logState, preferencesManager)

    assertFalse(dataSavers.mqtt.isEnabled.value)
  }

  @Test
  fun `iterator returns both savers`() {
    val dataSavers = DataSavers(context, logState, preferencesManager)

    val saverList = mutableListOf<DataSaver>()
    dataSavers.iterator().forEach { saverList.add(it) }

    assertEquals(2, saverList.size)
  }

  @Test
  fun `asList returns both savers as list`() {
    val dataSavers = DataSavers(context, logState, preferencesManager)

    val list = dataSavers.asList()

    assertEquals(2, list.size)
    assertTrue(list.any { it is MQTTDataSaver })
    assertTrue(list.any { it is FileSystemDataSaver })
  }

  @Test
  fun `enabledCount returns 0 when none enabled`() {
    val dataSavers = DataSavers(context, logState, preferencesManager)

    assertEquals(0, dataSavers.enabledCount)
  }

  @Test
  fun `enabledCount returns 1 when one enabled`() {
    every { preferencesManager.mqttEnabled } returns true
    every { preferencesManager.mqttConfig } returns
        MQTTConfig(host = "broker.test.com", port = 1883, useSSL = false)

    val dataSavers = DataSavers(context, logState, preferencesManager)

    assertEquals(1, dataSavers.enabledCount)
  }

  @Test
  fun `mqtt saver configured from preferences`() {
    val testConfig =
        MQTTConfig(
            host = "custom.broker.com",
            port = 8883,
            useSSL = true,
            username = "user",
            password = "pass",
            topicPrefix = "custom/prefix",
        )
    every { preferencesManager.mqttConfig } returns testConfig

    val dataSavers = DataSavers(context, logState, preferencesManager)

    // Verify mqtt saver was configured (isConfigured checks host is not empty)
    assertTrue(dataSavers.mqtt.isConfigured)
  }

  @Test
  fun `fileSystem saver configured from preferences`() {
    val testConfig =
        FileSystemDataSaverConfig(baseDirectory = "content://test/directory", splitAtSizeMb = 50)
    every { preferencesManager.fileSystemDataSaverConfig } returns testConfig

    val dataSavers = DataSavers(context, logState, preferencesManager)

    // Verify file system saver was configured (isConfigured checks baseDirectory is not empty)
    assertTrue(dataSavers.fileSystem.isConfigured)
  }
}
