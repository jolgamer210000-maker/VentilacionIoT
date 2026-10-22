package com.example.ventilacioniot

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import java.util.UUID

class MqttManager(
    private val onMensaje: (String, String) -> Unit,
    private val onEstado: (String) -> Unit
) {
    private var client: Mqtt3AsyncClient? = null

    fun conectar(prefijo: String, topics: List<String>) {
        val c = MqttClient.builder()
            .useMqttVersion3()
            .identifier("$prefijo-${UUID.randomUUID().toString().take(8)}")
            .serverHost(BuildConfig.MQTT_HOST)
            .serverPort(8883)
            .sslWithDefaultConfig()
            .buildAsync()
        client = c

        c.connectWith()
            .simpleAuth()
            .username(BuildConfig.MQTT_USER)
            .password(BuildConfig.MQTT_PASS.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { _, error ->
                if (error != null) {
                    onEstado("Error: ${error.message}")
                } else {
                    onEstado("Conectado")
                    topics.forEach { suscribir(it) }
                }
            }
    }

    private fun suscribir(topic: String) {
        client?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                onMensaje(publish.topic.toString(), String(publish.payloadAsBytes))
            }
            ?.send()
    }

    fun publicar(topic: String, mensaje: String) {
        client?.publishWith()
            ?.topic(topic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.payload(mensaje.toByteArray())
            ?.send()
    }

    fun desconectar() {
        client?.disconnect()
    }
}