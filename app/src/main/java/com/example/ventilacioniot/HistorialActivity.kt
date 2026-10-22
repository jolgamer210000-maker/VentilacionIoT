package com.example.ventilacioniot

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors

class HistorialActivity : AppCompatActivity() {

    private val database by lazy {
        DatabaseHelper(applicationContext)
    }

    private val tareasBD = Executors.newSingleThreadExecutor()
    private var cerrando = false

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

        setContentView(R.layout.activity_historial)

        val resumen = findViewById<TextView>(R.id.tvResumenHistorial)
        val lista = findViewById<ListView>(R.id.listaHistorial)

        findViewById<Button>(R.id.btnVolverHistorial)
            .setOnClickListener {
                finish()
            }

        // Consulta SQLite sin bloquear la pantalla.
        tareasBD.execute {
            try {
                val lecturas = database.obtenerLecturas(usuario)

                val filas = lecturas.map { lectura ->
                    val temperatura = String.format(
                        Locale.getDefault(),
                        "%.1f",
                        lectura.temperatura
                    )

                    val ventilador = when (lectura.ventilador) {
                        "ON" -> "Encendido"
                        "OFF" -> "Apagado"
                        else -> lectura.ventilador
                    }

                    "${lectura.fecha}\n" +
                            "$temperatura °C · Ventilador: $ventilador"
                }

                runOnUiThread {
                    if (pantallaActiva()) {
                        resumen.text = if (lecturas.isEmpty()) {
                            "No hay lecturas guardadas para $usuario"
                        } else {
                            "$usuario · Últimas ${lecturas.size} lecturas"
                        }

                        lista.adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            filas
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (pantallaActiva()) {
                        resumen.text = "No se pudo cargar el historial"
                    }
                }
            }
        }
    }

    private fun pantallaActiva(): Boolean {
        return !cerrando && !isFinishing && !isDestroyed
    }

    override fun onDestroy() {
        cerrando = true

        tareasBD.execute {
            database.close()
        }
        tareasBD.shutdown()

        super.onDestroy()
    }
}