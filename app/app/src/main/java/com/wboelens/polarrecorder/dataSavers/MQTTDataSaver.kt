package com.wboelens.polarrecorder.dataSavers

import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.viewModels.LogViewModel
import java.util.UUID
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

data class MQTTConfig(
    val brokerUrl: String = "",
    val username: String? = null,
    val password: String? = null,
    val clientId: String = "PolarRecorder_${UUID.randomUUID()}",
    val topicPrefix: String = "polar_recorder"
)

class MQTTDataSaver(logViewModel: LogViewModel, preferencesManager: PreferencesManager) :
    DataSaver(logViewModel, preferencesManager), MqttCallback {
  private lateinit var mqttClient: MqttClient
  private var brokerOptions: MqttConnectOptions? = null

  private var config: MQTTConfig = MQTTConfig()

  override val isConfigured: Boolean
    get() = config.brokerUrl.isNotEmpty()

  private fun setEnabled(enable: Boolean) {
    _isEnabled.value = enable
    preferencesManager.mqttEnabled = enable
  }

  fun configure(config: MQTTConfig) {
    preferencesManager.mqttConfig = config

    val sanitizedConfig =
        config.copy(
            // Convert empty strings to null
            username = config.username?.takeIf { it.isNotEmpty() },
            password = config.password?.takeIf { it.isNotEmpty() },
            // Replace mqtt url with tcp
            brokerUrl = config.brokerUrl.replace("^mqtt://".toRegex(), "tcp://"))

    this.config = sanitizedConfig

    brokerOptions =
        MqttConnectOptions().apply {
          isCleanSession = true
          sanitizedConfig.username?.let { userName = it }
          sanitizedConfig.password?.let { password = it.toCharArray() }
        }
  }

  override fun enable() {
    if (config.brokerUrl.isEmpty()) {
      this.addErrorMessage("Broker URL must be configured before starting")
      return
    }

    setEnabled(true)
  }

  override fun disable() {
    if (::mqttClient.isInitialized && mqttClient.isConnected) {
      try {
        mqttClient.disconnect()
        mqttClient.close()

        setEnabled(false)
      } catch (e: MqttException) {
        logViewModel.addLogError("Failed to disconnect from MQTT broker: ${e.message}")
      }
    }
  }

  override fun initSaving(
      recordingName: String,
      deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>
  ) {
    super.initSaving(recordingName, deviceIdsWithInfo)

    try {
      mqttClient = MqttClient(config.brokerUrl, config.clientId, MemoryPersistence())
      mqttClient.setCallback(this)
      val connOpts = brokerOptions ?: MqttConnectOptions().apply { isCleanSession = true }

      mqttClient.connect(connOpts)

      logViewModel.addLogMessage("Connected to MQTT broker")
    } catch (e: MqttException) {
      this.addErrorMessage("Failed to connect to MQTT broker: ${e.message}")
    }
  }

  override fun saveData(
      phoneTimestamp: Long,
      deviceId: String,
      recordingName: String,
      dataType: String,
      payload: String
  ) {
    val topic = "${config.topicPrefix}/$dataType/$deviceId"

    try {
      val message =
          MqttMessage(payload.toByteArray()).apply {
            qos = 1 // At least once delivery
            isRetained = false
          }
      mqttClient.publish(topic, message)
      if (!firstMessageSaved["$deviceId/$dataType"]!!) {
        logViewModel.addLogMessage(
            "Successfully published $dataType first data to MQTT topic: $topic")
        firstMessageSaved["$deviceId/$dataType"] = true
      }
    } catch (e: MqttException) {
      logViewModel.addLogError("Failed to publish MQTT message: ${e.message}")
    }
  }

  override fun cleanup() {
    if (::mqttClient.isInitialized) {
      mqttClient.close(true)
    }
  }

  // MqttCallbacks
  override fun connectionLost(cause: Throwable?) {
    this.addErrorMessage("MQTT connection lost: ${cause?.message}")
  }

  override fun messageArrived(topic: String?, message: MqttMessage?) {
    // no-op
  }

  override fun deliveryComplete(token: IMqttDeliveryToken?) {
    // no-op
  }
}
