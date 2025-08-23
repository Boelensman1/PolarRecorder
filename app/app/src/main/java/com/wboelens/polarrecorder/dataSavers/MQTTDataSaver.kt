package com.wboelens.polarrecorder.dataSavers

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.viewModels.LogViewModel
import java.nio.charset.StandardCharsets
import java.util.UUID

data class MQTTConfig(
    val host: String = "",
    val port: Int,
    val useSSL: Boolean,
    val username: String? = null,
    val password: String? = null,
    val clientId: String = "PolarRecorder_${UUID.randomUUID()}",
    val topicPrefix: String = "polar_recorder",
)

class MQTTDataSaver(logViewModel: LogViewModel, preferencesManager: PreferencesManager) :
    DataSaver(logViewModel, preferencesManager) {
  private var mqttClient: Mqtt3AsyncClient? = null

  private lateinit var config: MQTTConfig

  override val isConfigured: Boolean
    get() = config.host.isNotEmpty()

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
        )

    this.config = sanitizedConfig
  }

  override fun enable() {
    if (config.host.isEmpty()) {
      logViewModel.addLogError("Broker host must be configured before starting")
      return
    }

    setEnabled(true)
  }

  override fun disable() {
    mqttClient?.let {
      try {
        if (it.state.isConnected) {
          it.disconnect()
        }
      } catch (e: Exception) {
        logViewModel.addLogError("Failed to disconnect from MQTT broker: ${e.message}")
      }
    }
    mqttClient = null
    setEnabled(false)
  }

  override fun initSaving(
      recordingName: String,
      deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>,
  ) {
    super.initSaving(recordingName, deviceIdsWithInfo)

    try {
      // Create the MQTT client using the separate host, port and SSL settings
      val clientBuilder =
          MqttClient.builder()
              .identifier(config.clientId)
              .serverHost(config.host)
              .serverPort(config.port)

      if (config.useSSL) {
        clientBuilder.sslWithDefaultConfig()
      }

      val client = clientBuilder.useMqttVersion3().build().toAsync()

      // Connect to the broker asynchronously
      val connectBuilder = client.connectWith().cleanSession(true).keepAlive(60)

      // Add credentials if provided
      if (!config.username.isNullOrEmpty()) {
        connectBuilder
            .simpleAuth()
            .username(config.username!!)
            .password(config.password?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf())
            .applySimpleAuth()
      }

      // Connect asynchronously
      connectBuilder.send().whenComplete { _, throwable ->
        if (throwable != null) {
          logViewModel.addLogError("Failed to connect to MQTT broker: ${throwable.message}")
          _isInitialized.value = InitializationState.FAILED
        } else {
          mqttClient = client

          /*.automaticReconnect(
          MqttClientAutoReconnect.builder()
              .initialDelay(1, TimeUnit.SECONDS)
              .maxDelay(5, TimeUnit.SECONDS)
              .build())*/
          logViewModel.addLogMessage("Connected to MQTT broker")
          _isInitialized.value = InitializationState.SUCCESS
        }
      }
    } catch (e: Exception) {
      logViewModel.addLogError("Failed to connect to MQTT broker: ${e.message}")
      _isInitialized.value = InitializationState.FAILED
    }
  }

  override fun saveData(
      phoneTimestamp: Long,
      deviceId: String,
      recordingName: String,
      dataType: String,
      data: Any,
  ) {
    val topic = "${config.topicPrefix}/$dataType/$deviceId"
    val payload = this.createJSONPayload(phoneTimestamp, deviceId, recordingName, dataType, data)

    try {
      mqttClient?.let { client ->
        client
            .publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(payload.toByteArray())
            .send()

        if (!firstMessageSaved["$deviceId/$dataType"]!!) {
          logViewModel.addLogMessage(
              "Successfully published first $dataType data to MQTT topic: $topic"
          )
          firstMessageSaved["$deviceId/$dataType"] = true
        }
      } ?: run { logViewModel.addLogError("MQTT client not initialized") }
    } catch (e: Exception) {
      logViewModel.addLogError("Failed to publish MQTT message: ${e.message}")
    }
  }

  override fun cleanup() {
    mqttClient?.let {
      try {
        if (it.state.isConnected) {
          it.disconnect()
        }
      } catch (e: Exception) {
        logViewModel.addLogError("Error during MQTT client cleanup: ${e.message}")
      }
    }
    mqttClient = null
  }
}
