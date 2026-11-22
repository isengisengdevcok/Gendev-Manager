package com.example.core.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class ParsedManifestInfo(
    val packageName: String,
    val versionCode: String,
    val versionName: String,
    val minSdk: String,
    val targetSdk: String,
    val appLabel: String,
    val debuggable: Boolean,
    val allowBackup: Boolean,
    val usesCleartextTraffic: Boolean,
    val permissions: List<String>,
    val activities: List<ManifestComponentInfo>,
    val services: List<ManifestComponentInfo>,
    val receivers: List<ManifestComponentInfo>,
    val providers: List<ManifestComponentInfo>,
    val decodedXml: String
)

data class ManifestComponentInfo(
    val name: String,
    val exported: Boolean?,
    val permission: String?
)

object AxmlParser {

    private const val CHUNK_AXML_FILE = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RESOURCE_MAP = 0x0180
    private const val CHUNK_START_NAMESPACE = 0x0100
    private const val CHUNK_END_NAMESPACE = 0x0101
    private const val CHUNK_START_TAG = 0x0102
    private const val CHUNK_END_TAG = 0x0103
    private const val CHUNK_TEXT = 0x0104

    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    fun isBinaryAxml(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val buf = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        val type = buf.short.toInt() and 0xFFFF
        return type == CHUNK_AXML_FILE
    }

