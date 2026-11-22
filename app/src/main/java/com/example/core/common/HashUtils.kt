package com.example.core.common

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object HashUtils {

    fun calculateHash(file: File, algorithm: String = "SHA-256"): String {
        if (!file.exists() || !file.isFile) return ""
        return file.inputStream().use { calculateHash(it, algorithm) }
    }

    fun calculateHash(inputStream: InputStream, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(16 * 1024)
        var read = inputStream.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            read = inputStream.read(buffer)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun calculateHashBytes(bytes: ByteArray, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
            .coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.2f %s", value, units[digitGroups])
        }
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "--"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }
}
