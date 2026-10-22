package com.example.ventilacioniot

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {

    private const val ITERACIONES = 120000
    private const val LARGO_CLAVE = 256

    fun generarSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        return Base64.encodeToString(
            salt,
            Base64.NO_WRAP
        )
    }

    fun hashPassword(password: String, salt: String): String {
        val caracteres = password.toCharArray()

        val spec = PBEKeySpec(
            caracteres,
            Base64.decode(salt, Base64.NO_WRAP),
            ITERACIONES,
            LARGO_CLAVE
        )

        return try {
            val factory = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
            )

            val hash = factory.generateSecret(spec).encoded

            Base64.encodeToString(hash, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
            caracteres.fill('\u0000')
        }
    }

    fun compararHashes(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
        )
    }
}