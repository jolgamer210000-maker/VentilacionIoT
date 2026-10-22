package com.example.ventilacioniot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sesion = getSharedPreferences("sesion", MODE_PRIVATE)
        val usuario = sesion.getString("usuario", null)

        // Si no hay una sesión guardada, vuelve al login.
        if (usuario.isNullOrBlank()) {
            volverAlLogin()
            return
        }

        setContentView(R.layout.activity_main)

        val bienvenida = findViewById<TextView>(R.id.tvBienvenida)
        bienvenida.text = "Bienvenido, $usuario"

        findViewById<Button>(R.id.btnControlador).setOnClickListener {
            startActivity(Intent(this, ControladorActivity::class.java))
        }

        findViewById<Button>(R.id.btnDispositivo).setOnClickListener {
            startActivity(Intent(this, DispositivoActivity::class.java))
        }

        findViewById<Button>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }


        findViewById<Button>(R.id.btnCerrar).setOnClickListener {
            sesion.edit().remove("usuario").apply()
            volverAlLogin()
        }
    }

    private fun mostrarAviso(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    private fun volverAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}