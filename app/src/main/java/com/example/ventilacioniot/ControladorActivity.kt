package com.example.ventilacioniot

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class ControladorActivity : AppCompatActivity() {

    private lateinit var mqtt: MqttManager
    private lateinit var tvConexion: TextView
    private lateinit var tvEstado: TextView
    private lateinit var btnConectar: Button

    private val database by lazy {
        DatabaseHelper(applicationContext)
    }

    private val tareasBD = Executors.newSingleThreadExecutor()

    private var conectado = false
    private var cerrando = false
    private var usuarioActual = ""
    private var ultimoGuardado: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recupera el usuario que inició sesión.
        val usuario = getSharedPreferences("sesion", MODE_PRIVATE)
            .getString("usuario", null)

        if (usuario.isNullOrBlank()) {
            volverAlLogin()
            return
        }

        usuarioActual = usuario

        setContentView(R.layout.activity_controlador)

        tvConexion = findViewById(R.id.tvConexion)
        tvEstado = findViewById(R.id.tvEstado)
        btnConectar = findViewById(R.id.btnConectar)

        val etObjetivo = findViewById<EditText>(R.id.etObjetivo)
        val spVelocidad = findViewById<Spinner>(R.id.spVelocidad)

        spVelocidad.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                "1 — Baja",
                "2 — Media",
                "3 — Alta"
            )
        )

        // Recibe mensajes y cambios de conexión.
        mqtt = MqttManager(
            onMensaje = { topic, mensaje ->
                runOnUiThread {
                    if (pantallaActiva() &&
                        topic == "ventilacion/estado"
                    ) {
                        mostrarEstado(mensaje)
                    }
                }
            },
            onEstado = { estado ->
                runOnUiThread {
                    if (pantallaActiva()) {
                        conectado = estado == "Conectado"
                        tvConexion.text = estado
                        btnConectar.isEnabled = !conectado
                    }
                }
            }
        )

        btnConectar.setOnClickListener {
            conectar()
        }

        findViewById<Button>(R.id.btnAuto).setOnClickListener {
            enviar("MODE:AUTO")
        }

        findViewById<Button>(R.id.btnManual).setOnClickListener {
            enviar("MODE:MANUAL")
        }

        findViewById<Button>(R.id.btnEncender).setOnClickListener {
            enviar("FAN:ON")
        }

        findViewById<Button>(R.id.btnApagar).setOnClickListener {
            enviar("FAN:OFF")
        }

        findViewById<Button>(R.id.btnObjetivo).setOnClickListener {
            val valor = etObjetivo.text.toString()
                .trim()
                .replace(',', '.')
                .toDoubleOrNull()

            if (valor == null ||
                !valor.isFinite() ||
                valor !in 10.0..40.0
            ) {
                etObjetivo.error = "Ingresa un valor entre 10 y 40 °C"
            } else {
                etObjetivo.error = null
                enviar("TARGET:$valor")
            }
        }

        findViewById<Button>(R.id.btnVelocidad).setOnClickListener {
            val velocidad = spVelocidad.selectedItemPosition + 1
            enviar("SPEED:$velocidad")
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }
    }

    private fun pantallaActiva(): Boolean {
        return !cerrando && !isFinishing && !isDestroyed
    }

    private fun volverAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    private fun conectar() {
        if (BuildConfig.MQTT_HOST.isBlank() ||
            BuildConfig.MQTT_USER.isBlank() ||
            BuildConfig.MQTT_PASS.isBlank()
        ) {
            aviso("Primero configura los datos del broker")
            return
        }

        btnConectar.isEnabled = false
        tvConexion.text = "Conectando..."

        try {
            mqtt.conectar(
                "controlador",
                listOf("ventilacion/estado")
            )
        } catch (e: Exception) {
            conectado = false
            btnConectar.isEnabled = true
            tvConexion.text = "No se pudo conectar: ${e.message}"
        }
    }

    private fun enviar(comando: String) {
        if (!conectado) {
            aviso("Primero conecta con el broker")
            return
        }

        try {
            mqtt.publicar(
                "ventilacion/comando",
                comando
            )
        } catch (e: Exception) {
            aviso("No se pudo enviar: ${e.message}")
        }
    }

    private fun mostrarEstado(mensaje: String) {
        // Convierte "temp=26.5;fan=ON;..." en un mapa.
        val datos = mensaje.split(";").mapNotNull { parte ->
            val par = parte.split("=", limit = 2)

            if (par.size == 2) {
                par[0].trim() to par[1].trim()
            } else {
                null
            }
        }.toMap()

        val temperatura = datos["temp"] ?: "--"

        val ventilador = when (datos["fan"]) {
            "ON" -> "Encendido"
            "OFF" -> "Apagado"
            else -> "--"
        }

        val velocidad = datos["speed"] ?: "--"

        val modo = when (datos["mode"]) {
            "AUTO" -> "Automático"
            "MANUAL" -> "Manual"
            else -> "--"
        }

        val objetivo = datos["target"] ?: "--"

        tvEstado.text = """
            Temperatura: $temperatura °C
            Ventilador: $ventilador
            Velocidad: $velocidad
            Modo: $modo
            Objetivo: $objetivo °C
        """.trimIndent()

        guardarEnHistorial(datos)
    }

    private fun guardarEnHistorial(datos: Map<String, String>) {
        if (cerrando || tareasBD.isShutdown) return

        val temperatura = datos["temp"]?.toDoubleOrNull() ?: return
        val ventilador = datos["fan"] ?: return

        if (!temperatura.isFinite()) return
        if (ventilador != "ON" && ventilador != "OFF") return

        // Guarda la primera lectura y luego una cada 10 segundos.
        val ahora = SystemClock.elapsedRealtime()
        val anterior = ultimoGuardado

        if (anterior != null && ahora - anterior < 10_000L) {
            return
        }

        ultimoGuardado = ahora
        val usuarioLectura = usuarioActual

        // Trabaja con SQLite fuera del hilo de la pantalla.
        tareasBD.execute {
            try {
                database.guardarLectura(
                    usuarioLectura,
                    temperatura,
                    ventilador
                )
            } catch (e: Exception) {
                runOnUiThread {
                    if (pantallaActiva()) {
                        aviso("No se pudo guardar la lectura")
                    }
                }
            }
        }
    }

    private fun aviso(mensaje: String) {
        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        cerrando = true
        conectado = false

        try {
            if (::mqtt.isInitialized) {
                mqtt.desconectar()
            }
        } finally {
            // Cierra la base después de los guardados pendientes.
            tareasBD.execute {
                database.close()
            }
            tareasBD.shutdown()

            super.onDestroy()
        }
    }
}