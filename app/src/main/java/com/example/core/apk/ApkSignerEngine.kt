package com.example.core.apk

import android.content.Context
import com.example.core.common.HashUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ApkSignatureVerification(
    val isSignedV1: Boolean,
    val hasV2OrV3Block: Boolean,
    val verifiedIntegrity: Boolean,
    val statusLabel: String,
    val certificates: List<ApkCertificateInfo>
)

data class ApkCertificateInfo(
    val aliasFile: String,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String,
    val validUntil: String,
    val sigAlgName: String,
    val sha256: String,
    val sha1: String,
    val md5: String
)

class ApkSignerEngine(private val context: Context) {

    private val keystoreDir: File
        get() = File(context.filesDir, "keystore").apply { mkdirs() }

    private val privateKeyFile: File
        get() = File(keystoreDir, "gendev_rsa2048.pk8")

    private val certificateFile: File
        get() = File(keystoreDir, "gendev_rsa2048.der")

    fun getOrCreateSigningIdentity(): Pair<PrivateKey, X509Certificate> {
        if (privateKeyFile.exists() && certificateFile.exists()) {
            try {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privateKeyFile.readBytes()))
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(certificateFile.inputStream()) as X509Certificate
                return priv to cert
            } catch (_: Exception) {
                // Regenerate cleanly if corrupted
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val certBytes = buildSelfSignedX509Der(kp, "CN=GENDEV Manager Debug, O=GENDEV Studio, C=US")
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(certBytes.inputStream()) as X509Certificate

        privateKeyFile.writeBytes(kp.private.encoded)
        certificateFile.writeBytes(certBytes)
        return kp.private to cert
    }

