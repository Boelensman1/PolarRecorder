package com.wboelens.polarrecorder.dataSavers

import android.content.Context
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.state.LogState

class DataSavers(
    context: Context,
    logState: LogState,
    preferencesManager: PreferencesManager,
) {
  val mqtt: MQTTDataSaver = MQTTDataSaver(logState, preferencesManager)
  val fileSystem: FileSystemDataSaver = FileSystemDataSaver(context, logState, preferencesManager)

  private val savers = mutableListOf<DataSaver>()

  init {
    mqtt.configure(preferencesManager.mqttConfig)
    if (preferencesManager.mqttEnabled) {
      mqtt.enable()
    }
    savers.add(mqtt)

    fileSystem.configure(preferencesManager.fileSystemDataSaverConfig)
    if (preferencesManager.fileSystemEnabled) {
      fileSystem.enable()
    }
    savers.add(fileSystem)
  }

  fun iterator(): Iterator<DataSaver> = savers.iterator()

  fun asList(): List<DataSaver> = savers.toList()

  val enabledCount: Int
    get() = savers.count { it.isEnabled.value }
}
