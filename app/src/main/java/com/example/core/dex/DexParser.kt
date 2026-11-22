package com.example.core.dex

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

data class DexHeaderInfo(
    val magic: String,
    val fileSize: Int,
    val stringCount: Int,
    val typeCount: Int,
    val protoCount: Int,
    val fieldCount: Int,
    val methodCount: Int,
    val classCount: Int
)

data class DexFieldInfo(
    val name: String,
    val typeDescriptor: String,
    val accessFlags: Int,
    val isStatic: Boolean
) {
    val accessString: String get() = formatAccessFlags(accessFlags, isField = true)
    val readableType: String get() = descriptorToReadable(typeDescriptor)
}

data class DexMethodInfo(
    val classDescriptor: String,
    val name: String,
    val returnType: String,
    val paramTypes: List<String>,
    val accessFlags: Int,
    val isDirect: Boolean,
    val registersSize: Int,
    val insSize: Int,
    val outsSize: Int,
    val codeInsns: IntArray
) {
    val accessString: String get() = formatAccessFlags(accessFlags, isField = false)
    val signature: String
        get() = "$name(${paramTypes.joinToString("")})$returnType"
    val readableSignature: String
        get() = "${descriptorToReadable(returnType)} $name(${paramTypes.joinToString(", ") { descriptorToReadable(it) }})"
}

data class DexClassInfo(
    val descriptor: String,
    val className: String,
    val packageName: String,
    val superClassDescriptor: String?,
    val accessFlags: Int,
    val sourceFile: String?,
    val staticFields: List<DexFieldInfo>,
    val instanceFields: List<DexFieldInfo>,
    val directMethods: List<DexMethodInfo>,
    val virtualMethods: List<DexMethodInfo>
) {
    val allMethods: List<DexMethodInfo> get() = directMethods + virtualMethods
    val allFields: List<DexFieldInfo> get() = staticFields + instanceFields
}

data class DexParsedFile(
    val dexName: String,
    val header: DexHeaderInfo,
    val strings: List<String>,
    val types: List<String>,
    val methodsTable: List<String>,
    val fieldsTable: List<String>,
    val classes: List<DexClassInfo>
)

data class DexReferenceHit(
    val className: String,
    val methodName: String,
    val detail: String
)

class DexParser {