    fun inspectAndVerify(apkFile: File): ApkSignatureVerification {
        if (!apkFile.exists() || !apkFile.isFile) {
            return ApkSignatureVerification(false, false, false, "File Missing", emptyList())
        }
        val hasV2V3 = hasApkSigningBlockV2(apkFile)
        val certs = mutableListOf<ApkCertificateInfo>()
        var manifestEntriesMatch = false
        var hasCertBlock = false

        ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("META-INF/MANIFEST.MF")
            val sigFiles = zip.entries().asSequence()
                .filter {
                    val u = it.name.uppercase(Locale.US)
                    u.startsWith("META-INF/") && (u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC"))
                }.toList()

            hasCertBlock = sigFiles.isNotEmpty()
            for (sf in sigFiles) {
                val bytes = zip.getInputStream(sf).use { it.readBytes() }
                parseCertificatesFromBlock(sf.name, bytes, certs)
            }

            if (manifestEntry != null) {
                val mfText = zip.getInputStream(manifestEntry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                manifestEntriesMatch = verifyManifestDigests(zip, mfText)
            }
        }

        val status = when {
            hasCertBlock && manifestEntriesMatch && hasV2V3 -> "Valid (v1 JAR + v2/v3 Block)"
            hasCertBlock && manifestEntriesMatch -> "Valid (v1 JAR Signed)"
            hasV2V3 -> "v2/v3 Signature Block Present"
            hasCertBlock && !manifestEntriesMatch -> "Signature Digest Mismatch"
            else -> "Unsigned APK"
        }

        return ApkSignatureVerification(
            isSignedV1 = hasCertBlock,
            hasV2OrV3Block = hasV2V3,
            verifiedIntegrity = (hasCertBlock && manifestEntriesMatch) || hasV2V3,
            statusLabel = status,
            certificates = certs
        )
    }

    fun signAndAlignApk(inputZip: File, outputApk: File): Result<File> = runCatching {
        val (privateKey, cert) = getOrCreateSigningIdentity()
        val certEncoded = cert.encoded
        val b64Encoder = Base64.getEncoder()

        val manifestSections = StringBuilder()
        manifestSections.append("Manifest-Version: 1.0\r\n")
        manifestSections.append("Created-By: GENDEV MANAGER ApkSigner\r\n\r\n")

        val sfSections = StringBuilder()
        val entryDigests = mutableListOf<Pair<String, String>>()

        val tempEntries = mutableListOf<Triple<String, ByteArray, Int>>()
        ZipFile(inputZip).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && !it.name.startsWith("META-INF/", ignoreCase = true) }
                .sortedBy { it.name }
                .toList()
            for (entry in entries) {
                val data = zip.getInputStream(entry).use { it.readBytes() }
                val sha256B64 = b64Encoder.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(data)
                )
                val section = "Name: ${entry.name}\r\nSHA-256-Digest: $sha256B64\r\n\r\n"
                manifestSections.append(section)
                val sectionDigest = b64Encoder.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(section.toByteArray(StandardCharsets.UTF_8))
                )
                entryDigests.add(entry.name to sectionDigest)
                val shouldStore = entry.name == "resources.arsc" || entry.name.endsWith(".so")
                tempEntries.add(Triple(entry.name, data, if (shouldStore) ZipEntry.STORED else ZipEntry.DEFLATED))
            }
        }

        val manifestBytes = manifestSections.toString().toByteArray(StandardCharsets.UTF_8)
        val manifestFullDigest = b64Encoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(manifestBytes)
        )
        sfSections.append("Signature-Version: 1.0\r\n")
        sfSections.append("Created-By: GENDEV MANAGER ApkSigner\r\n")
        sfSections.append("SHA-256-Digest-Manifest: $manifestFullDigest\r\n\r\n")
        for ((name, secDigest) in entryDigests) {
            sfSections.append("Name: $name\r\nSHA-256-Digest: $secDigest\r\n\r\n")
        }
        val sfBytes = sfSections.toString().toByteArray(StandardCharsets.UTF_8)
        val pkcs7Bytes = buildPkcs7SignedData(sfBytes, privateKey, certEncoded, cert)

        outputApk.parentFile?.mkdirs()
        var currentOffset = 0L
        ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
            fun writeEntry(name: String, data: ByteArray, method: Int) {
                val entry = ZipEntry(name)
                entry.method = method
                if (method == ZipEntry.STORED) {
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    val crc = CRC32().apply { update(data) }.value
                    entry.crc = crc
                    val headerLen = 30L + name.toByteArray(StandardCharsets.UTF_8).size
                    val alignMod = (currentOffset + headerLen) % 4L
                    if (alignMod != 0L) {
                        val padLen = (4 - alignMod).toInt()
                        entry.extra = ByteArray(padLen)
                    }
                }
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
                currentOffset += 30L + name.toByteArray(StandardCharsets.UTF_8).size +
                    (entry.extra?.size ?: 0) + (if (method == ZipEntry.STORED) data.size.toLong() else entry.compressedSize.coerceAtLeast(0L))
            }

            writeEntry("META-INF/MANIFEST.MF", manifestBytes, ZipEntry.DEFLATED)
            writeEntry("META-INF/CERT.SF", sfBytes, ZipEntry.DEFLATED)
            writeEntry("META-INF/CERT.RSA", pkcs7Bytes, ZipEntry.DEFLATED)
            for ((name, data, method) in tempEntries) {
                writeEntry(name, data, method)
            }
        }
        outputApk
    }

    private fun parseCertificatesFromBlock(
        aliasFile: String,
        bytes: ByteArray,
        out: MutableList<ApkCertificateInfo>
    ) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val parsed = cf.generateCertificates(bytes.inputStream())
            for (c in parsed) {
                val x509 = c as? X509Certificate ?: continue
                val enc = x509.encoded
                out.add(
                    ApkCertificateInfo(
                        aliasFile = aliasFile,
                        subject = x509.subjectX500Principal.name,
                        issuer = x509.issuerX500Principal.name,
                        serialNumber = x509.serialNumber.toString(16).uppercase(Locale.US),
                        validFrom = HashUtils.formatTimestamp(x509.notBefore.time),
                        validUntil = HashUtils.formatTimestamp(x509.notAfter.time),
                        sigAlgName = x509.sigAlgName,
                        sha256 = HashUtils.calculateHashBytes(enc, "SHA-256"),
                        sha1 = HashUtils.calculateHashBytes(enc, "SHA-1"),
                        md5 = HashUtils.calculateHashBytes(enc, "MD5")
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun verifyManifestDigests(zip: ZipFile, manifestText: String): Boolean {
        val blocks = manifestText.split("\r\n\r\n", "\n\n")
        val b64 = Base64.getDecoder()
        var checked = 0
        for (block in blocks) {
            val lines = block.lines()
            val nameLine = lines.firstOrNull { it.startsWith("Name: ") } ?: continue
            val name = nameLine.removePrefix("Name: ").trim()
            val sha256Line = lines.firstOrNull { it.startsWith("SHA-256-Digest: ") }
            val sha1Line = lines.firstOrNull { it.startsWith("SHA1-Digest: ") || it.startsWith("SHA-1-Digest: ") }
            val entry = zip.getEntry(name) ?: return false
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            if (sha256Line != null) {
                val expected = b64.decode(sha256Line.substringAfter(": ").trim())
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                if (!expected.contentEquals(actual)) return false
                checked++
            } else if (sha1Line != null) {
                val expected = b64.decode(sha1Line.substringAfter(": ").trim())
                val actual = MessageDigest.getInstance("SHA-1").digest(bytes)
                if (!expected.contentEquals(actual)) return false
                checked++
            }
        }
        return checked > 0
    }

    private fun hasApkSigningBlockV2(apkFile: File): Boolean {
        return try {
            val len = apkFile.length()
            if (len < 64) return false
            java.io.RandomAccessFile(apkFile, "r").use { raf ->
                val searchTail = minOf(len, 65560L).toInt()
                val tail = ByteArray(searchTail)
                raf.seek(len - searchTail)
                raf.readFully(tail)
                val magic = "APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII)
                tail.toList().windowed(magic.size).any { it.toByteArray().contentEquals(magic) }
            }
        } catch (_: Exception) {
            false
        }
    }

    // Pure-Kotlin ASN.1 DER X.509 v3 self-signed certificate builder (no sun.security dependencies)
    private fun buildSelfSignedX509Der(keyPair: KeyPair, commonName: String): ByteArray {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 3650L * 86_400_000L)
        val serial = BigInteger.valueOf(now and 0x7FFF_FFFF_FFFFL)

        val sha256WithRsaOid = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B))
        val sigAlgId = derSeq(sha256WithRsaOid + derNull())
        val dn = buildDn(commonName)
        val validity = derSeq(derUtcTime(notBefore) + derUtcTime(notAfter))
        val spki = keyPair.public.encoded
        val version = derTaggedExplicit(0, derInteger(BigInteger.valueOf(2)))

        val tbs = derSeq(
            version +
                derInteger(serial) +
                sigAlgId +
                dn +
                validity +
                dn +
                spki
        )

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(tbs)
        }.sign()

        return derSeq(tbs + sigAlgId + derBitString(sig))
    }

    // Pure-Kotlin ASN.1 DER PKCS#7 SignedData builder for META-INF/CERT.RSA
    private fun buildPkcs7SignedData(
        sfBytes: ByteArray,
        privateKey: PrivateKey,
        certDer: ByteArray,
        cert: X509Certificate
    ): ByteArray {
        val oidSignedData = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02))
        val oidData = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01))
        val oidSha256 = derOid(byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01))
        val oidRsa = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01))

        val digestAlgId = derSeq(oidSha256 + derNull())
        val digestAlgs = derSet(digestAlgId)
        val contentInfo = derSeq(oidData)
        val certificates = derTaggedExplicit(0, certDer)

        val sigBytes = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(sfBytes)
        }.sign()

        val issuerAndSerial = derSeq(cert.issuerX500Principal.encoded + derInteger(cert.serialNumber))
        val signerInfo = derSeq(
            derInteger(BigInteger.ONE) +
                issuerAndSerial +
                digestAlgId +
                derSeq(oidRsa + derNull()) +
                derOctetString(sigBytes)
        )
        val signerInfos = derSet(signerInfo)

        val signedData = derSeq(
            derInteger(BigInteger.ONE) +
                digestAlgs +
                contentInfo +
                certificates +
                signerInfos
        )

        return derSeq(oidSignedData + derTaggedExplicit(0, signedData))
    }

    private fun buildDn(cnText: String): ByteArray {
        val cnVal = cnText.substringAfter("CN=").substringBefore(",").trim().ifEmpty { "GENDEV" }
        val oidCn = derOid(byteArrayOf(0x55, 0x04, 0x03))
        val atv = derSeq(oidCn + derUtf8String(cnVal))
        return derSeq(derSet(atv))
    }

    private fun derWrap(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len < 256 -> {
                out.write(0x81); out.write(len)
            }
            len < 65536 -> {
                out.write(0x82); out.write((len shr 8) and 0xFF); out.write(len and 0xFF)
            }
            else -> {
                out.write(0x83); out.write((len shr 16) and 0xFF); out.write((len shr 8) and 0xFF); out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun derSeq(content: ByteArray) = derWrap(0x30, content)
    private fun derSet(content: ByteArray) = derWrap(0x31, content)
    private fun derTaggedExplicit(tagNo: Int, content: ByteArray) = derWrap(0xA0 or tagNo, content)
    private fun derInteger(value: BigInteger) = derWrap(0x02, value.toByteArray())
    private fun derBitString(bytes: ByteArray) = derWrap(0x03, byteArrayOf(0x00) + bytes)
    private fun derOctetString(bytes: ByteArray) = derWrap(0x04, bytes)
    private fun derNull() = byteArrayOf(0x05, 0x00)
    private fun derOid(encodedBody: ByteArray) = derWrap(0x06, encodedBody)
    private fun derUtf8String(text: String) = derWrap(0x0C, text.toByteArray(StandardCharsets.UTF_8))
    private fun derUtcTime(date: Date): ByteArray {
        val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return derWrap(0x17, fmt.format(date).toByteArray(StandardCharsets.US_ASCII))
    }
}
