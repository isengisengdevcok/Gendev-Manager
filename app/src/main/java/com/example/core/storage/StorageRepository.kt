package com.example.core.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.core.common.HashUtils
import com.example.core.root.RootShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageRepository(
    private val context: Context,
    private val rootShellManager: RootShellManager = RootShellManager()
) {

    val workspaceDir: File
        get() = File(context.filesDir, "GENDEV_Workspace").apply { mkdirs() }

    val backupsDir: File
        get() = File(workspaceDir, "Backups").apply { mkdirs() }

    val rebuildsDir: File
        get() = File(workspaceDir, "Rebuilt_APKs").apply { mkdirs() }

    suspend fun ensureWorkspaceInitialized() = withContext(Dispatchers.IO) {
        val samplesDir = File(workspaceDir, "Samples").apply { mkdirs() }
        val scriptsDir = File(workspaceDir, "Scripts").apply { mkdirs() }
        backupsDir
        rebuildsDir

        val manifestSample = File(samplesDir, "AndroidManifest_sample.xml")
        if (!manifestSample.exists()) {
            manifestSample.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.gendev.sample"
    android:versionCode="10"
    android:versionName="1.0.0">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="false"
        android:debuggable="false"
        android:label="GENDEV Sample">
        <activity android:name=".MainActivity" android:exported="true" />
    </application>
</manifest>
""".trimIndent()
            )
        }

        val jsonSample = File(samplesDir, "build_profile.json")
        if (!jsonSample.exists()) {
            jsonSample.writeText(
                """{
  "application_name": "GENDEV MANAGER",
  "package_name": "com.aistudio.gendevmanager.kxrtvp",
  "abis": ["arm64-v8a", "armeabi-v7a", "x86_64"],
  "min_sdk": 26,
  "target_sdk": 36,
  "features": {
    "dual_pane": true,
    "dex_disassembler": true,
    "axml_decoder": true,
    "hybrid_ai": true
  }
}
""".trimIndent()
            )
        }

        val smaliSample = File(samplesDir, "DemoHook.smali")
        if (!smaliSample.exists()) {
            smaliSample.writeText(
                """.class public Lcom/gendev/sample/DemoHook;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static getEngineTag()Ljava/lang/String;
    .registers 1
    const-string v0, "GENDEV_ENGINE_V1"
    return-object v0
.end method
""".trimIndent()
            )
        }

        val scriptSample = File(scriptsDir, "device_check.sh")
        if (!scriptSample.exists()) {
            scriptSample.writeText(
                """#!/system/bin/sh
echo "=== GENDEV Device Diagnostic ==="
uname -a
getprop ro.product.model
getprop ro.build.version.release
df -h /data
""".trimIndent()
            )
        }

        // Heavy self-APK copy deferred out of cold launch for instant Home startup
    }

    suspend fun ensureSelfApkSample(): File? = withContext(Dispatchers.IO) {
        val samplesDir = File(workspaceDir, "Samples").apply { mkdirs() }
        val targetSelfApk = File(samplesDir, "GENDEV_Manager_self.apk")
        if (targetSelfApk.exists()) return@withContext targetSelfApk
        try {
            val selfApk = File(context.applicationInfo.sourceDir)
            if (selfApk.exists() && selfApk.canRead()) {
                selfApk.copyTo(targetSelfApk, overwrite = false)
                return@withContext targetSelfApk
            }
        } catch (_: Exception) {
        }
        null
    }

    fun getPhoneStorageDirectory(): File? =
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()

    fun getQuickRoots(): List<Pair<String, File>> {
        val roots = mutableListOf<Pair<String, File>>()
        val sdcard = getPhoneStorageDirectory()
        if (sdcard != null && sdcard.exists() && sdcard.canRead()) {
            roots.add("Phone Storage" to sdcard)
            val downloadDir = File(sdcard, "Download")
            if (downloadDir.exists() && downloadDir.isDirectory && downloadDir.canRead()) {
                roots.add("Download" to downloadDir)
            }
            val docsDir = File(sdcard, "Documents")
            if (docsDir.exists() && docsDir.isDirectory && docsDir.canRead()) {
                roots.add("Documents" to docsDir)
            }
        }
        roots.add("Workspace" to workspaceDir)
        roots.add("Samples" to File(workspaceDir, "Samples").apply { mkdirs() })
        roots.add("App Cache" to context.cacheDir)
        context.getExternalFilesDir(null)?.let {
            roots.add("App External" to it)
        }
        return roots
    }

    fun getRootQuickRoots(): List<Pair<String, File>> = listOf(
        "/ (Root)" to File("/"),
        "/data/local/tmp" to File("/data/local/tmp"),
        "/data" to File("/data"),
        "/system" to File("/system"),
        "/vendor" to File("/vendor"),
        "/sdcard" to File("/sdcard")
    )

    private fun isAppOrNormalMediaDir(dir: File): Boolean {
        val abs = dir.absolutePath
        if (abs.startsWith(context.filesDir.absolutePath) || abs.startsWith(context.cacheDir.absolutePath)) {
            return true
        }
        val extApp = context.getExternalFilesDir(null)?.absolutePath
        if (extApp != null && abs.startsWith(extApp)) return true
        val phone = getPhoneStorageDirectory()?.absolutePath
        if (phone != null && abs.startsWith(phone) && !abs.contains("/Android/data") && !abs.contains("/Android/obb")) {
            return true
        }
        return false
    }

    suspend fun listDirectory(
        dir: File,
        showHidden: Boolean = false,
        sortBy: SortBy = SortBy.NAME,
        ascending: Boolean = true,
        filterCategory: FilterCategory = FilterCategory.ALL,
        query: String = "",
        useRoot: Boolean = false,
        suBinary: String? = null
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val shouldUseRoot = useRoot && !suBinary.isNullOrBlank() &&
            (!isAppOrNormalMediaDir(dir) || !dir.canRead())

        val rawItems: List<FileItem> = if (shouldUseRoot) {
            RootFileBridge.listDirectoryRoot(
                shell = rootShellManager,
                suBinary = suBinary!!,
                dirPath = dir.absolutePath,
                showHidden = showHidden,
                categorizer = ::categorizeExtension
            ).getOrElse {
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    dir.listFiles()?.toList().orEmpty()
                        .asSequence()
                        .filter { showHidden || !it.name.startsWith(".") }
                        .map { file -> toFileItem(file) }
                        .toList()
                } else {
                    emptyList()
                }
            }
        } else if (dir.exists() && dir.isDirectory && dir.canRead()) {
            dir.listFiles()?.toList().orEmpty()
                .asSequence()
                .filter { showHidden || !it.name.startsWith(".") }
                .map { file -> toFileItem(file) }
                .toList()
        } else {
            emptyList()
        }

        rawItems
            .asSequence()
            .filter { item ->
                if (query.isNotBlank() && !item.name.contains(query, ignoreCase = true)) {
                    return@filter false
                }
                when (filterCategory) {
                    FilterCategory.ALL -> true
                    FilterCategory.FOLDERS -> item.isDirectory
                    FilterCategory.APKS -> item.category == FileCategory.APK_BUNDLE
                    FilterCategory.ARCHIVES -> item.category == FileCategory.ARCHIVE || item.category == FileCategory.APK_BUNDLE
                    FilterCategory.CODE -> item.category in setOf(
                        FileCategory.XML, FileCategory.JSON, FileCategory.SMALI, FileCategory.CODE, FileCategory.TEXT
                    )
                    FilterCategory.IMAGES -> item.category == FileCategory.IMAGE
                }
            }
            .sortedWith(buildComparator(sortBy, ascending))
            .toList()
    }

    suspend fun searchRecursive(
        rootDir: File,
        query: String,
        showHidden: Boolean,
        maxResults: Int = 200
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !rootDir.exists() || !rootDir.canRead()) return@withContext emptyList()
        val results = mutableListOf<FileItem>()
        val stack = ArrayDeque<File>()
        stack.add(rootDir)
        while (stack.isNotEmpty() && results.size < maxResults) {
            val current = stack.removeFirst()
            val files = current.listFiles() ?: continue
            for (f in files) {
                if (!showHidden && f.name.startsWith(".")) continue
                if (f.name.contains(query, ignoreCase = true)) {
                    results.add(toFileItem(f))
                    if (results.size >= maxResults) break
                }
                if (f.isDirectory && f.canRead()) {
                    stack.addLast(f)
                }
            }
        }
        results
    }

    fun toFileItem(file: File): FileItem {
        val ext = file.extension.lowercase()
        val cat = if (file.isDirectory) {
            FileCategory.DIRECTORY
        } else {
            categorizeExtension(ext)
        }
        val childCount = if (file.isDirectory && file.canRead()) {
            file.list()?.size ?: 0
        } else 0
        return FileItem(
            name = file.name.ifEmpty { file.absolutePath },
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            isHidden = file.name.startsWith("."),
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            canExecute = file.canExecute(),
            childCount = childCount,
            extension = ext,
            category = cat
        )
    }

    fun categorizeExtension(ext: String): FileCategory {
        return when (ext.lowercase()) {
            "apk", "apks", "xapk", "aab" -> FileCategory.APK_BUNDLE
            "zip", "jar", "tar", "gz", "7z" -> FileCategory.ARCHIVE
            "xml", "axml" -> FileCategory.XML
            "json" -> FileCategory.JSON
            "dex" -> FileCategory.DEX
            "smali" -> FileCategory.SMALI
            "kt", "java", "html", "htm", "css", "js", "sh", "properties", "ini", "md", "yml", "yaml", "gradle", "kts" -> FileCategory.CODE
            "png", "jpg", "jpeg", "webp", "gif", "bmp" -> FileCategory.IMAGE
            "so", "bin", "arsc", "dat", "db", "ttf", "otf" -> FileCategory.BINARY
            else -> FileCategory.TEXT
        }
    }

    suspend fun copyFiles(
        sources: List<File>,
        targetDir: File,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val needsRoot = !targetDir.canWrite() || sources.any { !it.canRead() }
        if (needsRoot && useRoot && suBinary != null) {
            return@withContext RootFileBridge.copyRoot(rootShellManager, suBinary, sources, targetDir)
        }
        runCatching {
            if (!targetDir.exists()) targetDir.mkdirs()
            var count = 0
            for (src in sources) {
                val dest = resolveUniqueDestination(targetDir, src.name)
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = false)
                } else {
                    src.copyTo(dest, overwrite = false)
                }
                count++
            }
            count
        }
    }

    suspend fun moveFiles(
        sources: List<File>,
        targetDir: File,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val needsRoot = !targetDir.canWrite() || sources.any { !it.canRead() || it.parentFile?.canWrite() != true }
        if (needsRoot && useRoot && suBinary != null) {
            return@withContext RootFileBridge.moveRoot(rootShellManager, suBinary, sources, targetDir)
        }
        runCatching {
            if (!targetDir.exists()) targetDir.mkdirs()
            var count = 0
            for (src in sources) {
                val dest = resolveUniqueDestination(targetDir, src.name)
                val moved = src.renameTo(dest)
                if (!moved) {
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = true)
                        src.deleteRecursively()
                    } else {
                        src.copyTo(dest, overwrite = true)
                        src.delete()
                    }
                }
                count++
            }
            count
        }
    }

    suspend fun renameFile(
        file: File,
        newName: String,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (file.parentFile?.canWrite() != true && useRoot && suBinary != null) {
            return@withContext RootFileBridge.renameRoot(rootShellManager, suBinary, file, newName)
        }
        runCatching {
            val clean = newName.trim()
            require(clean.isNotEmpty() && !clean.contains('/')) { "Invalid filename" }
            val dest = File(file.parentFile, clean)
            require(!dest.exists()) { "A file named '$clean' already exists" }
            check(file.renameTo(dest)) { "Rename failed (check write permissions)" }
            dest
        }
    }

    suspend fun deleteFiles(
        files: List<File>,
        createBackup: Boolean = true,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val needsRoot = files.any { it.parentFile?.canWrite() != true }
        if (needsRoot && useRoot && suBinary != null) {
            return@withContext RootFileBridge.deleteRoot(rootShellManager, suBinary, files)
        }
        runCatching {
            var count = 0
            for (f in files) {
                if (createBackup && f.isFile && f.length() <= 20 * 1024 * 1024L && !f.absolutePath.startsWith(backupsDir.absolutePath)) {
                    val bkp = File(backupsDir, "${System.currentTimeMillis()}_${f.name}.bak")
                    runCatching { f.copyTo(bkp, overwrite = true) }
                }
                val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (ok) count++
            }
            count
        }
    }

    suspend fun createFolder(
        parent: File,
        name: String,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!parent.canWrite() && useRoot && suBinary != null) {
            return@withContext RootFileBridge.mkdirRoot(rootShellManager, suBinary, parent, name)
        }
        runCatching {
            val clean = name.trim()
            require(clean.isNotEmpty() && !clean.contains('/')) { "Invalid folder name" }
            val dir = File(parent, clean)
            require(!dir.exists()) { "Folder already exists" }
            check(dir.mkdirs()) { "Could not create folder" }
            dir
        }
    }

    suspend fun createFile(
        parent: File,
        name: String,
        initialContent: String = "",
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!parent.canWrite() && useRoot && suBinary != null) {
            return@withContext RootFileBridge.touchRoot(rootShellManager, suBinary, parent, name)
        }
        runCatching {
            val clean = name.trim()
            require(clean.isNotEmpty() && !clean.contains('/')) { "Invalid file name" }
            val file = File(parent, clean)
            require(!file.exists()) { "File already exists" }
            file.writeText(initialContent)
            file
        }
    }

    suspend fun chmodRoot(
        file: File,
        octalMode: String,
        suBinary: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        RootFileBridge.chmodRoot(rootShellManager, suBinary, file, octalMode)
    }

    suspend fun calculateRootSha256(file: File, suBinary: String): String = withContext(Dispatchers.IO) {
        RootFileBridge.sha256Root(rootShellManager, suBinary, file)
    }

    suspend fun readRootPreview(file: File, suBinary: String): String? = withContext(Dispatchers.IO) {
        RootFileBridge.readPreviewRoot(rootShellManager, suBinary, file)
    }

    suspend fun stageReadableCopyIfNeeded(
        file: File,
        useRoot: Boolean,
        suBinary: String?
    ): Result<File> = withContext(Dispatchers.IO) {
        if (file.exists() && file.canRead()) {
            Result.success(file)
        } else if (useRoot && suBinary != null) {
            val stagingDir = File(context.cacheDir, "root_staging").apply { mkdirs() }
            RootFileBridge.stageFileToCache(rootShellManager, suBinary, file, stagingDir)
        } else {
            Result.failure(IllegalAccessException("File requires Root Explorer or storage permission: ${file.name}"))
        }
    }

    suspend fun importUriToDir(uri: Uri, targetDir: File, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!targetDir.exists()) targetDir.mkdirs()
            val dest = resolveUniqueDestination(targetDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open selected document stream")
            dest
        }
    }

    suspend fun findDuplicates(rootDir: File, maxFiles: Int = 2000): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val bySize = mutableMapOf<Long, MutableList<File>>()
        var scanned = 0
        val stack = ArrayDeque<File>()
        if (rootDir.exists() && rootDir.canRead()) stack.add(rootDir)
        while (stack.isNotEmpty() && scanned < maxFiles) {
            val cur = stack.removeFirst()
            val children = cur.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory && f.canRead()) {
                    stack.addLast(f)
                } else if (f.isFile && f.length() > 0) {
                    bySize.getOrPut(f.length()) { mutableListOf() }.add(f)
                    scanned++
                }
            }
        }
        val groups = mutableListOf<DuplicateGroup>()
        for ((size, candidates) in bySize) {
            if (candidates.size < 2) continue
            val byHash = candidates.groupBy { HashUtils.calculateHash(it, "SHA-256") }
            for ((hash, dupes) in byHash) {
                if (hash.isNotEmpty() && dupes.size >= 2) {
                    groups.add(
                        DuplicateGroup(
                            sha256 = hash,
                            sizeBytes = size,
                            files = dupes.map { toFileItem(it) }
                        )
                    )
                }
            }
        }
        groups.sortedByDescending { it.sizeBytes * it.files.size }
    }

    suspend fun analyzeStorage(rootDir: File, maxFiles: Int = 3000): List<StorageCategoryStat> = withContext(Dispatchers.IO) {
        val stats = mutableMapOf<String, Pair<Long, Int>>()
        var scanned = 0
        val stack = ArrayDeque<File>()
        if (rootDir.exists() && rootDir.canRead()) stack.add(rootDir)
        while (stack.isNotEmpty() && scanned < maxFiles) {
            val cur = stack.removeFirst()
            val children = cur.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory && f.canRead()) {
                    stack.addLast(f)
                } else if (f.isFile) {
                    val label = when (categorizeExtension(f.extension)) {
                        FileCategory.APK_BUNDLE -> "APKs & Bundles"
                        FileCategory.ARCHIVE -> "Archives"
                        FileCategory.IMAGE -> "Images"
                        FileCategory.DEX, FileCategory.SMALI, FileCategory.XML, FileCategory.JSON, FileCategory.CODE -> "Code & Config"
                        FileCategory.BINARY -> "Binaries & Libs"
                        else -> "Documents & Other"
                    }
                    val prev = stats[label] ?: (0L to 0)
                    stats[label] = (prev.first + f.length()) to (prev.second + 1)
                    scanned++
                }
            }
        }
        stats.entries
            .map { StorageCategoryStat(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.bytes }
    }

    fun diffFiles(leftFile: File, rightFile: File, maxLines: Int = 400): List<FileDiffLine> {
        val leftLines = runCatching { leftFile.readLines().take(maxLines) }.getOrDefault(emptyList())
        val rightLines = runCatching { rightFile.readLines().take(maxLines) }.getOrDefault(emptyList())
        return computeLineDiff(leftLines, rightLines)
    }

    fun computeLineDiff(left: List<String>, right: List<String>): List<FileDiffLine> {
        val n = left.size
        val m = right.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (left[i] == right[j]) {
                    1 + dp[i + 1][j + 1]
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val out = mutableListOf<FileDiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                left[i] == right[j] -> {
                    out.add(FileDiffLine(i + 1, j + 1, left[i], DiffLineType.UNCHANGED))
                    i++; j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    out.add(FileDiffLine(i + 1, null, left[i], DiffLineType.REMOVED))
                    i++
                }
                else -> {
                    out.add(FileDiffLine(null, j + 1, right[j], DiffLineType.ADDED))
                    j++
                }
            }
        }
        while (i < n) {
            out.add(FileDiffLine(i + 1, null, left[i], DiffLineType.REMOVED))
            i++
        }
        while (j < m) {
            out.add(FileDiffLine(null, j + 1, right[j], DiffLineType.ADDED))
            j++
        }
        return out
    }

    private fun resolveUniqueDestination(dir: File, originalName: String): File {
        var candidate = File(dir, originalName)
        if (!candidate.exists()) return candidate
        val dot = originalName.lastIndexOf('.')
        val base = if (dot > 0) originalName.substring(0, dot) else originalName
        val ext = if (dot > 0) originalName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$index$ext")
            index++
        }
        return candidate
    }

    private fun buildComparator(sortBy: SortBy, ascending: Boolean): Comparator<FileItem> {
        val base = Comparator<FileItem> { a, b ->
            if (a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val cmp = when (sortBy) {
                SortBy.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                SortBy.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                SortBy.DATE -> a.lastModified.compareTo(b.lastModified)
                SortBy.TYPE -> a.extension.compareTo(b.extension)
            }
            if (ascending) cmp else -cmp
        }
        return base
    }
}
