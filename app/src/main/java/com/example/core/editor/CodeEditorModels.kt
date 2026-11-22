package com.example.core.editor

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

enum class EditorLanguage(val label: String, val extensions: Set<String>) {
    XML("XML", setOf("xml", "axml")),
    JSON("JSON", setOf("json")),
    HTML("HTML", setOf("html", "htm")),
    CSS("CSS", setOf("css")),
    JAVASCRIPT("JavaScript", setOf("js", "mjs")),
    SMALI("Smali", setOf("smali")),
    PROPERTIES("Properties", setOf("properties")),
    INI("INI", setOf("ini", "cfg", "conf")),
    SHELL("Shell", setOf("sh", "bash")),
    MARKDOWN("Markdown", setOf("md")),
    HEX("Hex", setOf("dex", "arsc", "so", "bin", "dat", "rsa", "sf", "class")),
    PLAIN("Text", setOf("txt", "log", "gradle", "kts", "kt", "java", "yml", "yaml"));

    companion object {
        fun detect(fileName: String, bytes: ByteArray? = null): EditorLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            entries.firstOrNull { ext in it.extensions }?.let { return it }
            if (bytes != null && isLikelyBinary(bytes)) return HEX
            return PLAIN
        }

        fun isLikelyBinary(bytes: ByteArray): Boolean {
            val sampleLen = minOf(bytes.size, 512)
            var controlCount = 0
            for (i in 0 until sampleLen) {
                val b = bytes[i].toInt() and 0xFF
                if (b == 0) return true
                if (b < 0x09 || (b in 0x0E..0x1F)) controlCount++
            }
            return sampleLen > 0 && (controlCount.toFloat() / sampleLen.toFloat()) > 0.15f
        }
    }
}

object EncodingDetector {
    fun detectEncoding(bytes: ByteArray): Pair<String, Charset> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return "UTF-8 BOM" to StandardCharsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return "UTF-16LE" to StandardCharsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return "UTF-16BE" to StandardCharsets.UTF_16BE
        }
        val asciiOnly = bytes.take(4096).all { (it.toInt() and 0x80) == 0 }
        return if (asciiOnly) "US-ASCII / UTF-8" to StandardCharsets.UTF_8
        else "UTF-8" to StandardCharsets.UTF_8
    }
}

data class HexRow(
    val offset: Int,
    val bytes: List<Byte>
)

object HexDumpFormatter {
    fun toRows(bytes: ByteArray, bytesPerRow: Int = 16, maxBytes: Int = 8192): List<HexRow> {
        val capped = minOf(bytes.size, maxBytes)
        val rows = ArrayList<HexRow>((capped / bytesPerRow) + 1)
        var offset = 0
        while (offset < capped) {
            val end = minOf(offset + bytesPerRow, capped)
            rows.add(HexRow(offset, bytes.slice(offset until end)))
            offset += bytesPerRow
        }
        return rows
    }

    fun formatByteHex(b: Byte): String = "%02X".format(b.toInt() and 0xFF)

    fun formatAsciiRow(bytes: List<Byte>): String =
        bytes.joinToString("") { b ->
            val ch = b.toInt() and 0xFF
            if (ch in 32..126) ch.toChar().toString() else "."
        }
}
