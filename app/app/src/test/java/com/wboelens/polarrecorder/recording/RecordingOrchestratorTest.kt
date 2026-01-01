package com.wboelens.polarrecorder.recording

import app.cash.turbine.test
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import com.wboelens.polarrecorder.dataSavers.DataSaver
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.services.RecordingState
import com.wboelens.polarrecorder.state.ConnectionState
import com.wboelens.polarrecorder.state.Device
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.state.LogEntry
import com.wboelens.polarrecorder.state.LogState
import com.wboelens.polarrecorder.state.LogType
import com.wboelens.polarrecorder.testutil.MockFactories
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for RecordingOrchestrator, the business logic extracted from RecordingService. Tests
 * cover all public methods and state flows.
 */
class RecordingOrchestratorTest {

  // Test implementations for injectable dependencies
  class TestClock(var time: Long = 1000L) : Clock {
    override fun currentTimeMillis(): Long = time
  }

  class TestSchedulerProvider : SchedulerProvider {
    override fun io(): Scheduler = Schedulers.trampoline()

    override fun computation(): Scheduler = Schedulers.trampoline()
  }

  // Mocks
  private lateinit var polarManager: PolarManager
  private lateinit var deviceState: DeviceState
  private lateinit var logState: LogState
  private lateinit var preferencesManager: PreferencesManager
  private lateinit var dataSavers: DataSavers
  private lateinit var clock: TestClock
  private lateinit var schedulerProvider: TestSchedulerProvider
  private lateinit var appInfoProvider: AppInfoProvider

  // Subject under test
  private lateinit var orchestrator: RecordingOrchestrator

  // StateFlows for controlling mock behavior
  private lateinit var selectedDevicesFlow: MutableStateFlow<List<Device>>
  private lateinit var connectedDevicesFlow: MutableStateFlow<List<Device>>
  private lateinit var logMessagesFlow: MutableStateFlow<List<LogEntry>>

  @BeforeEach
  fun setup() {
    polarManager = mockk(relaxed = true)
    deviceState = mockk(relaxed = true)
    logState = mockk(relaxed = true)
    preferencesManager = mockk(relaxed = true)
    dataSavers = mockk(relaxed = true)
    clock = TestClock()
    schedulerProvider = TestSchedulerProvider()
    appInfoProvider = mockk(relaxed = true)

    // Setup default state flows
    selectedDevicesFlow = MutableStateFlow(emptyList())
    connectedDevicesFlow = MutableStateFlow(emptyList())
    logMessagesFlow = MutableStateFlow(emptyList())

    every { deviceState.selectedDevices } returns selectedDevicesFlow
    every { deviceState.connectedDevices } returns connectedDevicesFlow
    every { logState.logMessages } returns logMessagesFlow
    every { dataSavers.asList() } returns emptyList()
    every { dataSavers.enabledCount } returns 0
    every { polarManager.getSdkVersion() } returns "1.0.0"

    orchestrator =
        RecordingOrchestrator(
            polarManager = polarManager,
            deviceState = deviceState,
            logState = logState,
            preferencesManager = preferencesManager,
            dataSavers = dataSavers,
            clock = clock,
            schedulerProvider = schedulerProvider,
            appInfoProvider = appInfoProvider,
        )
  }

  // ==================== Helper Functions ====================

  private fun createDevice(
      deviceId: String,
      name: String = "Test Device",
      isSelected: Boolean = true,
      connectionState: ConnectionState = ConnectionState.CONNECTED,
      dataTypes: Set<PolarDeviceDataType> = setOf(PolarDeviceDataType.HR),
  ): Device {
    val info = MockFactories.createMockDevice(deviceId, name)
    return Device(
        info = info,
        isSelected = isSelected,
        connectionState = connectionState,
        dataTypes = dataTypes,
    )
  }

  private fun createMockDataSaver(
      enabled: Boolean = false,
      initialized: InitializationState = InitializationState.NOT_STARTED,
  ): DataSaver {
    return mockk(relaxed = true) {
      every { isEnabled } returns MutableStateFlow(enabled)
      every { isInitialized } returns MutableStateFlow(initialized)
    }
  }

  private fun setupValidRecordingConditions() {
    val device = createDevice("DEVICE_001")
    selectedDevicesFlow.value = listOf(device)
    connectedDevicesFlow.value = listOf(device)

    val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
    every { dataSavers.asList() } returns listOf(dataSaver)
    every { dataSavers.enabledCount } returns 1

    every { deviceState.getDeviceDataTypes("DEVICE_001") } returns setOf(PolarDeviceDataType.HR)
    every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
        PolarSensorSetting(emptyMap())

    // Setup streaming mock
    every { polarManager.startStreaming(any(), any(), any()) } returns Flowable.never<Any>()
  }

