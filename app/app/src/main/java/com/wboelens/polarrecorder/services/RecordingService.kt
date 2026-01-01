package com.wboelens.polarrecorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.polar.sdk.api.PolarBleApi
import com.wboelens.polarrecorder.PolarRecorderApplication
import com.wboelens.polarrecorder.recording.RecordingOrchestrator
import com.wboelens.polarrecorder.recording.StartRecordingResult
import com.wboelens.polarrecorder.state.LogState
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service for background recording. This service handles Android lifecycle and
 * notification concerns, while delegating all recording business logic to RecordingOrchestrator.
 */
@Suppress("TooManyFunctions")
class RecordingService : Service() {
  companion object {
    const val ACTION_START_RECORDING = "com.wboelens.polarrecorder.START_RECORDING"
    const val ACTION_STOP_RECORDING = "com.wboelens.polarrecorder.STOP_RECORDING"
    const val EXTRA_RECORDING_NAME = "recording_name"
    const val EXTRA_DEVICE_IDS = "device_ids"
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "RecordingServiceChannel"
  }

  private val executor = Executors.newSingleThreadScheduledExecutor()

  // Dependencies (initialized in onCreate)
  private lateinit var orchestrator: RecordingOrchestrator
  private lateinit var logState: LogState

  // Coroutine scope for state observation
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private var connectedDevicesJob: Job? = null
  private var logMessagesJob: Job? = null

  // Binder
  private val binder = LocalBinder()

  inner class LocalBinder : Binder() {
    val recordingState: StateFlow<RecordingState>
      get() = orchestrator.recordingState

    val lastData: StateFlow<Map<String, Map<PolarBleApi.PolarDeviceDataType, Float?>>>
      get() = orchestrator.lastData

    val lastDataTimestamps: StateFlow<Map<String, Long>>
      get() = orchestrator.lastDataTimestamps

    fun startRecording(recordingName: String) {
      this@RecordingService.doStartRecording(recordingName)
    }

    fun stopRecording() {
      this@RecordingService.doStopRecording()
    }

    fun getService(): RecordingService = this@RecordingService
  }

  private val app: PolarRecorderApplication
    get() = application as PolarRecorderApplication

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    initializeDependencies()
    startObservingDeviceChanges()
    startObservingLogMessages()
  }

  private fun initializeDependencies() {
    app.ensureManagersInitialized()
    orchestrator = app.recordingOrchestrator!!
    logState = app.logState
  }

  private fun startObservingDeviceChanges() {
    connectedDevicesJob =
        scope.launch {
          app.deviceState.connectedDevices.collect { devices ->
            val recordingStopped = orchestrator.handleDevicesChanged(devices)
            if (recordingStopped) {
              stopServiceAfterRecordingEnded()
            }
          }
        }
  }

  private fun startObservingLogMessages() {
    logMessagesJob =
        scope.launch {
          logState.logMessages.collect { messages ->
            orchestrator.handleLogMessagesChanged(messages)
          }
        }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_RECORDING -> {
        val name = intent.getStringExtra(EXTRA_RECORDING_NAME)
        if (name != null) {
          doStartRecording(name)
        }
      }
      ACTION_STOP_RECORDING -> doStopRecording()
      else -> {
        // Service started without action - show notification if recording
        if (orchestrator.recordingState.value.isRecording) {
          val notification = createNotification()
          startForeground(NOTIFICATION_ID, notification)
        }
      }
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder = binder

  private fun doStartRecording(recordingName: String) {
    val result = orchestrator.startRecording(recordingName)

    if (result is StartRecordingResult.Success) {
      // Service-specific: start foreground notification
      val notification = createNotification()
      startForeground(NOTIFICATION_ID, notification)
      scheduleNotificationUpdates()
    }
    // Errors are already logged by orchestrator
  }

  private fun doStopRecording() {
    logState.requestFlushQueue()

    Handler(Looper.getMainLooper()).post {
      orchestrator.stopRecording()
      stopServiceAfterRecordingEnded()
    }
  }

  private fun stopServiceAfterRecordingEnded() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun scheduleNotificationUpdates() {
    executor.scheduleWithFixedDelay(
        {
          val notification = createNotification()
          val notificationManager =
              getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          notificationManager.notify(NOTIFICATION_ID, notification)
        },
        1,
        1,
        TimeUnit.MINUTES,
    )
  }

  private fun createNotificationChannel() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(
              CHANNEL_ID,
              "Recording Service Channel",
              NotificationManager.IMPORTANCE_LOW,
          )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    val durationMs =
        System.currentTimeMillis() - orchestrator.recordingState.value.recordingStartTime
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationText =
        if (minutes == 1L) {
          "1 minute"
        } else {
          "$minutes minutes"
        }

    val pendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Recording in progress")
        .setContentText("Recording for $durationText")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .build()
  }

  override fun onDestroy() {
    // Cancel coroutine jobs
    connectedDevicesJob?.cancel()
    logMessagesJob?.cancel()

    // Cleanup orchestrator resources
    orchestrator.cleanup()

    executor.shutdownNow()
    super.onDestroy()
  }
}
