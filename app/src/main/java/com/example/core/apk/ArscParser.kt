package com.example.core.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class ArscTypeSummary(
    val typeId: Int,
    val typeName: String,
    val entryCount: Int
)

data class ArscTableInfo(
    val packageCount: Int,
    val packageName: String,
    val packageId: Int,
    val globalStringCount: Int,
    val resourceTypes: List<ArscTypeSummary>,
    val sampleKeys: List<String>
)

object ArscParser {
    private const val CHUNK_TABLE = 0x0002
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_PACKAGE = 0x0200
    private const val CHUNK_TYPE_SPEC = 0x0202

    fun parse(bytes: ByteArray): ArscTableInfo? {
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = buf.short.toInt() and 0xFFFF
        val headerSize = buf.short.toInt() and 0xFFFF
        val fileSize = buf.int
        val packageCount = buf.int
        if (type != CHUNK_TABLE) return null

        var pos = headerSize.coerceAtLeast(12)
        val limit = minOf(fileSize, bytes.size)
        var globalStringCount = 0
        var pkgName = ""
        var pkgId = 0x7F
        val typeSummaries = mutableListOf<ArscTypeSummary>()
        var sampleKeys = emptyList<String>()

        while (pos + 8 <= limit) {
            buf.position(pos)
            val cType = buf.short.toInt() and 0xFFFF
            val cHeaderSize = buf.short.toInt() and 0xFFFF
            val cSize = buf.int
            if (cSize < 8 || pos + cSize > limit) break

            when (cType) {
                CHUNK_STRING_POOL -> {
                    val pool = AxmlParser.parseStringPool(bytes, pos, cHeaderSize, minOf(cSize, 512 * 1024))
                    globalStringCount = pool.size
                }
                CHUNK_PACKAGE -> {
                    if (pos + 288 <= limit) {
                        buf.position(pos + 8)
                        pkgId = buf.int
                        val nameBytes = ByteArray(256)
                        buf.get(nameBytes)
                        pkgName = String(nameBytes, StandardCharsets.UTF_16LE).substringBefore('\u0000')
                        val typeStringsRel = buf.int
                        buf.int // lastPublicType
                        val keyStringsRel = buf.int

                        var typeNames = emptyList<String>()
                        if (typeStringsRel > 0 && pos + typeStringsRel + 8 <= limit) {
                            buf.position(pos + typeStringsRel + 4)
                            val tsSize = buf.int
                            typeNames = AxmlParser.parseStringPool(bytes, pos + typeStringsRel, 28, tsSize)
                        }
                        if (keyStringsRel > 0 && pos + keyStringsRel + 8 <= limit) {
                            buf.position(pos + keyStringsRel + 4)
                            val ksSize = buf.int
                            sampleKeys = AxmlParser.parseStringPool(bytes, pos + keyStringsRel, 28, minOf(ksSize, 256 * 1024))
                                .take(80)
                        }

                        var subPos = pos + cHeaderSize
                        val pkgEnd = pos + cSize
                        while (subPos + 8 <= pkgEnd) {
                            buf.position(subPos)
                            val scType = buf.short.toInt() and 0xFFFF
                            val scHeader = buf.short.toInt() and 0xFFFF
                            val scSize = buf.int
                            if (scSize < 8 || subPos + scSize > pkgEnd) break
                            if (scType == CHUNK_TYPE_SPEC && subPos + 16 <= pkgEnd) {
                                buf.position(subPos + 8)
                                val tId = buf.get().toInt() and 0xFF
                                buf.get(); buf.short // res0, res1
                                val entryCount = buf.int
                                val tName = typeNames.getOrNull(tId - 1) ?: "type_$tId"
                                typeSummaries.add(ArscTypeSummary(tId, tName, entryCount))
                            }
                            subPos += scSize
                        }
                    }
                }
            }
            pos += cSize
        }

        return ArscTableInfo(
            packageCount = packageCount,
            packageName = pkgName,
            packageId = pkgId,
            globalStringCount = globalStringCount,
            resourceTypes = typeSummaries,
            sampleKeys = sampleKeys
        )
    }
}