    fun decode(bytes: ByteArray): ParsedManifestInfo {
        if (!isBinaryAxml(bytes)) {
            val text = bytes.toString(StandardCharsets.UTF_8)
            return parsePlainTextManifest(text)
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.short.toInt() and 0xFFFF
        val headerSize = buf.short.toInt() and 0xFFFF
        val fileSize = buf.int
        require(fileType == CHUNK_AXML_FILE && bytes.size >= 8) { "Not a valid binary AXML file" }

        var pos = headerSize.coerceAtLeast(8)
        var strings = emptyList<String>()
        val namespaces = mutableMapOf<String, String>()
        val xmlBuilder = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

        var packageName = ""
        var versionCode = ""
        var versionName = ""
        var minSdk = ""
        var targetSdk = ""
        var appLabel = ""
        var debuggable = false
        var allowBackup = true
        var usesCleartextTraffic = false
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<ManifestComponentInfo>()
        val services = mutableListOf<ManifestComponentInfo>()
        val receivers = mutableListOf<ManifestComponentInfo>()
        val providers = mutableListOf<ManifestComponentInfo>()

        var indent = 0
        val limit = minOf(fileSize, bytes.size)

        while (pos + 8 <= limit) {
            buf.position(pos)
            val chunkType = buf.short.toInt() and 0xFFFF
            val chunkHeaderSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize < 8 || pos + chunkSize > limit) break

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    strings = parseStringPool(bytes, pos, chunkHeaderSize, chunkSize)
                }
                CHUNK_RESOURCE_MAP -> {
                    // Resource ID table corresponds to string pool indices; skipped for XML text reconstruction
                }
                CHUNK_START_NAMESPACE -> {
                    if (pos + 24 <= limit) {
                        buf.position(pos + 16)
                        val prefixIdx = buf.int
                        val uriIdx = buf.int
                        val prefix = strings.getOrNull(prefixIdx).orEmpty()
                        val uri = strings.getOrNull(uriIdx).orEmpty()
                        if (uri.isNotEmpty()) namespaces[uri] = prefix
                    }
                }
                CHUNK_END_NAMESPACE -> Unit
                CHUNK_START_TAG -> {
                    if (pos + 36 <= limit) {
                        buf.position(pos + 16)
                        val nsIdx = buf.int
                        val nameIdx = buf.int
                        val attrStart = buf.short.toInt() and 0xFFFF
                        val attrSize = buf.short.toInt() and 0xFFFF
                        val attrCount = buf.short.toInt() and 0xFFFF

                        val tagName = strings.getOrNull(nameIdx) ?: "unknown"
                        val tagPrefix = if (nsIdx >= 0) namespaces[strings.getOrNull(nsIdx)] else null
                        val qualifiedTag = if (!tagPrefix.isNullOrEmpty()) "$tagPrefix:$tagName" else tagName

                        xmlBuilder.append("  ".repeat(indent)).append("<").append(qualifiedTag)
                        if (indent == 0 && namespaces.isNotEmpty()) {
                            namespaces.forEach { (uri, pfx) ->
                                val nsAttr = if (pfx.isNotEmpty()) "xmlns:$pfx" else "xmlns"
                                xmlBuilder.append(" ").append(nsAttr).append("=\"").append(escapeXml(uri)).append("\"")
                            }
                        }

                        val attrs = mutableMapOf<String, String>()
                        var attrPos = pos + 16 + attrStart
                        val effectiveAttrSize = if (attrSize >= 20) attrSize else 20
                        for (i in 0 until attrCount) {
                            if (attrPos + 20 > pos + chunkSize) break
                            buf.position(attrPos)
                            val aNsIdx = buf.int
                            val aNameIdx = buf.int
                            val aRawValueIdx = buf.int
                            val aSize = buf.short.toInt() and 0xFFFF
                            val aRes0 = buf.get()
                            val aDataType = buf.get().toInt() and 0xFF
                            val aData = buf.int

                            val aName = strings.getOrNull(aNameIdx) ?: "attr_$i"
                            val aNsUri = if (aNsIdx >= 0) strings.getOrNull(aNsIdx) else null
                            val aPrefix = if (aNsUri != null) namespaces[aNsUri] else null
                            val qAttrName = if (!aPrefix.isNullOrEmpty()) "$aPrefix:$aName" else aName

                            val valStr = formatTypedValue(aDataType, aData, aRawValueIdx, strings)
                            attrs[aName] = valStr
                            xmlBuilder.append("\n").append("  ".repeat(indent + 1))
                                .append(qAttrName).append("=\"").append(escapeXml(valStr)).append("\"")

                            attrPos += effectiveAttrSize
                        }
                        xmlBuilder.append(">\n")
                        indent++

                        when (tagName) {
                            "manifest" -> {
                                packageName = attrs["package"].orEmpty()
                                versionCode = attrs["versionCode"].orEmpty()
                                versionName = attrs["versionName"].orEmpty()
                            }
                            "uses-sdk" -> {
                                minSdk = attrs["minSdkVersion"].orEmpty()
                                targetSdk = attrs["targetSdkVersion"].orEmpty()
                            }
                            "uses-permission", "uses-permission-sdk-23" -> {
                                attrs["name"]?.let { if (it.isNotBlank()) permissions.add(it) }
                            }
                            "application" -> {
                                appLabel = attrs["label"].orEmpty()
                                debuggable = attrs["debuggable"] == "true"
                                allowBackup = attrs["allowBackup"] != "false"
                                usesCleartextTraffic = attrs["usesCleartextTraffic"] == "true"
                            }
                            "activity", "activity-alias" -> {
                                attrs["name"]?.let {
                                    activities.add(
                                        ManifestComponentInfo(
                                            name = it,
                                            exported = attrs["exported"]?.toBooleanStrictOrNull(),
                                            permission = attrs["permission"]
                                        )
                                    )
                                }
                            }
                            "service" -> {
                                attrs["name"]?.let {
                                    services.add(
                                        ManifestComponentInfo(
                                            name = it,
                                            exported = attrs["exported"]?.toBooleanStrictOrNull(),
                                            permission = attrs["permission"]
                                        )
                                    )
                                }
                            }
                            "receiver" -> {
                                attrs["name"]?.let {
                                    receivers.add(
                                        ManifestComponentInfo(
                                            name = it,
                                            exported = attrs["exported"]?.toBooleanStrictOrNull(),
                                            permission = attrs["permission"]
                                        )
                                    )
                                }
                            }
                            "provider" -> {
                                attrs["name"]?.let {
                                    providers.add(
                                        ManifestComponentInfo(
                                            name = it,
                                            exported = attrs["exported"]?.toBooleanStrictOrNull(),
                                            permission = attrs["permission"]
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                CHUNK_END_TAG -> {
                    if (pos + 24 <= limit) {
                        buf.position(pos + 16)
                        val nsIdx = buf.int
                        val nameIdx = buf.int
                        val tagName = strings.getOrNull(nameIdx) ?: "unknown"
                        val tagPrefix = if (nsIdx >= 0) namespaces[strings.getOrNull(nsIdx)] else null
                        val qualifiedTag = if (!tagPrefix.isNullOrEmpty()) "$tagPrefix:$tagName" else tagName
                        indent = (indent - 1).coerceAtLeast(0)
                        xmlBuilder.append("  ".repeat(indent)).append("</").append(qualifiedTag).append(">\n")
                    }
                }
                CHUNK_TEXT -> {
                    if (pos + 20 <= limit) {
                        buf.position(pos + 16)
                        val textIdx = buf.int
                        val text = strings.getOrNull(textIdx).orEmpty().trim()
                        if (text.isNotEmpty()) {
                            xmlBuilder.append("  ".repeat(indent)).append(escapeXml(text)).append("\n")
                        }
                    }
                }
            }
            pos += chunkSize
        }

        return ParsedManifestInfo(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            appLabel = appLabel,
            debuggable = debuggable,
            allowBackup = allowBackup,
            usesCleartextTraffic = usesCleartextTraffic,
            permissions = permissions.distinct(),
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            decodedXml = xmlBuilder.toString().trimEnd()
        )
    }

    internal fun parseStringPool(
        bytes: ByteArray,
        chunkStart: Int,
        chunkHeaderSize: Int,
        chunkSize: Int
    ): List<String> {
        if (chunkStart + 28 > bytes.size) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(chunkStart + 8)
        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsStart = buf.int
        val stylesStart = buf.int
        val isUtf8 = (flags and (1 shl 8)) != 0

        if (stringCount <= 0 || stringCount > 100_000) return emptyList()
        val offsetsStart = chunkStart + chunkHeaderSize.coerceAtLeast(28)
        val poolDataStart = chunkStart + stringsStart
        val chunkEnd = minOf(chunkStart + chunkSize, bytes.size)

        val result = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val offsetPos = offsetsStart + i * 4
            if (offsetPos + 4 > chunkEnd) break
            buf.position(offsetPos)
            val strOffset = poolDataStart + buf.int
            if (strOffset < 0 || strOffset >= chunkEnd) {
                result.add("")
                continue
            }
            val decoded = if (isUtf8) {
                readUtf8String(bytes, strOffset, chunkEnd)
            } else {
                readUtf16String(bytes, strOffset, chunkEnd)
            }
            result.add(decoded)
        }
        return result
    }

    private fun readUtf8String(bytes: ByteArray, start: Int, limit: Int): String {
        var idx = start
        // skip char count (1 or 2 bytes)
        if (idx >= limit) return ""
        val first = bytes[idx++].toInt() and 0xFF
        if ((first and 0x80) != 0 && idx < limit) idx++
        if (idx >= limit) return ""
        var byteLen = bytes[idx++].toInt() and 0xFF
        if ((byteLen and 0x80) != 0 && idx < limit) {
            val second = bytes[idx++].toInt() and 0xFF
            byteLen = ((byteLen and 0x7F) shl 8) or second
        }
        val safeLen = byteLen.coerceAtMost((limit - idx).coerceAtLeast(0))
        return String(bytes, idx, safeLen, StandardCharsets.UTF_8)
    }

    private fun readUtf16String(bytes: ByteArray, start: Int, limit: Int): String {
        if (start + 2 > limit) return ""
        var idx = start
        var charLen = (bytes[idx].toInt() and 0xFF) or ((bytes[idx + 1].toInt() and 0xFF) shl 8)
        idx += 2
        if ((charLen and 0x8000) != 0 && idx + 2 <= limit) {
            val low = (bytes[idx].toInt() and 0xFF) or ((bytes[idx + 1].toInt() and 0xFF) shl 8)
            idx += 2
            charLen = ((charLen and 0x7FFF) shl 16) or low
        }
        val byteLen = (charLen * 2).coerceAtMost((limit - idx).coerceAtLeast(0))
        return String(bytes, idx, byteLen, StandardCharsets.UTF_16LE)
    }

    private fun formatTypedValue(
        dataType: Int,
        data: Int,
        rawValueIdx: Int,
        strings: List<String>
    ): String {
        return when (dataType) {
            TYPE_STRING -> strings.getOrNull(data) ?: strings.getOrNull(rawValueIdx) ?: ""
            TYPE_REFERENCE -> "@0x%08x".format(data)
            TYPE_ATTRIBUTE -> "?0x%08x".format(data)
            TYPE_INT_DEC -> data.toString()
            TYPE_INT_HEX -> "0x%x".format(data)
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
            TYPE_DIMENSION -> "${data shr 8}px"
            TYPE_FRACTION -> "${data}%"
            in 0x1c..0x1f -> "#%08x".format(data)
            TYPE_NULL -> strings.getOrNull(rawValueIdx) ?: ""
            else -> strings.getOrNull(rawValueIdx) ?: data.toString()
        }
    }

    private fun parsePlainTextManifest(xml: String): ParsedManifestInfo {
        val pkg = Regex("""package="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty()
        val vCode = Regex("""versionCode="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty()
        val vName = Regex("""versionName="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty()
        val minSdk = Regex("""minSdkVersion="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty()
        val targetSdk = Regex("""targetSdkVersion="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty()
        val perms = Regex("""<uses-permission[^>]+android:name="([^"]+)"""").findAll(xml)
            .map { it.groupValues[1] }.distinct().toList()
        return ParsedManifestInfo(
            packageName = pkg,
            versionCode = vCode,
            versionName = vName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            appLabel = "",
            debuggable = xml.contains("""android:debuggable="true""""),
            allowBackup = !xml.contains("""android:allowBackup="false""""),
            usesCleartextTraffic = xml.contains("""android:usesCleartextTraffic="true""""),
            permissions = perms,
            activities = emptyList(),
            services = emptyList(),
            receivers = emptyList(),
            providers = emptyList(),
            decodedXml = xml
        )
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