    fun parseFromApk(apkFile: File, dexEntryName: String = "classes.dex"): Result<DexParsedFile> =
        runCatching {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(dexEntryName)
                    ?: error("DEX entry '$dexEntryName' not found in ${apkFile.name}")
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                parseBytes(bytes, dexEntryName)
            }
        }

    fun parseBytes(bytes: ByteArray, dexName: String = "classes.dex"): DexParsedFile {
        require(bytes.size >= 112) { "File too small to be a valid DEX" }
        val magic = String(bytes, 0, 8, Charsets.US_ASCII)
        require(magic.startsWith("dex\n")) { "Invalid DEX magic header" }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(32)
        val fileSize = buf.int
        buf.int // headerSize (0x70)
        buf.int // endianTag
        buf.int; buf.int // link
        buf.int // mapOff
        val stringIdsSize = buf.int
        val stringIdsOff = buf.int
        val typeIdsSize = buf.int
        val typeIdsOff = buf.int
        val protoIdsSize = buf.int
        val protoIdsOff = buf.int
        val fieldIdsSize = buf.int
        val fieldIdsOff = buf.int
        val methodIdsSize = buf.int
        val methodIdsOff = buf.int
        val classDefsSize = buf.int
        val classDefsOff = buf.int

        val header = DexHeaderInfo(
            magic = magic.replace("\n", "\\n").replace("\u0000", ""),
            fileSize = fileSize,
            stringCount = stringIdsSize,
            typeCount = typeIdsSize,
            protoCount = protoIdsSize,
            fieldCount = fieldIdsSize,
            methodCount = methodIdsSize,
            classCount = classDefsSize
        )

        val strings = ArrayList<String>(stringIdsSize.coerceAtMost(200_000))
        for (i in 0 until stringIdsSize) {
            val ptrPos = stringIdsOff + i * 4
            if (ptrPos + 4 > bytes.size) break
            buf.position(ptrPos)
            val dataOff = buf.int
            strings.add(readMutf8String(bytes, dataOff))
        }

        val types = ArrayList<String>(typeIdsSize.coerceAtMost(100_000))
        for (i in 0 until typeIdsSize) {
            val pos = typeIdsOff + i * 4
            if (pos + 4 > bytes.size) break
            buf.position(pos)
            val descIdx = buf.int
            types.add(strings.getOrNull(descIdx) ?: "Lunknown;")
        }

        // Proto table: shortyIdx, returnTypeIdx, parametersOff
        val protos = ArrayList<Pair<String, List<String>>>(protoIdsSize.coerceAtMost(100_000))
        for (i in 0 until protoIdsSize) {
            val pos = protoIdsOff + i * 12
            if (pos + 12 > bytes.size) break
            buf.position(pos)
            buf.int // shortyIdx
            val retIdx = buf.int
            val paramsOff = buf.int
            val retType = types.getOrNull(retIdx) ?: "V"
            val params = if (paramsOff > 0 && paramsOff + 4 <= bytes.size) {
                buf.position(paramsOff)
                val pCount = buf.int.coerceIn(0, 64)
                (0 until pCount).mapNotNull {
                    if (buf.position() + 2 <= bytes.size) {
                        val tIdx = buf.short.toInt() and 0xFFFF
                        types.getOrNull(tIdx)
                    } else null
                }
            } else emptyList()
            protos.add(retType to params)
        }

        val fieldsRaw = ArrayList<Triple<String, String, String>>(fieldIdsSize.coerceAtMost(150_000))
        val fieldsTable = ArrayList<String>(fieldIdsSize.coerceAtMost(150_000))
        for (i in 0 until fieldIdsSize) {
            val pos = fieldIdsOff + i * 8
            if (pos + 8 > bytes.size) break
            buf.position(pos)
            val classIdx = buf.short.toInt() and 0xFFFF
            val typeIdx = buf.short.toInt() and 0xFFFF
            val nameIdx = buf.int
            val owner = types.getOrNull(classIdx) ?: "Lunknown;"
            val fType = types.getOrNull(typeIdx) ?: "Ljava/lang/Object;"
            val fName = strings.getOrNull(nameIdx) ?: "f$i"
            fieldsRaw.add(Triple(owner, fName, fType))
            fieldsTable.add("$owner->$fName:$fType")
        }

        data class RawMethodRef(
            val owner: String,
            val name: String,
            val retType: String,
            val params: List<String>
        )

        val methodsRaw = ArrayList<RawMethodRef>(methodIdsSize.coerceAtMost(200_000))
        val methodsTable = ArrayList<String>(methodIdsSize.coerceAtMost(200_000))
        for (i in 0 until methodIdsSize) {
            val pos = methodIdsOff + i * 8
            if (pos + 8 > bytes.size) break
            buf.position(pos)
            val classIdx = buf.short.toInt() and 0xFFFF
            val protoIdx = buf.short.toInt() and 0xFFFF
            val nameIdx = buf.int
            val owner = types.getOrNull(classIdx) ?: "Lunknown;"
            val (ret, params) = protos.getOrNull(protoIdx) ?: ("V" to emptyList())
            val mName = strings.getOrNull(nameIdx) ?: "m$i"
            methodsRaw.add(RawMethodRef(owner, mName, ret, params))
            methodsTable.add("$owner->$mName(${params.joinToString("")})$ret")
        }

        val classes = ArrayList<DexClassInfo>(classDefsSize.coerceAtMost(60_000))
        for (i in 0 until classDefsSize) {
            val pos = classDefsOff + i * 32
            if (pos + 32 > bytes.size) break
            buf.position(pos)
            val classIdx = buf.int
            val accessFlags = buf.int
            val superIdx = buf.int
            buf.int // interfacesOff
            val sourceFileIdx = buf.int
            buf.int // annotationsOff
            val classDataOff = buf.int
            buf.int // staticValuesOff

            val descriptor = types.getOrNull(classIdx) ?: "Lunknown;"
            val readableFull = descriptorToReadable(descriptor)
            val pkgName = readableFull.substringBeforeLast('.', "(default)")
            val simpleOrFull = readableFull
            val superDesc = if (superIdx >= 0) types.getOrNull(superIdx) else null
            val sourceFile = if (sourceFileIdx >= 0) strings.getOrNull(sourceFileIdx) else null

            val staticFields = mutableListOf<DexFieldInfo>()
            val instanceFields = mutableListOf<DexFieldInfo>()
            val directMethods = mutableListOf<DexMethodInfo>()
            val virtualMethods = mutableListOf<DexMethodInfo>()

            if (classDataOff > 0 && classDataOff < bytes.size) {
                val reader = UlebReader(bytes, classDataOff)
                val staticFieldsCount = reader.readUleb128()
                val instanceFieldsCount = reader.readUleb128()
                val directMethodsCount = reader.readUleb128()
                val virtualMethodsCount = reader.readUleb128()

                var fIdx = 0
                repeat(staticFieldsCount) {
                    fIdx += reader.readUleb128()
                    val flags = reader.readUleb128()
                    val raw = fieldsRaw.getOrNull(fIdx)
                    if (raw != null) {
                        staticFields.add(DexFieldInfo(raw.second, raw.third, flags, isStatic = true))
                    }
                }
                fIdx = 0
                repeat(instanceFieldsCount) {
                    fIdx += reader.readUleb128()
                    val flags = reader.readUleb128()
                    val raw = fieldsRaw.getOrNull(fIdx)
                    if (raw != null) {
                        instanceFields.add(DexFieldInfo(raw.second, raw.third, flags, isStatic = false))
                    }
                }

                fun readMethods(count: Int, isDirect: Boolean, target: MutableList<DexMethodInfo>) {
                    var mIdx = 0
                    repeat(count) {
                        mIdx += reader.readUleb128()
                        val flags = reader.readUleb128()
                        val codeOff = reader.readUleb128()
                        val raw = methodsRaw.getOrNull(mIdx)
                        var regs = 0; var ins = 0; var outs = 0
                        var insns = IntArray(0)
                        if (codeOff > 0 && codeOff + 16 <= bytes.size) {
                            val cb = ByteBuffer.wrap(bytes, codeOff, 16).order(ByteOrder.LITTLE_ENDIAN)
                            regs = cb.short.toInt() and 0xFFFF
                            ins = cb.short.toInt() and 0xFFFF
                            outs = cb.short.toInt() and 0xFFFF
                            cb.short // triesSize
                            cb.int // debugInfoOff
                            val insnsSize = cb.int.coerceIn(0, 8192)
                            val insnsByteOff = codeOff + 16
                            if (insnsByteOff + insnsSize * 2 <= bytes.size) {
                                val ib = ByteBuffer.wrap(bytes, insnsByteOff, insnsSize * 2).order(ByteOrder.LITTLE_ENDIAN)
                                insns = IntArray(insnsSize) { ib.short.toInt() and 0xFFFF }
                            }
                        }
                        if (raw != null) {
                            target.add(
                                DexMethodInfo(
                                    classDescriptor = descriptor,
                                    name = raw.name,
                                    returnType = raw.retType,
                                    paramTypes = raw.params,
                                    accessFlags = flags,
                                    isDirect = isDirect,
                                    registersSize = regs,
                                    insSize = ins,
                                    outsSize = outs,
                                    codeInsns = insns
                                )
                            )
                        }
                    }
                }

                readMethods(directMethodsCount, true, directMethods)
                readMethods(virtualMethodsCount, false, virtualMethods)
            }

            classes.add(
                DexClassInfo(
                    descriptor = descriptor,
                    className = simpleOrFull,
                    packageName = pkgName,
                    superClassDescriptor = superDesc,
                    accessFlags = accessFlags,
                    sourceFile = sourceFile,
                    staticFields = staticFields,
                    instanceFields = instanceFields,
                    directMethods = directMethods,
                    virtualMethods = virtualMethods
                )
            )
        }

        return DexParsedFile(
            dexName = dexName,
            header = header,
            strings = strings,
            types = types,
            methodsTable = methodsTable,
            fieldsTable = fieldsTable,
            classes = classes.sortedBy { it.className }
        )
    }

    fun disassembleClassToSmali(dex: DexParsedFile, cls: DexClassInfo): String {
        val sb = StringBuilder()
        sb.append(".class ").append(formatAccessFlags(cls.accessFlags, isField = false)).append(cls.descriptor).append('\n')
        cls.superClassDescriptor?.let { sb.append(".super ").append(it).append('\n') }
        cls.sourceFile?.let { sb.append(".source \"").append(it).append("\"\n") }
        sb.append('\n')

        if (cls.staticFields.isNotEmpty()) {
            sb.append("# static fields\n")
            for (f in cls.staticFields) {
                sb.append(".field ").append(f.accessString).append(f.name).append(':').append(f.typeDescriptor).append('\n')
            }
            sb.append('\n')
        }
        if (cls.instanceFields.isNotEmpty()) {
            sb.append("# instance fields\n")
            for (f in cls.instanceFields) {
                sb.append(".field ").append(f.accessString).append(f.name).append(':').append(f.typeDescriptor).append('\n')
            }
            sb.append('\n')
        }

        if (cls.directMethods.isNotEmpty()) {
            sb.append("# direct methods\n")
            for (m in cls.directMethods) {
                appendMethodSmali(sb, dex, m)
            }
        }
        if (cls.virtualMethods.isNotEmpty()) {
            sb.append("# virtual methods\n")
            for (m in cls.virtualMethods) {
                appendMethodSmali(sb, dex, m)
            }
        }
        return sb.toString().trimEnd()
    }

    fun findReferences(dex: DexParsedFile, query: String, maxHits: Int = 80): List<DexReferenceHit> {
        if (query.isBlank()) return emptyList()
        val hits = mutableListOf<DexReferenceHit>()
        for (cls in dex.classes) {
            if (cls.className.contains(query, ignoreCase = true)) {
                hits.add(DexReferenceHit(cls.className, "<class>", cls.descriptor))
            }
            for (f in cls.allFields) {
                if (f.name.contains(query, ignoreCase = true) || f.typeDescriptor.contains(query, ignoreCase = true)) {
                    hits.add(DexReferenceHit(cls.className, f.name, "field ${f.typeDescriptor}"))
                }
            }
            for (m in cls.allMethods) {
                if (m.name.contains(query, ignoreCase = true)) {
                    hits.add(DexReferenceHit(cls.className, m.name, m.readableSignature))
                } else if (m.codeInsns.isNotEmpty()) {
                    val smaliBody = disassembleMethodInsns(dex, m.codeInsns)
                    val matchingLine = smaliBody.firstOrNull { it.contains(query, ignoreCase = true) }
                    if (matchingLine != null) {
                        hits.add(DexReferenceHit(cls.className, m.name, matchingLine.trim()))
                    }
                }
                if (hits.size >= maxHits) return hits
            }
        }
        return hits
    }

    private fun appendMethodSmali(sb: StringBuilder, dex: DexParsedFile, m: DexMethodInfo) {
        sb.append(".method ").append(m.accessString).append(m.signature).append('\n')
        if (m.codeInsns.isEmpty()) {
            sb.append("    # abstract or native method (no code_item)\n")
        } else {
            sb.append("    .registers ").append(m.registersSize).append('\n')
            val lines = disassembleMethodInsns(dex, m.codeInsns)
            for (line in lines) {
                sb.append("    ").append(line).append('\n')
            }
        }
        sb.append(".end method\n\n")
    }

    private fun disassembleMethodInsns(dex: DexParsedFile, insns: IntArray): List<String> {
        val out = mutableListOf<String>()
        var pc = 0
        while (pc < insns.size) {
            val unit = insns[pc]
            val opcode = unit and 0xFF
            val hi = (unit ushr 8) and 0xFF
            when (opcode) {
                0x00 -> {
                    if (unit == 0x0000) out.add("nop")
                    else out.add(".packed-data 0x%04x".format(unit))
                    pc += 1
                }
                0x01 -> { out.add("move v${hi and 0xF}, v${(hi ushr 4) and 0xF}"); pc += 1 }
                0x0a -> { out.add("move-result v$hi"); pc += 1 }
                0x0c -> { out.add("move-result-object v$hi"); pc += 1 }
                0x0d -> { out.add("move-exception v$hi"); pc += 1 }
                0x0e -> { out.add("return-void"); pc += 1 }
                0x0f -> { out.add("return v$hi"); pc += 1 }
                0x11 -> { out.add("return-object v$hi"); pc += 1 }
                0x12 -> {
                    val reg = hi and 0xF
                    val lit = ((hi ushr 4) shl 28) shr 28
                    out.add("const/4 v$reg, $lit")
                    pc += 1
                }
                0x13 -> {
                    val lit = if (pc + 1 < insns.size) insns[pc + 1].toShort().toInt() else 0
                    out.add("const/16 v$hi, $lit")
                    pc += 2
                }
                0x14 -> {
                    val v = if (pc + 2 < insns.size) (insns[pc + 1] or (insns[pc + 2] shl 16)) else 0
                    out.add("const v$hi, 0x%x".format(v))
                    pc += 3
                }
                0x1a -> {
                    val strIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    val s = dex.strings.getOrNull(strIdx).orEmpty().replace("\"", "\\\"").replace("\n", "\\n")
                    out.add("const-string v$hi, \"$s\"")
                    pc += 2
                }
                0x1c -> {
                    val tIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    out.add("const-class v$hi, ${dex.types.getOrNull(tIdx) ?: "Lunknown;"}")
                    pc += 2
                }
                0x1f -> {
                    val tIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    out.add("check-cast v$hi, ${dex.types.getOrNull(tIdx) ?: "Lunknown;"}")
                    pc += 2
                }
                0x20 -> {
                    val tIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    out.add("instance-of v${hi and 0xF}, v${(hi ushr 4) and 0xF}, ${dex.types.getOrNull(tIdx) ?: "Lunknown;"}")
                    pc += 2
                }
                0x22 -> {
                    val tIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    out.add("new-instance v$hi, ${dex.types.getOrNull(tIdx) ?: "Lunknown;"}")
                    pc += 2
                }
                0x27 -> { out.add("throw v$hi"); pc += 1 }
                0x28 -> {
                    val off = hi.toByte().toInt()
                    out.add("goto :offset_${pc + off}")
                    pc += 1
                }
                in 0x32..0x37 -> {
                    val name = when (opcode) {
                        0x32 -> "if-eq"; 0x33 -> "if-ne"; 0x34 -> "if-lt"
                        0x35 -> "if-ge"; 0x36 -> "if-gt"; else -> "if-le"
                    }
                    val off = if (pc + 1 < insns.size) insns[pc + 1].toShort().toInt() else 0
                    out.add("$name v${hi and 0xF}, v${(hi ushr 4) and 0xF}, :cond_${pc + off}")
                    pc += 2
                }
                in 0x38..0x3d -> {
                    val name = when (opcode) {
                        0x38 -> "if-eqz"; 0x39 -> "if-nez"; 0x3a -> "if-ltz"
                        0x3b -> "if-gez"; 0x3c -> "if-gtz"; else -> "if-lez"
                    }
                    val off = if (pc + 1 < insns.size) insns[pc + 1].toShort().toInt() else 0
                    out.add("$name v$hi, :cond_${pc + off}")
                    pc += 2
                }
                in 0x52..0x5f -> {
                    val opName = when (opcode) {
                        0x52 -> "iget"; 0x54 -> "iget-object"; 0x55 -> "iget-boolean"
                        0x59 -> "iput"; 0x5b -> "iput-object"; 0x5c -> "iput-boolean"
                        else -> "field-op-0x%02x".format(opcode)
                    }
                    val fIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    val fSig = dex.fieldsTable.getOrNull(fIdx) ?: "field@$fIdx"
                    out.add("$opName v${hi and 0xF}, v${(hi ushr 4) and 0xF}, $fSig")
                    pc += 2
                }
                in 0x60..0x6d -> {
                    val opName = when (opcode) {
                        0x60 -> "sget"; 0x62 -> "sget-object"; 0x63 -> "sget-boolean"
                        0x67 -> "sput"; 0x69 -> "sput-object"; 0x6a -> "sput-boolean"
                        else -> "sfield-op-0x%02x".format(opcode)
                    }
                    val fIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    val fSig = dex.fieldsTable.getOrNull(fIdx) ?: "field@$fIdx"
                    out.add("$opName v$hi, $fSig")
                    pc += 2
                }
                in 0x6e..0x72 -> {
                    val opName = when (opcode) {
                        0x6e -> "invoke-virtual"
                        0x6f -> "invoke-super"
                        0x70 -> "invoke-direct"
                        0x71 -> "invoke-static"
                        else -> "invoke-interface"
                    }
                    val count = (hi ushr 4) and 0xF
                    val g = hi and 0xF
                    val mIdx = if (pc + 1 < insns.size) insns[pc + 1] else 0
                    val regsWord = if (pc + 2 < insns.size) insns[pc + 2] else 0
                    val regs = listOf(
                        regsWord and 0xF,
                        (regsWord ushr 4) and 0xF,
                        (regsWord ushr 8) and 0xF,
                        (regsWord ushr 12) and 0xF,
                        g
                    ).take(count.coerceIn(0, 5)).joinToString(", ") { "v$it" }
                    val mSig = dex.methodsTable.getOrNull(mIdx) ?: "method@$mIdx"
                    out.add("$opName {$regs}, $mSig")
                    pc += 3
                }
                else -> {
                    val width = opcodeWidth(opcode)
                    out.add(".op 0x%02x # raw=0x%04x".format(opcode, unit))
                    pc += width
                }
            }
        }
        return out
    }

    private fun opcodeWidth(opcode: Int): Int {
        return when (opcode) {
            0x00, 0x01, 0x04, 0x07, in 0x0a..0x12, 0x27, 0x28, in 0x7b..0x8f, in 0xb0..0xcf -> 1
            0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, in 0x1f..0x23, 0x29, in 0x2d..0x3d, in 0x44..0x6d, in 0x90..0xaf, in 0xd0..0xe2 -> 2
            0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, in 0x24..0x26, in 0x2a..0x2c, in 0x6e..0x78 -> 3
            0x18 -> 5
            else -> 1
        }
    }

    private fun readMutf8String(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= bytes.size) return ""
        var pos = offset
        // Skip ULEB128 utf16_size
        while (pos < bytes.size) {
            val b = bytes[pos++].toInt() and 0xFF
            if ((b and 0x80) == 0) break
        }
        val start = pos
        while (pos < bytes.size && bytes[pos].toInt() != 0) {
            pos++
        }
        return String(bytes, start, (pos - start).coerceAtLeast(0), Charsets.UTF_8)
    }

    private class UlebReader(private val bytes: ByteArray, startPos: Int) {
        var pos = startPos
        fun readUleb128(): Int {
            var result = 0
            var shift = 0
            while (pos < bytes.size) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            return result
        }
    }
}

