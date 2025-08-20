package com.wboelens.polarrecorder

import android.app.Application
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.repository.LogRepository
import com.wboelens.polarrecorder.repository.PolarRepository

class PolarRecorderApplication : Application() {

  // Repositories crucial for the app
  // https://developer.android.com/topic/architecture/data-layer

  private lateinit var _logRepository: LogRepository
  private lateinit var _polarRepository: PolarRepository

  private lateinit var _polarManager: PolarManager

  private lateinit var _preferencesManager: PreferencesManager

  private lateinit var _dataSavers: DataSavers

  private lateinit var _recordingManager: RecordingManager

  val logRepository: LogRepository
    get() = _logRepository

  val polarRepository: PolarRepository
    get() = _polarRepository

  val polarManager: PolarManager
    get() = _polarManager

  val preferencesManager: PreferencesManager
    get() = _preferencesManager

  val dataSavers: DataSavers
    get() = _dataSavers

  val recordingManager: RecordingManager
    get() = _recordingManager

  override fun onCreate() {
    super.onCreate()

    _logRepository = LogRepository()
    _polarRepository = PolarRepository()

    _polarManager = PolarManager(applicationContext, polarRepository, logRepository)
    _preferencesManager = PreferencesManager(applicationContext)
    _dataSavers = DataSavers(applicationContext, logRepository, preferencesManager)

    _recordingManager = RecordingManager(
        applicationContext,
        polarManager,
        logRepository,
        polarRepository,
        preferencesManager,
        dataSavers,
    )
  }

  override fun onTerminate() {
    super.onTerminate()

    logRepository.cleanup()
    polarRepository.cleanup()
  }
}
