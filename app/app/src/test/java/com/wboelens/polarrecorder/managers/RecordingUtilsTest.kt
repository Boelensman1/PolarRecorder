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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for RecordingUtils - tests getDataFragment function for each supported Polar data
 * type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RecordingUtilsTest {

  @Test
  fun `getDataFragment HR returns heart rate`() {
    val hrSample = mockk<PolarHrData.PolarHrSample> { every { hr } returns 72 }
    val hrData = mockk<PolarHrData> { every { samples } returns listOf(hrSample) }

    val result = getDataFragment(PolarDeviceDataType.HR, hrData)

    assertEquals(72f, result)
  }

  @Test
  fun `getDataFragment HR returns null with empty samples`() {
    val hrData = mockk<PolarHrData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.HR, hrData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment PPI returns PPI value`() {
    val ppiSample = mockk<PolarPpiData.PolarPpiSample> { every { ppi } returns 850 }
    val ppiData = mockk<PolarPpiData> { every { samples } returns listOf(ppiSample) }

    val result = getDataFragment(PolarDeviceDataType.PPI, ppiData)

    assertEquals(850f, result)
  }

  @Test
  fun `getDataFragment PPI returns null with empty samples`() {
    val ppiData = mockk<PolarPpiData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.PPI, ppiData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment ACC returns x value`() {
    val accSample =
        mockk<PolarAccelerometerData.PolarAccelerometerDataSample> { every { x } returns 125 }
    val accData = mockk<PolarAccelerometerData> { every { samples } returns listOf(accSample) }

    val result = getDataFragment(PolarDeviceDataType.ACC, accData)

    assertEquals(125f, result)
  }

  @Test
  fun `getDataFragment ACC returns null with empty samples`() {
    val accData = mockk<PolarAccelerometerData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.ACC, accData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment PPG returns channel sample`() {
    val ppgSample =
        mockk<PolarPpgData.PolarPpgSample> {
          every { channelSamples } returns listOf(1000, 2000, 3000)
        }
    val ppgData = mockk<PolarPpgData> { every { samples } returns listOf(ppgSample) }

    val result = getDataFragment(PolarDeviceDataType.PPG, ppgData)

    assertEquals(3000f, result)
  }

  @Test
  fun `getDataFragment PPG returns null with empty samples`() {
    val ppgData = mockk<PolarPpgData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.PPG, ppgData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment ECG returns voltage`() {
    val ecgSample = mockk<EcgSample> { every { voltage } returns 1500 }
    val ecgData = mockk<PolarEcgData> { every { samples } returns listOf(ecgSample) }

    val result = getDataFragment(PolarDeviceDataType.ECG, ecgData)

    assertEquals(1500f, result)
  }

  @Test
  fun `getDataFragment ECG returns null with empty samples`() {
    val ecgData = mockk<PolarEcgData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.ECG, ecgData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment GYRO returns x value`() {
    val gyroSample = mockk<PolarGyroData.PolarGyroDataSample> { every { x } returns 45.5f }
    val gyroData = mockk<PolarGyroData> { every { samples } returns listOf(gyroSample) }

    val result = getDataFragment(PolarDeviceDataType.GYRO, gyroData)

    assertEquals(45.5f, result)
  }

  @Test
  fun `getDataFragment GYRO returns null with empty samples`() {
    val gyroData = mockk<PolarGyroData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.GYRO, gyroData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment TEMPERATURE returns temperature`() {
    val tempSample =
        mockk<PolarTemperatureData.PolarTemperatureDataSample> {
          every { temperature } returns 36.5f
        }
    val tempData = mockk<PolarTemperatureData> { every { samples } returns listOf(tempSample) }

    val result = getDataFragment(PolarDeviceDataType.TEMPERATURE, tempData)

    assertEquals(36.5f, result)
  }

  @Test
  fun `getDataFragment TEMPERATURE returns null with empty samples`() {
    val tempData = mockk<PolarTemperatureData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.TEMPERATURE, tempData)

    assertNull(result)
  }

  @Test
  fun `getDataFragment SKIN_TEMPERATURE returns temperature`() {
    val tempSample =
        mockk<PolarTemperatureData.PolarTemperatureDataSample> {
          every { temperature } returns 33.2f
        }
    val tempData = mockk<PolarTemperatureData> { every { samples } returns listOf(tempSample) }

    val result = getDataFragment(PolarDeviceDataType.SKIN_TEMPERATURE, tempData)

    assertEquals(33.2f, result)
  }

  @Test
  fun `getDataFragment MAGNETOMETER returns x value`() {
    val magSample =
        mockk<PolarMagnetometerData.PolarMagnetometerDataSample> { every { x } returns 12.3f }
    val magData = mockk<PolarMagnetometerData> { every { samples } returns listOf(magSample) }

    val result = getDataFragment(PolarDeviceDataType.MAGNETOMETER, magData)

    assertEquals(12.3f, result)
  }

  @Test
  fun `getDataFragment MAGNETOMETER returns null with empty samples`() {
    val magData = mockk<PolarMagnetometerData> { every { samples } returns emptyList() }

    val result = getDataFragment(PolarDeviceDataType.MAGNETOMETER, magData)

    assertNull(result)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `getDataFragment unsupported type throws IllegalArgumentException`() {
    val mockData = mockk<Any>()

    getDataFragment(PolarDeviceDataType.PRESSURE, mockData)
  }

  @Test
  fun `getDataFragment HR returns last sample when multiple samples`() {
    val hrSample1 = mockk<PolarHrData.PolarHrSample> { every { hr } returns 70 }
    val hrSample2 = mockk<PolarHrData.PolarHrSample> { every { hr } returns 75 }
    val hrSample3 = mockk<PolarHrData.PolarHrSample> { every { hr } returns 80 }
    val hrData =
        mockk<PolarHrData> { every { samples } returns listOf(hrSample1, hrSample2, hrSample3) }

    val result = getDataFragment(PolarDeviceDataType.HR, hrData)

    assertEquals(80f, result)
  }
}
