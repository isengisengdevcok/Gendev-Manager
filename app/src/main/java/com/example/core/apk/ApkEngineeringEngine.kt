package com.example.core.apk

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.example.core.common.HashUtils
import com.example.core.storage.ArchiveEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class InstalledAppSummary(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceApkPath: String,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean,
    val targetSdk: Int,
    val minSdk: Int
)

data class ApkInspectionResult(
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val md5: String,
    val manifest: ParsedManifestInfo?,
    val signature: ApkSignatureVerification,
    val resourceTable: ArscTableInfo?,
    val dexFiles: List<String>,
    val nativeAbis: List<String>,
    val totalEntries: Int,
    val entries: List<ArchiveEntryItem>,
    val isSplitOrBundle: Boolean,
    val bundleNote: String? = null
)

data class ApkComparisonResult(
    val leftName: String,
    val rightName: String,
    val sizeDeltaBytes: Long,
    val addedPermissions: List<String>,
    val removedPermissions: List<String>,
    val addedAbis: List<String>,
    val removedAbis: List<String>,
    val dexCountLeft: Int,
    val dexCountRight: Int,
    val addedEntriesCount: Int,
    val removedEntriesCount: Int,
    val sampleAddedEntries: List<String>,
    val sampleRemovedEntries: List<String>
)

data class ApkRebuildResult(
    val outputApk: File,
    val verifiedValid: Boolean,
    val signatureStatus: String,
    val totalEntries: Int,
    val sha256: String
)

