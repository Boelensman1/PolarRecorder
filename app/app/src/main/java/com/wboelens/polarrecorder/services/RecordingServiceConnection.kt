package com.wboelens.polarrecorder.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.polar.sdk.api.PolarBleApi
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordingServiceConnection(private val context: Context) {
  private val _binder = MutableStateFlow<RecordingService.LocalBinder?>(null)
  val binder: StateFlow<RecordingService.LocalBinder?> = _binder.asStateFlow()

  private var isBound = false

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          _binder.value = service as RecordingService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          _binder.value = null
        }
      }

  fun bind() {
    if (!isBound) {
      val intent = Intent(context, RecordingService::class.java)
      context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
      isBound = true
    }
  }

  fun unbind() {
    if (isBound) {
      context.unbindService(connection)
      isBound = false
      _binder.value = null
    }
  }

  fun startRecordingService(
      recordingName: String,
      deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>,
  ) {
    val intent =
        Intent(context, RecordingService::class.java).apply {
          action = RecordingService.ACTION_START_RECORDING
          putExtra(RecordingService.EXTRA_RECORDING_NAME, recordingName)
          putExtra(
              RecordingService.EXTRA_DEVICE_IDS,
              ArrayList(deviceIdsWithInfo.keys.toList()),
          )
        }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      context.startForegroundService(intent)
    } else {
      context.startService(intent)
    }
  }

  fun stopRecordingService() {
    _binder.value?.stopRecording()
  }

  // Convenience accessors for state flows
  val recordingState: StateFlow<RecordingState>?
    get() = _binder.value?.recordingState

  val isRecording: Boolean
    get() = _binder.value?.recordingState?.value?.isRecording == true

  val lastData: StateFlow<Map<String, Map<PolarBleApi.PolarDeviceDataType, Float?>>>?
    get() = _binder.value?.lastData

  val lastDataTimestamps: StateFlow<Map<String, Long>>?
    get() = _binder.value?.lastDataTimestamps
}
