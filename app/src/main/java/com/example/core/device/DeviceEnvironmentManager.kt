package com.example.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

enum class RootImplementation(val displayName: String) {
    MAGISK("Magisk"),
    KITSUNE_MASK("Kitsune Mask"),
    KERNEL_SU("KernelSU"),
    APATCH("APatch"),
    SUPERSU("SuperSU / Generic su"),
    NONE("Non-Root (Standard User)")
}

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}

data class BatteryDetails(
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val technology: String
)

data class DeviceEnvironmentInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val deviceName: String,
    val androidVersion: String,
    val sdkLevel: Int,
    val securityPatch: String,
    val supportedAbis: List<String>,
    val primaryArchitecture: String,
    val internalStorage: StorageStats,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val rootAvailable: Boolean,
    val suBinaryPath: String?,
    val rootImplementation: RootImplementation,
    val shizukuInstalled: Boolean,
    val allFilesAccessGranted: Boolean,
    val workspaceDir: File,
    val supportedOperations: List<String>
)

class DeviceEnvironmentManager(private val context: Context) {

    fun inspectEnvironment(): DeviceEnvironmentInfo {
        val suPath = findSuBinary()
        val rootImpl = detectRootImplementation(suPath)
        val rootAvailable = suPath != null || rootImpl != RootImplementation.NONE
        val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
        val allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }

        val storage = readStorageStats(Environment.getDataDirectory())
        val (totalRam, availRam) = readRamStats()
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val primaryArch = abis.firstOrNull() ?: System.getProperty("os.arch").orEmpty()
        val workspace = File(context.filesDir, "GENDEV_Workspace").apply { mkdirs() }

        val operations = buildList {
            add("Workspace & App-Scoped File Operations")
            add("Storage Access Framework (SAF) Trees")
            add("ZIP / APK / XAPK / APKS / AAB Inspection")
            add("Binary AXML & DEX/Smali Disassembly")
            add("APK Rebuild, ZipAlign & v1 JAR Signing")
            add("Standard Android Shell Execution")
            if (allFilesGranted) add("Full Shared Storage (/storage/emulated/0)")
            if (shizukuInstalled) add("Shizuku Elevated Package Bridge Ready")
            if (rootAvailable) add("Authorized Root (su) Filesystem & Shell")
        }

        return DeviceEnvironmentInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            deviceName = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            supportedAbis = abis,
            primaryArchitecture = primaryArch,
            internalStorage = storage,
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            rootAvailable = rootAvailable,
            suBinaryPath = suPath,
            rootImplementation = rootImpl,
            shizukuInstalled = shizukuInstalled,
            allFilesAccessGranted = allFilesGranted,
            workspaceDir = workspace,
            supportedOperations = operations
        )
    }

    fun readStorageStats(targetDir: File): StorageStats {
        return try {
            val stat = StatFs(targetDir.absolutePath)
            val total = stat.blockSizeLong * stat.blockCountLong
            val free = stat.blockSizeLong * stat.availableBlocksLong
            StorageStats(
                totalBytes = total,
                freeBytes = free,
                usedBytes = (total - free).coerceAtLeast(0L)
            )
        } catch (_: Exception) {
            StorageStats(0L, 0L, 0L)
        }
    }

    fun readRamStats(): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.totalMem to memInfo.availMem
        } catch (_: Exception) {
            0L to 0L
        }
    }

    fun readBatteryDetails(): BatteryDetails {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
        return BatteryDetails(
            levelPercent = pct,
            isCharging = charging,
            temperatureCelsius = tempTenths / 10f,
            voltageMv = voltage,
            technology = tech
        )
    }

    fun findSuBinary(paths: List<String> = DEFAULT_SU_PATHS): String? {
        return paths.firstOrNull { path ->
            try {
                val f = File(path)
                f.exists() && f.canExecute()
            } catch (_: SecurityException) {
                false
            }
        }
    }

    fun detectRootImplementation(suPath: String?): RootImplementation {
        return when {
            isPackageInstalled("io.github.huskydg.magisk") ||
                File("/data/adb/kitsune").exists() -> RootImplementation.KITSUNE_MASK
            isPackageInstalled("me.weishu.kernelsu") ||
                File("/data/adb/ksu").exists() -> RootImplementation.KERNEL_SU
            isPackageInstalled("me.bmax.apatch") ||
                File("/data/adb/ap").exists() -> RootImplementation.APATCH
            isPackageInstalled("com.topjohnwu.magisk") ||
                File("/data/adb/magisk").exists() -> RootImplementation.MAGISK
            suPath != null -> RootImplementation.SUPERSU
            else -> RootImplementation.NONE
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        val DEFAULT_SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/su/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/vendor/bin/su"
        )
    }
}