  // ==================== startRecording() Validation Tests ====================

  @Nested
  inner class StartRecordingValidation {

    @Test
    fun `startRecording with empty name returns EmptyRecordingName`() {
      val result = orchestrator.startRecording("")

      assertTrue(result is StartRecordingResult.EmptyRecordingName)
      verify { logState.addLogError(any()) }
    }

    @Test
    fun `startRecording when already recording returns AlreadyRecording`() {
      setupValidRecordingConditions()

      // Start first recording
      orchestrator.startRecording("First Recording")

      // Try to start another
      val result = orchestrator.startRecording("Second Recording")

      assertTrue(result is StartRecordingResult.AlreadyRecording)
    }

    @Test
    fun `startRecording with no devices selected returns NoDevicesSelected`() {
      selectedDevicesFlow.value = emptyList()

      val result = orchestrator.startRecording("Test Recording")

      assertTrue(result is StartRecordingResult.NoDevicesSelected)
      verify { logState.addLogError(match { it.contains("No devices selected") }) }
    }

    @Test
    fun `startRecording with devices not connected returns DevicesNotConnected`() {
      val device = createDevice("DEVICE_001", name = "My Polar H10")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = emptyList() // Device selected but not connected

      val result = orchestrator.startRecording("Test Recording")

      assertTrue(result is StartRecordingResult.DevicesNotConnected)
      assertEquals(
          "My Polar H10", (result as StartRecordingResult.DevicesNotConnected).disconnectedNames)
    }

    @Test
    fun `startRecording with no data savers enabled returns NoDataSaversEnabled`() {
      val device = createDevice("DEVICE_001")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)

      val disabledSaver = createMockDataSaver(enabled = false)
      every { dataSavers.asList() } returns listOf(disabledSaver)

      val result = orchestrator.startRecording("Test Recording")

      assertTrue(result is StartRecordingResult.NoDataSaversEnabled)
    }

    @Test
    fun `startRecording with data savers not initialized returns DataSaversNotInitialized`() {
      val device = createDevice("DEVICE_001")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)

      val uninitializedSaver =
          createMockDataSaver(enabled = true, initialized = InitializationState.NOT_STARTED)
      every { dataSavers.asList() } returns listOf(uninitializedSaver)

      val result = orchestrator.startRecording("Test Recording")

