package com.wboelens.polarrecorder.managers

import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarTemperatureData
import io.mockk.every
import io.mockk.mockk
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Unit tests for RecordingUtils - tests getDataFragment function for each supported Polar data
 * type. Uses parameterized tests for DRY coverage of all data types.
 */
class RecordingUtilsTest {

  companion object {
    // Mock data creators
    private fun createHrMock(hr: Int): PolarHrData {
      val sample = mockk<PolarHrData.PolarHrSample> { every { this@mockk.hr } returns hr }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyHrMock(): PolarHrData = mockk { every { samples } returns emptyList() }

    private fun createPpiMock(ppi: Int): PolarPpiData {
      val sample = mockk<PolarPpiData.PolarPpiSample> { every { this@mockk.ppi } returns ppi }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyPpiMock(): PolarPpiData = mockk { every { samples } returns emptyList() }

    private fun createAccMock(x: Int): PolarAccelerometerData {
      val sample =
          mockk<PolarAccelerometerData.PolarAccelerometerDataSample> {
            every { this@mockk.x } returns x
          }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyAccMock(): PolarAccelerometerData = mockk {
      every { samples } returns emptyList()
    }

    private fun createPpgMock(lastChannelValue: Int): PolarPpgData {
      val sample =
          mockk<PolarPpgData.PolarPpgSample> {
            every { channelSamples } returns listOf(1000, 2000, lastChannelValue)
          }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyPpgMock(): PolarPpgData = mockk { every { samples } returns emptyList() }

    private fun createEcgMock(voltage: Int): PolarEcgData {
      val sample = mockk<EcgSample> { every { this@mockk.voltage } returns voltage }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyEcgMock(): PolarEcgData = mockk { every { samples } returns emptyList() }

    private fun createGyroMock(x: Float): PolarGyroData {
      val sample = mockk<PolarGyroData.PolarGyroDataSample> { every { this@mockk.x } returns x }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyGyroMock(): PolarGyroData = mockk {
      every { samples } returns emptyList()
    }

    private fun createTempMock(temp: Float): PolarTemperatureData {
      val sample =
          mockk<PolarTemperatureData.PolarTemperatureDataSample> {
            every { temperature } returns temp
          }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyTempMock(): PolarTemperatureData = mockk {
      every { samples } returns emptyList()
    }

    private fun createMagMock(x: Float): PolarMagnetometerData {
      val sample =
          mockk<PolarMagnetometerData.PolarMagnetometerDataSample> {
            every { this@mockk.x } returns x
          }
      return mockk { every { samples } returns listOf(sample) }
    }

    private fun createEmptyMagMock(): PolarMagnetometerData = mockk {
      every { samples } returns emptyList()
    }

    @JvmStatic
    fun dataTypeTestCases(): Stream<Arguments> =
        Stream.of(
            Arguments.of(PolarDeviceDataType.HR, createHrMock(72), 72f),
            Arguments.of(PolarDeviceDataType.PPI, createPpiMock(850), 850f),
            Arguments.of(PolarDeviceDataType.ACC, createAccMock(125), 125f),
            Arguments.of(PolarDeviceDataType.PPG, createPpgMock(3000), 3000f),
            Arguments.of(PolarDeviceDataType.ECG, createEcgMock(1500), 1500f),
            Arguments.of(PolarDeviceDataType.GYRO, createGyroMock(45.5f), 45.5f),
            Arguments.of(PolarDeviceDataType.TEMPERATURE, createTempMock(36.5f), 36.5f),
            Arguments.of(PolarDeviceDataType.SKIN_TEMPERATURE, createTempMock(33.2f), 33.2f),
            Arguments.of(PolarDeviceDataType.MAGNETOMETER, createMagMock(12.3f), 12.3f),
        )

    @JvmStatic
    fun emptyDataTestCases(): Stream<Arguments> =
        Stream.of(
            Arguments.of(PolarDeviceDataType.HR, createEmptyHrMock()),
            Arguments.of(PolarDeviceDataType.PPI, createEmptyPpiMock()),
            Arguments.of(PolarDeviceDataType.ACC, createEmptyAccMock()),
            Arguments.of(PolarDeviceDataType.PPG, createEmptyPpgMock()),
            Arguments.of(PolarDeviceDataType.ECG, createEmptyEcgMock()),
            Arguments.of(PolarDeviceDataType.GYRO, createEmptyGyroMock()),
            Arguments.of(PolarDeviceDataType.TEMPERATURE, createEmptyTempMock()),
            Arguments.of(PolarDeviceDataType.MAGNETOMETER, createEmptyMagMock()),
        )

    @JvmStatic
    fun multiSampleTestCases(): Stream<Arguments> {
      // HR with multiple samples - should return last
      val hrSample1 = mockk<PolarHrData.PolarHrSample> { every { hr } returns 70 }
      val hrSample2 = mockk<PolarHrData.PolarHrSample> { every { hr } returns 75 }
      val hrSample3 = mockk<PolarHrData.PolarHrSample> { every { hr } returns 80 }
      val hrData =
          mockk<PolarHrData> { every { samples } returns listOf(hrSample1, hrSample2, hrSample3) }

      return Stream.of(Arguments.of(PolarDeviceDataType.HR, hrData, 80f))
    }
  }

  @ParameterizedTest(name = "getDataFragment {0} returns correct value")
  @MethodSource("dataTypeTestCases")
  fun `getDataFragment returns correct value for data type`(
      dataType: PolarDeviceDataType,
      mockData: Any,
      expectedValue: Float
  ) {
    val result = getDataFragment(dataType, mockData)

    assertEquals(expectedValue, result)
  }

  @ParameterizedTest(name = "getDataFragment {0} returns null for empty samples")
  @MethodSource("emptyDataTestCases")
  fun `getDataFragment returns null for empty samples`(
      dataType: PolarDeviceDataType,
      emptyMockData: Any
  ) {
    val result = getDataFragment(dataType, emptyMockData)

    assertNull(result)
  }

  @ParameterizedTest(name = "getDataFragment {0} returns last sample when multiple")
  @MethodSource("multiSampleTestCases")
  fun `getDataFragment returns last sample when multiple samples`(
      dataType: PolarDeviceDataType,
      mockData: Any,
      expectedValue: Float
  ) {
    val result = getDataFragment(dataType, mockData)

    assertEquals(expectedValue, result)
  }

  @org.junit.jupiter.api.Test
  fun `getDataFragment unsupported type throws IllegalArgumentException`() {
    val mockData = mockk<Any>()

    assertThrows<IllegalArgumentException> {
      getDataFragment(PolarDeviceDataType.PRESSURE, mockData)
    }
  }
}