private fun descriptorToReadable(desc: String): String {
    if (desc.isEmpty()) return "void"
    return when {
        desc == "V" -> "void"
        desc == "Z" -> "boolean"
        desc == "B" -> "byte"
        desc == "S" -> "short"
        desc == "C" -> "char"
        desc == "I" -> "int"
        desc == "J" -> "long"
        desc == "F" -> "float"
        desc == "D" -> "double"
        desc.startsWith("[") -> descriptorToReadable(desc.substring(1)) + "[]"
        desc.startsWith("L") && desc.endsWith(";") -> desc.substring(1, desc.length - 1).replace('/', '.')
        else -> desc
    }
}

private fun formatAccessFlags(flags: Int, isField: Boolean): String {
    val parts = mutableListOf<String>()
    if ((flags and 0x0001) != 0) parts.add("public")
    if ((flags and 0x0002) != 0) parts.add("private")
    if ((flags and 0x0004) != 0) parts.add("protected")
    if ((flags and 0x0008) != 0) parts.add("static")
    if ((flags and 0x0010) != 0) parts.add("final")
    if (!isField && (flags and 0x0020) != 0) parts.add("synchronized")
    if ((flags and 0x0400) != 0) parts.add("abstract")
    if (!isField && (flags and 0x0100) != 0) parts.add("native")
    if (!isField && (flags and 0x10000) != 0) parts.add("constructor")
    return if (parts.isEmpty()) "" else parts.joinToString(" ") + " "
}