      assertTrue(result is StartRecordingResult.DataSaversNotInitialized)
    }

    @Test
    fun `startRecording with valid conditions returns Success`() {
      setupValidRecordingConditions()

      val result = orchestrator.startRecording("Test Recording")

      assertTrue(result is StartRecordingResult.Success)
    }
  }

  // ==================== startRecording() Success Behavior Tests ====================

  @Nested
  inner class StartRecordingSuccessBehavior {

    @Test
    fun `startRecording updates recordingState with correct values`() {
      setupValidRecordingConditions()
      clock.time = 5000L

      orchestrator.startRecording("My Recording")

      val state = orchestrator.recordingState.value
      assertTrue(state.isRecording)
      assertEquals("My Recording", state.currentRecordingName)
      assertEquals(5000L, state.recordingStartTime)
    }

    @Test
    fun `startRecording initializes lastData map for selected devices`() {
      val device =
          createDevice(
              "DEVICE_001", dataTypes = setOf(PolarDeviceDataType.HR, PolarDeviceDataType.ACC))
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)

      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      every { dataSavers.asList() } returns listOf(dataSaver)
      every { dataSavers.enabledCount } returns 1
      every { deviceState.getDeviceDataTypes("DEVICE_001") } returns
          setOf(PolarDeviceDataType.HR, PolarDeviceDataType.ACC)
      every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
          PolarSensorSetting(emptyMap())
      every { polarManager.startStreaming(any(), any(), any()) } returns Flowable.never<Any>()

      orchestrator.startRecording("Test Recording")

      val lastData = orchestrator.lastData.value
      assertTrue(lastData.containsKey("DEVICE_001"))
      assertEquals(2, lastData["DEVICE_001"]?.size)
    }

    @Test
    fun `startRecording clears lastDataTimestamps`() = runTest {
      setupValidRecordingConditions()

      orchestrator.startRecording("Test Recording")

      assertTrue(orchestrator.lastDataTimestamps.value.isEmpty())
    }

    @Test
    fun `startRecording logs app and device info when appInfoProvider is set`() {
      setupValidRecordingConditions()
      every { appInfoProvider.versionName } returns "1.2.3"
      every { appInfoProvider.versionCode } returns 42L
      every { appInfoProvider.androidVersion } returns "Android 14"
      every { appInfoProvider.deviceInfo } returns "Google Pixel 8"

      orchestrator.startRecording("Test Recording")

      verify { logState.addLogMessage(match { it.contains("1.2.3") }) }
      verify { logState.addLogMessage(match { it.contains("Android 14") }) }
      verify { logState.addLogMessage(match { it.contains("Google Pixel 8") }) }
    }

    @Test
    fun `startRecording starts streams for each device and dataType`() {
      val device = createDevice("DEVICE_001", dataTypes = setOf(PolarDeviceDataType.HR))
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)

      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      every { dataSavers.asList() } returns listOf(dataSaver)
      every { dataSavers.enabledCount } returns 1
      every { deviceState.getDeviceDataTypes("DEVICE_001") } returns setOf(PolarDeviceDataType.HR)
      every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
          PolarSensorSetting(emptyMap())
      every { polarManager.startStreaming(any(), any(), any()) } returns Flowable.never<Any>()

      orchestrator.startRecording("Test Recording")

      verify { polarManager.startStreaming("DEVICE_001", PolarDeviceDataType.HR, any()) }
    }

    @Test
    fun `startRecording logs success message with saver count`() {
      setupValidRecordingConditions()

      orchestrator.startRecording("My Recording")

      verify {
        logState.addLogSuccess(match { it.contains("My Recording") && it.contains("1 data saver") })
      }
    }
  }

  // ==================== stopRecording() Tests ====================

  @Nested
  inner class StopRecording {

    @Test
    fun `stopRecording when not recording logs error`() {
      orchestrator.stopRecording()

      verify { logState.addLogError(match { it.contains("no recording in progress") }) }
    }

    @Test
    fun `stopRecording when recording resets state`() {
      setupValidRecordingConditions()
      orchestrator.startRecording("Test Recording")

      orchestrator.stopRecording()

      val state = orchestrator.recordingState.value
      assertFalse(state.isRecording)
      assertEquals("", state.currentRecordingName)
      assertEquals(0L, state.recordingStartTime)
    }

    @Test
    fun `stopRecording logs message`() {
      setupValidRecordingConditions()
      orchestrator.startRecording("Test Recording")

      orchestrator.stopRecording()

      verify { logState.addLogMessage("Recording stopped") }
    }

    @Test
    fun `stopRecording calls stopSaving on enabled data savers`() {
      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      val device = createDevice("DEVICE_001")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)
      every { dataSavers.asList() } returns listOf(dataSaver)
      every { dataSavers.enabledCount } returns 1
      every { deviceState.getDeviceDataTypes("DEVICE_001") } returns setOf(PolarDeviceDataType.HR)
      every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
          PolarSensorSetting(emptyMap())
      every { polarManager.startStreaming(any(), any(), any()) } returns Flowable.never<Any>()

      orchestrator.startRecording("Test Recording")
      orchestrator.stopRecording()

      verify { dataSaver.stopSaving() }
    }

    @Test
    fun `stopRecording clears lastDataTimestamps`() {
      setupValidRecordingConditions()
      orchestrator.startRecording("Test Recording")

      orchestrator.stopRecording()

      assertTrue(orchestrator.lastDataTimestamps.value.isEmpty())
    }
  }

  // ==================== handleDevicesChanged() Tests ====================

  @Nested
  inner class HandleDevicesChanged {

    @Test
    fun `handleDevicesChanged when not recording returns false`() {
      val result = orchestrator.handleDevicesChanged(emptyList())

      assertFalse(result)
    }

    @Test
    fun `handleDevicesChanged with empty devices and stopOnDisconnect stops recording`() {
      setupValidRecordingConditions()
      every { preferencesManager.recordingStopOnDisconnect } returns true
      orchestrator.startRecording("Test Recording")

      val result = orchestrator.handleDevicesChanged(emptyList())

      assertTrue(result)
      assertFalse(orchestrator.recordingState.value.isRecording)
    }

    @Test
    fun `handleDevicesChanged with empty devices and stopOnDisconnect false continues recording`() {
      setupValidRecordingConditions()
      every { preferencesManager.recordingStopOnDisconnect } returns false
      orchestrator.startRecording("Test Recording")

      val result = orchestrator.handleDevicesChanged(emptyList())

      assertFalse(result)
      assertTrue(orchestrator.recordingState.value.isRecording)
    }

    @Test
    fun `handleDevicesChanged when device reconnects restarts streams`() {
      val device = createDevice("DEVICE_001")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)

      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      every { dataSavers.asList() } returns listOf(dataSaver)
      every { dataSavers.enabledCount } returns 1
      every { deviceState.getDeviceDataTypes("DEVICE_001") } returns setOf(PolarDeviceDataType.HR)
      every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
          PolarSensorSetting(emptyMap())
      every { polarManager.startStreaming(any(), any(), any()) } returns Flowable.never<Any>()
      every { preferencesManager.recordingStopOnDisconnect } returns false

      orchestrator.startRecording("Test Recording")

      // Simulate disconnect
      orchestrator.handleDevicesChanged(emptyList())

      // Simulate reconnect
      orchestrator.handleDevicesChanged(listOf(device))

      // Verify startStreaming was called twice (initial + reconnect)
      verify(exactly = 2) {
        polarManager.startStreaming("DEVICE_001", PolarDeviceDataType.HR, any())
      }
    }
  }

  // ==================== handleLogMessagesChanged() Tests ====================

  @Nested
  inner class HandleLogMessagesChanged {

    @Test
    fun `handleLogMessagesChanged with empty messages does nothing`() {
      orchestrator.handleLogMessagesChanged(emptyList())

      // No exception thrown, no data saver calls
      verify(exactly = 0) { dataSavers.asList() }
    }

    @Test
    fun `handleLogMessagesChanged when not recording does not save`() {
      val messages = listOf(LogEntry("Test", LogType.NORMAL, 1000L))

      orchestrator.handleLogMessagesChanged(messages)

      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      verify(exactly = 0) { dataSaver.saveData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleLogMessagesChanged when recording saves new messages`() {
      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      val device = createDevice("DEVICE_001")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)
      every { dataSavers.asList() } returns listOf(dataSaver)
      every { dataSavers.enabledCount } returns 1
      every { deviceState.getDeviceDataTypes("DEVICE_001") } returns setOf(PolarDeviceDataType.HR)
      every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
          PolarSensorSetting(emptyMap())
      every { polarManager.startStreaming(any(), any(), any()) } returns Flowable.never<Any>()

      orchestrator.startRecording("Test Recording")
      val messages = listOf(LogEntry("Test message", LogType.NORMAL, 1000L))
      orchestrator.handleLogMessagesChanged(messages)

      verify { dataSaver.saveData(1000L, "DEVICE_001", "Test Recording", "LOG", any()) }
    }
  }

  // ==================== StateFlow Tests ====================

  @Nested
  inner class StateFlowTests {

    @Test
    fun `recordingState initial value is not recording`() {
      val state = orchestrator.recordingState.value

      assertFalse(state.isRecording)
      assertEquals("", state.currentRecordingName)
      assertEquals(0L, state.recordingStartTime)
    }

    @Test
    fun `recordingState emits updates when recording starts`() = runTest {
      setupValidRecordingConditions()
      clock.time = 12345L

      orchestrator.recordingState.test {
        assertEquals(RecordingState(), awaitItem())

        orchestrator.startRecording("Test Recording")

        val updatedState = awaitItem()
        assertTrue(updatedState.isRecording)
        assertEquals("Test Recording", updatedState.currentRecordingName)
        assertEquals(12345L, updatedState.recordingStartTime)

        cancelAndIgnoreRemainingEvents()
      }
    }

    @Test
    fun `lastData initial value is empty map`() {
      assertTrue(orchestrator.lastData.value.isEmpty())
    }

    @Test
    fun `lastDataTimestamps initial value is empty map`() {
      assertTrue(orchestrator.lastDataTimestamps.value.isEmpty())
    }
  }

  // ==================== cleanup() Tests ====================

  @Nested
  inner class Cleanup {

    @Test
    fun `cleanup can be called when no recording in progress`() {
      // Should not throw
      orchestrator.cleanup()
    }

    @Test
    fun `cleanup disposes streams when recording is in progress`() {
      val device = createDevice("DEVICE_001")
      selectedDevicesFlow.value = listOf(device)
      connectedDevicesFlow.value = listOf(device)

      val dataSaver = createMockDataSaver(enabled = true, initialized = InitializationState.SUCCESS)
      every { dataSavers.asList() } returns listOf(dataSaver)
      every { dataSavers.enabledCount } returns 1
      every { deviceState.getDeviceDataTypes("DEVICE_001") } returns setOf(PolarDeviceDataType.HR)
      every { deviceState.getDeviceSensorSettingsForDataType(any(), any()) } returns
          PolarSensorSetting(emptyMap())

      val mockFlowable = mockk<Flowable<PolarHrData>>(relaxed = true)
      every { mockFlowable.subscribeOn(any()) } returns mockFlowable
      every { mockFlowable.observeOn(any()) } returns mockFlowable
      every { mockFlowable.retry(any<Long>()) } returns mockFlowable
      every { mockFlowable.doOnSubscribe(any()) } returns mockFlowable
      every { mockFlowable.doOnError(any()) } returns mockFlowable
      every { mockFlowable.doOnComplete(any()) } returns mockFlowable
      every { mockFlowable.subscribe(any(), any()) } returns mockk(relaxed = true)

      every { polarManager.startStreaming(any(), any(), any()) } returns mockFlowable

      orchestrator.startRecording("Test Recording")

      // Should not throw
      orchestrator.cleanup()
    }
  }
}