class ApkEngineeringEngine(
    private val context: Context,
    private val signerEngine: ApkSignerEngine
) {

    suspend fun inspectApk(apkFile: File): Result<ApkInspectionResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkFile.exists() && apkFile.isFile) { "APK file not found: ${apkFile.name}" }
            val ext = apkFile.extension.lowercase()
            val isBundle = ext in setOf("apks", "xapk", "aab")
            var manifestInfo: ParsedManifestInfo? = null
            var arscInfo: ArscTableInfo? = null
            val dexFiles = mutableListOf<String>()
            val abis = sortedSetOf<String>()
            val entries = mutableListOf<ArchiveEntryItem>()

            ZipFile(apkFile).use { zip ->
                val all = zip.entries().asSequence().toList()
                for (entry in all) {
                    entries.add(
                        ArchiveEntryItem(
                            path = entry.name,
                            name = entry.name.trimEnd('/').substringAfterLast('/'),
                            isDirectory = entry.isDirectory,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            crc = entry.crc,
                            method = entry.method
                        )
                    )
                    if (!entry.isDirectory) {
                        if (entry.name == "AndroidManifest.xml") {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            manifestInfo = runCatching { AxmlParser.decode(bytes) }.getOrNull()
                        } else if (entry.name == "resources.arsc") {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            arscInfo = runCatching { ArscParser.parse(bytes) }.getOrNull()
                        } else if (entry.name.endsWith(".dex") && !entry.name.contains('/')) {
                            dexFiles.add(entry.name)
                        } else if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                            val parts = entry.name.split('/')
                            if (parts.size >= 3) abis.add(parts[1])
                        }
                    }
                }
            }

            val signature = signerEngine.inspectAndVerify(apkFile)
            val bundleNote = when (ext) {
                "apks", "xapk" -> "Split APK container detected (${entries.count { it.name.endsWith(".apk") }} inner APKs). Extract inner base.apk to edit DEX/Manifest."
                "aab" -> "Android App Bundle (.aab) detected. Protobuf manifest & base/ module structure."
                else -> null
            }

            ApkInspectionResult(
                filePath = apkFile.absolutePath,
                fileName = apkFile.name,
                fileSizeBytes = apkFile.length(),
                sha256 = HashUtils.calculateHash(apkFile, "SHA-256"),
                md5 = HashUtils.calculateHash(apkFile, "MD5"),
                manifest = manifestInfo,
                signature = signature,
                resourceTable = arscInfo,
                dexFiles = dexFiles.sorted(),
                nativeAbis = abis.toList(),
                totalEntries = entries.size,
                entries = entries.sortedWith(compareBy<ArchiveEntryItem> { !it.isDirectory }.thenBy { it.path }),
                isSplitOrBundle = isBundle,
                bundleNote = bundleNote
            )
        }
    }

    suspend fun compareApks(left: ApkInspectionResult, right: ApkInspectionResult): ApkComparisonResult =
        withContext(Dispatchers.Default) {
            val leftPerms = left.manifest?.permissions.orEmpty().toSet()
            val rightPerms = right.manifest?.permissions.orEmpty().toSet()
            val leftPaths = left.entries.filter { !it.isDirectory }.map { it.path }.toSet()
            val rightPaths = right.entries.filter { !it.isDirectory }.map { it.path }.toSet()
            val added = (rightPaths - leftPaths).sorted()
            val removed = (leftPaths - rightPaths).sorted()
            ApkComparisonResult(
                leftName = left.fileName,
                rightName = right.fileName,
                sizeDeltaBytes = right.fileSizeBytes - left.fileSizeBytes,
                addedPermissions = (rightPerms - leftPerms).sorted(),
                removedPermissions = (leftPerms - rightPerms).sorted(),
                addedAbis = (right.nativeAbis.toSet() - left.nativeAbis.toSet()).sorted(),
                removedAbis = (left.nativeAbis.toSet() - right.nativeAbis.toSet()).sorted(),
                dexCountLeft = left.dexFiles.size,
                dexCountRight = right.dexFiles.size,
                addedEntriesCount = added.size,
                removedEntriesCount = removed.size,
                sampleAddedEntries = added.take(25),
                sampleRemovedEntries = removed.take(25)
            )
        }

    suspend fun rebuildAndSignApk(
        sourceApk: File,
        outputApk: File,
        replacements: Map<String, ByteArray> = emptyMap(),
        removeEntries: Set<String> = emptySet()
    ): Result<ApkRebuildResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceApk.exists() && sourceApk.isFile) { "Source APK missing" }
            val tempUnsigned = File(context.cacheDir, "rebuild_unsigned_${System.currentTimeMillis()}.apk")
            try {
                ZipFile(sourceApk).use { zip ->
                    ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                        val written = mutableSetOf<String>()
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            if (entry.name in removeEntries) continue
                            if (entry.name.startsWith("META-INF/", ignoreCase = true)) continue
                            val data = replacements[entry.name] ?: zip.getInputStream(entry).use { it.readBytes() }
                            zos.putNextEntry(ZipEntry(entry.name))
                            zos.write(data)
                            zos.closeEntry()
                            written.add(entry.name)
                        }
                        for ((path, data) in replacements) {
                            if (path !in written) {
                                zos.putNextEntry(ZipEntry(path))
                                zos.write(data)
                                zos.closeEntry()
                            }
                        }
                    }
                }
                signerEngine.signAndAlignApk(tempUnsigned, outputApk).getOrThrow()
            } finally {
                tempUnsigned.delete()
            }

            // Integrity validation gate: never report success without validating output
            val verifiedSig = signerEngine.inspectAndVerify(outputApk)
            val entryCount = ZipFile(outputApk).use { z ->
                val manifestEntry = z.getEntry("AndroidManifest.xml")
                    ?: error("Validation failed: AndroidManifest.xml missing in rebuilt APK")
                val mBytes = z.getInputStream(manifestEntry).use { it.readBytes() }
                if (AxmlParser.isBinaryAxml(mBytes)) {
                    val decoded = AxmlParser.decode(mBytes)
                    require(decoded.packageName.isNotBlank()) { "Validation failed: invalid package name in manifest" }
                }
                z.size()
            }
            check(verifiedSig.verifiedIntegrity) {
                "Validation failed: APK signature verification did not pass (${verifiedSig.statusLabel})"
            }

            ApkRebuildResult(
                outputApk = outputApk,
                verifiedValid = verifiedSig.verifiedIntegrity,
                signatureStatus = verifiedSig.statusLabel,
                totalEntries = entryCount,
                sha256 = HashUtils.calculateHash(outputApk, "SHA-256")
            )
        }
    }

    suspend fun loadInstalledApps(includeSystem: Boolean = false): List<InstalledAppSummary> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            packages.mapNotNull { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@mapNotNull null
                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                val apkFile = File(sourceDir)
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                InstalledAppSummary(
                    appName = appInfo.loadLabel(pm)?.toString() ?: pkgInfo.packageName,
                    packageName = pkgInfo.packageName,
                    versionName = pkgInfo.versionName ?: "1.0",
                    versionCode = vCode,
                    sourceApkPath = sourceDir,
                    apkSizeBytes = if (apkFile.exists()) apkFile.length() else 0L,
                    isSystemApp = isSystem,
                    targetSdk = appInfo.targetSdkVersion,
                    minSdk = appInfo.minSdkVersion
                )
            }.sortedBy { it.appName.lowercase() }
        }

    suspend fun backupInstalledApk(app: InstalledAppSummary, backupsDir: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(app.sourceApkPath)
                require(src.exists() && src.canRead()) { "Cannot read APK source path: ${app.sourceApkPath}" }
                if (!backupsDir.exists()) backupsDir.mkdirs()
                val safeName = "${app.packageName}_v${app.versionName}.apk"
                val dest = File(backupsDir, safeName)
                src.copyTo(dest, overwrite = true)
                dest
            }
        }

    fun launchSystemApkInstaller(apkFile: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
