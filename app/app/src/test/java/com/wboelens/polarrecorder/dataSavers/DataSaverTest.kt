package com.wboelens.polarrecorder.dataSavers

import com.google.gson.JsonParser
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.state.LogState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the abstract DataSaver class. Uses a test implementation to verify base class
 * behavior including JSON payload creation, initialization state management, and firstMessageSaved
 * tracking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataSaverTest {

  private lateinit var logState: LogState
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var dataSaver: TestDataSaver

  /** Test implementation of the abstract DataSaver class */
  class TestDataSaver(logState: LogState, preferencesManager: PreferencesManager) :
      DataSaver(logState, preferencesManager) {

    override val isConfigured: Boolean = true

    var enableCalled = false
    var disableCalled = false
    var saveDataCalled = false
    var savedData: List<Any> = mutableListOf()

    override fun enable() {
      enableCalled = true
      _isEnabled.value = true
    }

    override fun disable() {
      disableCalled = true
      _isEnabled.value = false
    }

    override fun saveData(
        phoneTimestamp: Long,
        deviceId: String,
        recordingName: String,
        dataType: String,
        data: Any,
    ) {
      saveDataCalled = true
      (savedData as MutableList).add(data)
    }

    // Expose protected members for testing
    fun setInitialized(state: InitializationState) {
      _isInitialized.value = state
    }
  }

  @Before
  fun setup() {
    logState = mockk(relaxed = true)
    preferencesManager = mockk(relaxed = true)
    dataSaver = TestDataSaver(logState, preferencesManager)
  }

  // ==================== createJSONPayload Tests ====================

  @Test
  fun `createJSONPayload includes all required fields`() {
    val payload =
        dataSaver.createJSONPayload(
            phoneTimestamp = 1234567890L,
            deviceId = "DEVICE_001",
            recordingName = "TestRecording",
            dataType = "HR",
            data = mapOf("hr" to 72),
        )

    val json = JsonParser.parseString(payload).asJsonObject

    assertTrue(json.has("phoneTimestamp"))
    assertTrue(json.has("deviceId"))
    assertTrue(json.has("recordingName"))
    assertTrue(json.has("dataType"))
    assertTrue(json.has("data"))
  }

  @Test
  fun `createJSONPayload phoneTimestamp is correct`() {
    val payload =
        dataSaver.createJSONPayload(
            phoneTimestamp = 9876543210L,
            deviceId = "DEVICE_001",
            recordingName = "TestRecording",
            dataType = "HR",
            data = mapOf("hr" to 72),
        )

    val json = JsonParser.parseString(payload).asJsonObject

    assertEquals(9876543210L, json.get("phoneTimestamp").asLong)
  }

  @Test
  fun `createJSONPayload deviceId is correct`() {
    val payload =
        dataSaver.createJSONPayload(
            phoneTimestamp = 1234567890L,
            deviceId = "MY_DEVICE_123",
            recordingName = "TestRecording",
            dataType = "HR",
            data = mapOf("hr" to 72),
        )

    val json = JsonParser.parseString(payload).asJsonObject

    assertEquals("MY_DEVICE_123", json.get("deviceId").asString)
  }

  @Test
  fun `createJSONPayload recordingName is correct`() {
    val payload =
        dataSaver.createJSONPayload(
            phoneTimestamp = 1234567890L,
            deviceId = "DEVICE_001",
            recordingName = "CustomRecordingName",
            dataType = "HR",
            data = mapOf("hr" to 72),
        )

    val json = JsonParser.parseString(payload).asJsonObject

    assertEquals("CustomRecordingName", json.get("recordingName").asString)
  }

  @Test
  fun `createJSONPayload dataType is correct`() {
    val payload =
        dataSaver.createJSONPayload(
            phoneTimestamp = 1234567890L,
            deviceId = "DEVICE_001",
            recordingName = "TestRecording",
            dataType = "ACC",
            data = mapOf("x" to 100),
        )

    val json = JsonParser.parseString(payload).asJsonObject

    assertEquals("ACC", json.get("dataType").asString)
  }

  @Test
  fun `createJSONPayload data is serialized`() {
    val testData = mapOf("hr" to 72, "rrs" to listOf(800, 810, 805))

    val payload =
        dataSaver.createJSONPayload(
            phoneTimestamp = 1234567890L,
            deviceId = "DEVICE_001",
            recordingName = "TestRecording",
            dataType = "HR",
            data = testData,
        )

    val json = JsonParser.parseString(payload).asJsonObject
    val dataObject = json.get("data").asJsonObject

    assertEquals(72, dataObject.get("hr").asInt)
    assertTrue(dataObject.has("rrs"))
  }

  // ==================== initSaving Tests ====================

  @Test
  fun `initSaving resets initialization state`() {
    dataSaver.setInitialized(InitializationState.SUCCESS)

    dataSaver.initSaving("TestRecording", emptyMap())

    assertEquals(InitializationState.NOT_STARTED, dataSaver.isInitialized.value)
  }

  @Test
  fun `initSaving clears firstMessageSaved`() {
    dataSaver.firstMessageSaved["DEVICE_001/HR"] = true
    dataSaver.firstMessageSaved["DEVICE_001/ACC"] = true

    dataSaver.initSaving("TestRecording", emptyMap())

    assertTrue(dataSaver.firstMessageSaved.isEmpty())
  }

  @Test
  fun `initSaving populates firstMessageSaved for all devices and dataTypes`() {
    val deviceIdsWithInfo =
        mapOf(
            "DEVICE_001" to DeviceInfoForDataSaver("Device 1", setOf("HR", "ACC")),
            "DEVICE_002" to DeviceInfoForDataSaver("Device 2", setOf("ECG")),
        )

    dataSaver.initSaving("TestRecording", deviceIdsWithInfo)

    assertEquals(3, dataSaver.firstMessageSaved.size)
    assertFalse(dataSaver.firstMessageSaved["DEVICE_001/HR"]!!)
    assertFalse(dataSaver.firstMessageSaved["DEVICE_001/ACC"]!!)
    assertFalse(dataSaver.firstMessageSaved["DEVICE_002/ECG"]!!)
  }

  // ==================== stopSaving Tests ====================

  @Test
  fun `stopSaving resets initialization state`() {
    dataSaver.setInitialized(InitializationState.SUCCESS)

    dataSaver.stopSaving()

    assertEquals(InitializationState.NOT_STARTED, dataSaver.isInitialized.value)
  }

  // ==================== State Tests ====================

  @Test
  fun `isEnabled initial value is false`() {
    assertFalse(dataSaver.isEnabled.value)
  }

  @Test
  fun `isInitialized initial value is NOT_STARTED`() {
    assertEquals(InitializationState.NOT_STARTED, dataSaver.isInitialized.value)
  }

  @Test
  fun `enable sets isEnabled to true`() {
    dataSaver.enable()

    assertTrue(dataSaver.isEnabled.value)
    assertTrue(dataSaver.enableCalled)
  }

  @Test
  fun `disable sets isEnabled to false`() {
    dataSaver.enable()
    dataSaver.disable()

    assertFalse(dataSaver.isEnabled.value)
    assertTrue(dataSaver.disableCalled)
  }

  @Test
  fun `isConfigured returns true for test implementation`() {
    assertTrue(dataSaver.isConfigured)
  }
}
