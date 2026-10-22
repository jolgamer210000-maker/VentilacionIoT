package com.example.ventilacioniot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class DispositivoActivity : AppCompatActivity() {

    private lateinit var mqtt: MqttManager
    private lateinit var tvConexion: TextView
    private lateinit var tvDatos: TextView
    private lateinit var tvUltimoComando: TextView
    private lateinit var btnConectar: Button

    private val handler = Handler(Looper.getMainLooper())

    private var conectado = false
    private var temperatura = 27.0
    private var objetivo = 25.0
    private var ventilador = false
    private var velocidad = 1
    private var modo = "AUTO"

    private val simulacion = object : Runnable {
        override fun run() {
            if (conectado) {
                actualizarVentiladorAutomatico()

                // Simula enfriamiento o calentamiento.
                temperatura += if (ventilador) {
                    -0.3 * velocidad
                } else {
                    0.2
                }

                temperatura = temperatura.coerceIn(5.0, 45.0)

                actualizarVentiladorAutomatico()
                mostrarDatos()
                publicarEstado()
            }

            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usuario = getSharedPreferences("sesion", MODE_PRIVATE)
            .getString("usuario", null)

        if (usuario.isNullOrBlank()) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_dispositivo)

        tvConexion = findViewById(R.id.tvConexionDispositivo)
        tvDatos = findViewById(R.id.tvDatosDispositivo)
        tvUltimoComando = findViewById(R.id.tvUltimoComando)
        btnConectar = findViewById(R.id.btnConectarDispositivo)

        mqtt = MqttManager(
            onMensaje = { topic, mensaje ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed &&
                        topic == "ventilacion/comando"
                    ) {
                        procesarComando(mensaje)
                    }
                }
            },
            onEstado = { estado ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        conectado = estado == "Conectado"
                        tvConexion.text = estado
                        btnConectar.isEnabled = !conectado

                        if (conectado) {
                            actualizarVentiladorAutomatico()
                            mostrarDatos()
                            publicarEstado()
                        }
                    }
                }
            }
        )

        btnConectar.setOnClickListener {
            conectar()
        }

        findViewById<Button>(R.id.btnVolverDispositivo)
            .setOnClickListener {
                finish()
            }

        actualizarVentiladorAutomatico()
        mostrarDatos()
        handler.postDelayed(simulacion, 2000)
    }

    private fun conectar() {
        if (BuildConfig.MQTT_HOST.isBlank() ||
            BuildConfig.MQTT_USER.isBlank() ||
            BuildConfig.MQTT_PASS.isBlank()
        ) {
            Toast.makeText(
                this,
                "Primero configura los datos del broker",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        btnConectar.isEnabled = false
        tvConexion.text = "Conectando..."

        try {
            mqtt.conectar(
                "dispositivo",
                listOf("ventilacion/comando")
            )
        } catch (e: Exception) {
            conectado = false
            btnConectar.isEnabled = true
            tvConexion.text = "No se pudo conectar: ${e.message}"
        }
    }

    private fun actualizarVentiladorAutomatico() {
        if (modo == "AUTO") {
            if (temperatura > objetivo) {
                ventilador = true
            } else if (temperatura <= objetivo - 0.5) {
                ventilador = false
            }
        }
    }

    private fun procesarComando(mensaje: String) {
        val partes = mensaje.trim().split(":", limit = 2)
        if (partes.size != 2) return

        val orden = partes[0]
        val valor = partes[1]

        when (orden) {
            "MODE" -> {
                if (valor != "AUTO" && valor != "MANUAL") return
                modo = valor
            }

            "FAN" -> {
                if (valor != "ON" && valor != "OFF") return

                if (modo != "MANUAL") {
                    tvUltimoComando.text =
                        "Orden ignorada: selecciona modo manual"
                    publicarEstado()
                    return
                }

                ventilador = valor == "ON"
            }

            "SPEED" -> {
                val nuevaVelocidad = valor.toIntOrNull() ?: return
                if (nuevaVelocidad !in 1..3) return
                velocidad = nuevaVelocidad
            }

            "TARGET" -> {
                val nuevoObjetivo = valor.toDoubleOrNull() ?: return
                if (!nuevoObjetivo.isFinite() ||
                    nuevoObjetivo !in 10.0..40.0
                ) return

                objetivo = nuevoObjetivo
            }

            else -> return
        }

        tvUltimoComando.text = "Última orden: $mensaje"

        actualizarVentiladorAutomatico()
        mostrarDatos()
        publicarEstado()
    }

    private fun mostrarDatos() {
        val tempTexto = String.format(Locale.US, "%.1f", temperatura)
        val objetivoTexto = String.format(Locale.US, "%.1f", objetivo)
        val estadoVentilador =
            if (ventilador) "Encendido" else "Apagado"

        tvDatos.text = """
            Temperatura: $tempTexto °C
            Ventilador: $estadoVentilador
            Velocidad: $velocidad
            Modo: $modo
            Objetivo: $objetivoTexto °C
        """.trimIndent()
    }

    private fun publicarEstado() {
        if (!conectado) return

        val tempTexto = String.format(Locale.US, "%.1f", temperatura)
        val objetivoTexto = String.format(Locale.US, "%.1f", objetivo)
        val fanTexto = if (ventilador) "ON" else "OFF"

        val mensaje = "temp=$tempTexto;fan=$fanTexto;" +
                "speed=$velocidad;mode=$modo;target=$objetivoTexto"

        try {
            mqtt.publicar("ventilacion/estado", mensaje)
        } catch (e: Exception) {
            tvConexion.text = "Error al publicar: ${e.message}"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(simulacion)

        if (::mqtt.isInitialized) {
            mqtt.desconectar()
        }

        super.onDestroy()
    }
}