package com.example.ventilacioniot

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Lectura(
    val temperatura: Double,
    val ventilador: String,
    val fecha: String
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "ventilacion.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE usuarios (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "usuario TEXT UNIQUE NOT NULL, " +
                    "salt TEXT NOT NULL, " +
                    "hash TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE lecturas (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "usuario TEXT NOT NULL, " +
                    "temperatura REAL NOT NULL, " +
                    "ventilador TEXT NOT NULL, " +
                    "fecha TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        onCreate(db)
    }

    fun registrarUsuario(usuario: String, password: String): Boolean {
        val salt = SecurityUtils.generarSalt()
        val hash = SecurityUtils.hashPassword(password, salt)
        val valores = ContentValues().apply {
            put("usuario", usuario)
            put("salt", salt)
            put("hash", hash)
        }
        return writableDatabase.insert("usuarios", null, valores) != -1L
    }

    fun validarLogin(usuario: String, password: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT salt, hash FROM usuarios WHERE usuario = ?",
            arrayOf(usuario)
        )
        cursor.use {
            if (!it.moveToFirst()) return false
            val salt = it.getString(0)
            val hashGuardado = it.getString(1)
            val hashIngresado = SecurityUtils.hashPassword(password, salt)
            return SecurityUtils.compararHashes(hashGuardado, hashIngresado)
        }
    }

    fun guardarLectura(usuario: String, temperatura: Double, ventilador: String) {
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val valores = ContentValues().apply {
            put("usuario", usuario)
            put("temperatura", temperatura)
            put("ventilador", ventilador)
            put("fecha", fecha)
        }
        writableDatabase.insertOrThrow("lecturas", null, valores)
    }

    fun obtenerLecturas(usuario: String): List<Lectura> {
        val lista = mutableListOf<Lectura>()
        val cursor = readableDatabase.rawQuery(
            "SELECT temperatura, ventilador, fecha FROM lecturas WHERE usuario = ? ORDER BY id DESC LIMIT 100",
            arrayOf(usuario)
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(Lectura(it.getDouble(0), it.getString(1), it.getString(2)))
            }
        }
        return lista
    }
}