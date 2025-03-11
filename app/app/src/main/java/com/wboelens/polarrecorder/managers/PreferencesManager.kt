package com.wboelens.polarrecorder.managers

import android.content.Context
import android.content.SharedPreferences
import com.wboelens.polarrecorder.dataSavers.FileSystemDataSaverConfig
import com.wboelens.polarrecorder.dataSavers.MQTTConfig

data class PreferenceConfig<T>(val key: String, val defaultValue: T)

object Preferences {
  val MQTT_BROKER_URL = PreferenceConfig("mqtt_broker_url", "")
  val MQTT_USERNAME = PreferenceConfig("mqtt_username", "")
  val MQTT_PASSWORD = PreferenceConfig("mqtt_password", "")
  val MQTT_CLIENT_ID = PreferenceConfig("mqtt_client_id", "")
  val MQTT_TOPIC_PREFIX = PreferenceConfig("mqtt_topic_prefix", "")
  val MQTT_ENABLED = PreferenceConfig("mqtt_enabled", false)

  val FILE_SYSTEM_BASE_DIRECTORY = PreferenceConfig("file_system_base_directory", "")
  @Suppress("MagicNumber")
  val FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB =
      PreferenceConfig("file_system_recording_split_at_size_mb", 20)
  val FILE_SYSTEM_ENABLED = PreferenceConfig("file_system_enabled", false)

  val RECORDING_NAME = PreferenceConfig("recording_name", "PolarRecording")
  val RECORDING_NAME_APPEND_TIMESTAMP = PreferenceConfig("recording_name_append_timestamp", true)
}

class PreferencesManager(context: Context) {
  companion object {
    private var PREF_NAME = "com.wboelens.polarrecorder.PREFS"
  }

  private val mPref: SharedPreferences =
      context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

  var mqttConfig: MQTTConfig
    get() {
      return MQTTConfig(
          brokerUrl =
              mPref.getString(
                  Preferences.MQTT_BROKER_URL.key, Preferences.MQTT_BROKER_URL.defaultValue)!!,
          username =
              mPref.getString(
                  Preferences.MQTT_USERNAME.key, Preferences.MQTT_USERNAME.defaultValue)!!,
          password =
              mPref.getString(
                  Preferences.MQTT_PASSWORD.key, Preferences.MQTT_PASSWORD.defaultValue)!!,
          clientId =
              mPref.getString(
                  Preferences.MQTT_CLIENT_ID.key, Preferences.MQTT_CLIENT_ID.defaultValue)!!,
          topicPrefix =
              mPref.getString(
                  Preferences.MQTT_TOPIC_PREFIX.key, Preferences.MQTT_TOPIC_PREFIX.defaultValue)!!)
    }
    set(config) {
      mPref.edit().apply {
        putString(Preferences.MQTT_BROKER_URL.key, config.brokerUrl)
        putString(Preferences.MQTT_USERNAME.key, config.username)
        putString(Preferences.MQTT_PASSWORD.key, config.password)
        putString(Preferences.MQTT_TOPIC_PREFIX.key, config.topicPrefix)
        putString(Preferences.MQTT_CLIENT_ID.key, config.clientId)
        apply()
      }
    }

  var mqttEnabled: Boolean
    get() = mPref.getBoolean(Preferences.MQTT_ENABLED.key, Preferences.MQTT_ENABLED.defaultValue)
    set(enabled) {
      mPref.edit().putBoolean(Preferences.MQTT_ENABLED.key, enabled).apply()
    }

  var fileSystemDataSaverConfig: FileSystemDataSaverConfig
    get() {
      return FileSystemDataSaverConfig(
          baseDirectory =
              mPref.getString(
                  Preferences.FILE_SYSTEM_BASE_DIRECTORY.key,
                  Preferences.FILE_SYSTEM_BASE_DIRECTORY.defaultValue)!!,
          splitAtSizeMb =
              mPref.getInt(
                  Preferences.FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB.key,
                  Preferences.FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB.defaultValue))
    }
    set(config) {
      mPref.edit().apply {
        putString(Preferences.FILE_SYSTEM_BASE_DIRECTORY.key, config.baseDirectory)
        putInt(Preferences.FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB.key, config.splitAtSizeMb)
        apply()
      }
    }

  var fileSystemEnabled: Boolean
    get() =
        mPref.getBoolean(
            Preferences.FILE_SYSTEM_ENABLED.key, Preferences.FILE_SYSTEM_ENABLED.defaultValue)
    set(enabled) {
      mPref.edit().putBoolean(Preferences.FILE_SYSTEM_ENABLED.key, enabled).apply()
    }

  var recordingName: String
    get() =
        mPref.getString(Preferences.RECORDING_NAME.key, Preferences.RECORDING_NAME.defaultValue)!!
    set(name) {
      mPref.edit().putString(Preferences.RECORDING_NAME.key, name).apply()
    }

  var recordingNameAppendTimestamp: Boolean
    get() =
        mPref.getBoolean(
            Preferences.RECORDING_NAME_APPEND_TIMESTAMP.key,
            Preferences.RECORDING_NAME_APPEND_TIMESTAMP.defaultValue)
    set(enabled) {
      mPref.edit().putBoolean(Preferences.RECORDING_NAME_APPEND_TIMESTAMP.key, enabled).apply()
    }
}
