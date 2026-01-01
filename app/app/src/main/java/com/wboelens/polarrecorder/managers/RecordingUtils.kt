package com.wboelens.polarrecorder.managers

import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarTemperatureData

data class DeviceInfoForDataSaver(val deviceName: String, val dataTypes: Set<String>)

fun getDataFragment(dataType: PolarBleApi.PolarDeviceDataType, data: Any): Float? {
  return when (dataType) {
    PolarBleApi.PolarDeviceDataType.HR -> (data as PolarHrData).samples.lastOrNull()?.hr?.toFloat()
    PolarBleApi.PolarDeviceDataType.PPI ->
        (data as PolarPpiData).samples.lastOrNull()?.ppi?.toFloat()
    PolarBleApi.PolarDeviceDataType.ACC ->
        (data as PolarAccelerometerData).samples.lastOrNull()?.x?.toFloat()
    PolarBleApi.PolarDeviceDataType.PPG ->
        (data as PolarPpgData).samples.firstOrNull()?.channelSamples?.lastOrNull()?.toFloat()
    PolarBleApi.PolarDeviceDataType.ECG -> {
      val ecgData = data as PolarEcgData
      when (val ecgSample = ecgData.samples.lastOrNull()) {
        is EcgSample -> ecgSample.voltage.toFloat()
        else -> null
      }
    }
    PolarBleApi.PolarDeviceDataType.GYRO -> (data as PolarGyroData).samples.lastOrNull()?.x
    PolarBleApi.PolarDeviceDataType.TEMPERATURE ->
        (data as PolarTemperatureData).samples.lastOrNull()?.temperature
    PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE ->
        (data as PolarTemperatureData).samples.lastOrNull()?.temperature
    PolarBleApi.PolarDeviceDataType.MAGNETOMETER ->
        (data as PolarMagnetometerData).samples.lastOrNull()?.x
    else -> throw IllegalArgumentException("Unsupported data type: $dataType")
  }
}
