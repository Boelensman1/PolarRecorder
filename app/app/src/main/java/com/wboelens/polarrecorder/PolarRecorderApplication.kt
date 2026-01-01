package com.wboelens.polarrecorder

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.recording.AndroidAppInfoProvider
import com.wboelens.polarrecorder.recording.RecordingOrchestrator
import com.wboelens.polarrecorder.services.RecordingService
import com.wboelens.polarrecorder.services.RecordingServiceConnection
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.state.LogState

/**
 * Application class that holds Application-scoped state and managers. This ensures that recording
 * infrastructure survives Activity restarts during long recording sessions.
 */
class PolarRecorderApplication : Application() {
  // Application-scoped state holders (survive Activity restart)
  val deviceState = DeviceState()
  val logState = LogState()

  // Preferences are initialized immediately as they have no dependencies
  lateinit var preferencesManager: PreferencesManager
    private set

  // Managers are initialized lazily when first Activity starts
  private var _polarManager: PolarManager? = null
  private var _dataSavers: DataSavers? = null
  private var _recordingOrchestrator: RecordingOrchestrator? = null

  val polarManager: PolarManager?
    get() = _polarManager

  val dataSavers: DataSavers?
    get() = _dataSavers

  val recordingOrchestrator: RecordingOrchestrator?
    get() = _recordingOrchestrator

  // Service connection for recording control
  private var _serviceConnection: RecordingServiceConnection? = null

  override fun onCreate() {
    super.onCreate()
    preferencesManager = PreferencesManager(applicationContext)
  }

  /**
   * Initialize managers if they haven't been initialized yet. Called from MainActivity.onCreate().
   * Uses Application-scoped state classes so managers survive Activity restarts.
   */
  fun ensureManagersInitialized() {
    if (_polarManager == null) {
      _dataSavers = DataSavers(applicationContext, logState, preferencesManager)
      _polarManager = PolarManager(applicationContext, deviceState, logState)
      _recordingOrchestrator =
          RecordingOrchestrator(
              polarManager = _polarManager!!,
              deviceState = deviceState,
              logState = logState,
              preferencesManager = preferencesManager,
              dataSavers = _dataSavers!!,
              appInfoProvider = AndroidAppInfoProvider(applicationContext),
          )
    }
  }

  /** Get or create the service connection for recording control. */
  fun getServiceConnection(): RecordingServiceConnection {
    if (_serviceConnection == null) {
      _serviceConnection = RecordingServiceConnection(this)
    }
    return _serviceConnection!!
  }

  /** Check if a recording is currently active by checking if the service is running. */
  val isRecordingActive: Boolean
    get() {
      // First check if we have a bound connection with state
      _serviceConnection?.binder?.value?.let { binder ->
        return binder.recordingState.value.isRecording
      }
      // Fall back to checking if service is running
      return isServiceRunning(RecordingService::class.java)
    }

  @Suppress("DEPRECATION")
  private fun isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
      if (serviceClass.name == service.service.className) {
        return true
      }
    }
    return false
  }

  /**
   * Cleanup managers when no recording is active. This should be called when the app is being
   * closed and no recording is in progress.
   */
  fun cleanupIfNotRecording() {
    if (!isRecordingActive) {
      _recordingOrchestrator?.cleanup()
      _polarManager?.cleanup()
      deviceState.cleanup()
      _polarManager = null
      _dataSavers = null
      _recordingOrchestrator = null
    }
  }
}
