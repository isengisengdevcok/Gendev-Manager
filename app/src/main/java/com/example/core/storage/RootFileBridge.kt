package com.example.core.storage

import com.example.core.root.RootShellManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object RootFileBridge {

    fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private val isoDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    private val lsLineRegex = Regex(
        """^([\-dlcbps][rwxstST\-?]{9}[+@.]?)\s+(?:\d+|\?)\s+\S+\s+\S+\s+(?:(\d+),\s*\d+|(\d+)|\?)\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\??|[A-Za-z]{3}\s+\d{1,2}\s+(?:\d{2}:\d{2}|\d{4})|\?)\s+(.+)$"""
    )

    fun parseLsLine(
        parentPath: String,
        rawLine: String,
        categorizer: (String) -> FileCategory
    ): FileItem? {
        val line = rawLine.trimEnd('\r')
        if (line.isBlank() || line.startsWith("total ")) return null
        val match = lsLineRegex.matchEntire(line) ?: return null

        val perms = match.groupValues[1]
        val sizeStr = match.groupValues[3].ifBlank { "0" }
        val dateStr = match.groupValues[4].trimEnd('?')
        val rawNamePart = match.groupValues[5]

        val isSymlink = perms.startsWith('l')
        val name: String
        val symlinkTarget: String?
        if (isSymlink && rawNamePart.contains(" -> ")) {
            val idx = rawNamePart.indexOf(" -> ")
            name = rawNamePart.substring(0, idx)
            symlinkTarget = rawNamePart.substring(idx + 4)
        } else {
            name = rawNamePart
            symlinkTarget = null
        }

        if (name == "." || name == "..") return null

        val cleanParent = if (parentPath == "/") "" else parentPath.trimEnd('/')
        val fullPath = "$cleanParent/$name"
        val isDir = perms.startsWith('d') ||
            (isSymlink && symlinkTarget != null && (!symlinkTarget.substringAfterLast('/').contains('.') || symlinkTarget.endsWith("/")))
        val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.US)
        val cat = if (isDir) FileCategory.DIRECTORY else categorizer(ext)
        val sizeBytes = sizeStr.toLongOrNull() ?: 0L
        val lastModified = runCatching {
            if (dateStr.length >= 16 && dateStr[4] == '-') {
                isoDateFormat.get()?.parse(dateStr)?.time ?: 0L
            } else {
                0L
            }
        }.getOrDefault(0L)

        return FileItem(
            name = name,
            path = fullPath,
            isDirectory = isDir,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            isHidden = name.startsWith("."),
            canRead = perms.getOrNull(1) == 'r',
            canWrite = perms.getOrNull(2) == 'w',
            canExecute = perms.getOrNull(3) in listOf('x', 's', 't'),
            childCount = 0,
            extension = ext,
            category = cat,
            rawPermissions = perms.take(10),
            isRootItem = true,
            symlinkTarget = symlinkTarget
        )
    }

    suspend fun listDirectoryRoot(
        shell: RootShellManager,
        suBinary: String,
        dirPath: String,
        showHidden: Boolean,
        categorizer: (String) -> FileCategory
    ): Result<List<FileItem>> {
        val normalized = if (dirPath == "/") "/" else dirPath.trimEnd('/')
        val probeTarget = if (normalized == "/") "/" else "$normalized/"
        val cmd = "ls -la ${shellQuote(probeTarget)}"
        val res = shell.execute(command = cmd, useRoot = true, suBinary = suBinary)
        if (res.exitCode != 0 && res.stdout.isBlank()) {
            val err = res.stderr.ifBlank { "Root directory listing failed (exit ${res.exitCode})" }
            return Result.failure(IllegalStateException(err))
        }
        val items = res.stdout.lineSequence()
            .mapNotNull { parseLsLine(normalized, it, categorizer) }
            .filter { showHidden || !it.isHidden }
            .toList()
        return Result.success(items)
    }

    suspend fun copyRoot(
        shell: RootShellManager,
        suBinary: String,
        sources: List<File>,
        targetDir: File
    ): Result<Int> {
        val targetQuoted = shellQuote(targetDir.absolutePath)
        val mkdirRes = shell.execute("mkdir -p $targetQuoted", useRoot = true, suBinary = suBinary)
        if (mkdirRes.exitCode != 0) {
            return Result.failure(IllegalStateException(mkdirRes.stderr.ifBlank { "mkdir failed" }))
        }
        var count = 0
        for (src in sources) {
            val cmd = "cp -r ${shellQuote(src.absolutePath)} $targetQuoted/"
            val res = shell.execute(cmd, useRoot = true, suBinary = suBinary)
            if (res.exitCode != 0) {
                return Result.failure(
                    IllegalStateException(res.stderr.ifBlank { "Root copy failed for ${src.name}" })
                )
            }
            count++
        }
        return Result.success(count)
    }

    suspend fun moveRoot(
        shell: RootShellManager,
        suBinary: String,
        sources: List<File>,
        targetDir: File
    ): Result<Int> {
        val targetQuoted = shellQuote(targetDir.absolutePath)
        shell.execute("mkdir -p $targetQuoted", useRoot = true, suBinary = suBinary)
        var count = 0
        for (src in sources) {
            val cmd = "mv ${shellQuote(src.absolutePath)} $targetQuoted/"
            val res = shell.execute(cmd, useRoot = true, suBinary = suBinary)
            if (res.exitCode != 0) {
                return Result.failure(
                    IllegalStateException(res.stderr.ifBlank { "Root move failed for ${src.name}" })
                )
            }
            count++
        }
        return Result.success(count)
    }

    suspend fun renameRoot(
        shell: RootShellManager,
        suBinary: String,
        file: File,
        newName: String
    ): Result<File> {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/')) {
            return Result.failure(IllegalArgumentException("Invalid filename"))
        }
        val dest = File(file.parentFile ?: File("/"), clean)
        val cmd = "mv ${shellQuote(file.absolutePath)} ${shellQuote(dest.absolutePath)}"
        val res = shell.execute(cmd, useRoot = true, suBinary = suBinary)
        return if (res.exitCode == 0) {
            Result.success(dest)
        } else {
            Result.failure(IllegalStateException(res.stderr.ifBlank { "Root rename failed" }))
        }
    }

    suspend fun deleteRoot(
        shell: RootShellManager,
        suBinary: String,
        files: List<File>
    ): Result<Int> {
        var count = 0
        for (f in files) {
            val abs = f.absolutePath
            if (abs == "/" || abs == "/system" || abs == "/data" || abs == "/vendor") {
                return Result.failure(IllegalArgumentException("Refusing to delete critical root mountpoint: $abs"))
            }
            val cmd = "rm -rf ${shellQuote(abs)}"
            val res = shell.execute(cmd, useRoot = true, suBinary = suBinary)
            if (res.exitCode != 0) {
                return Result.failure(
                    IllegalStateException(res.stderr.ifBlank { "Root delete failed for ${f.name}" })
                )
            }
            count++
        }
        return Result.success(count)
    }

    suspend fun mkdirRoot(
        shell: RootShellManager,
        suBinary: String,
        parent: File,
        name: String
    ): Result<File> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) {
            return Result.failure(IllegalArgumentException("Invalid folder name"))
        }
        val dest = File(parent, clean)
        val res = shell.execute("mkdir -p ${shellQuote(dest.absolutePath)}", useRoot = true, suBinary = suBinary)
        return if (res.exitCode == 0) Result.success(dest)
        else Result.failure(IllegalStateException(res.stderr.ifBlank { "Root mkdir failed" }))
    }

    suspend fun touchRoot(
        shell: RootShellManager,
        suBinary: String,
        parent: File,
        name: String
    ): Result<File> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) {
            return Result.failure(IllegalArgumentException("Invalid file name"))
        }
        val dest = File(parent, clean)
        val res = shell.execute("touch ${shellQuote(dest.absolutePath)}", useRoot = true, suBinary = suBinary)
        return if (res.exitCode == 0) Result.success(dest)
        else Result.failure(IllegalStateException(res.stderr.ifBlank { "Root file creation failed" }))
    }

    suspend fun chmodRoot(
        shell: RootShellManager,
        suBinary: String,
        file: File,
        octalMode: String
    ): Result<Unit> {
        val clean = octalMode.trim()
        if (!clean.matches(Regex("^[0-7]{3,4}$"))) {
            return Result.failure(IllegalArgumentException("Mode must be 3 or 4 octal digits (e.g. 644 or 0755)"))
        }
        val res = shell.execute(
            "chmod $clean ${shellQuote(file.absolutePath)}",
            useRoot = true,
            suBinary = suBinary
        )
        return if (res.exitCode == 0) Result.success(Unit)
        else Result.failure(IllegalStateException(res.stderr.ifBlank { "chmod failed" }))
    }

    suspend fun sha256Root(
        shell: RootShellManager,
        suBinary: String,
        file: File
    ): String {
        val res = shell.execute("sha256sum ${shellQuote(file.absolutePath)}", useRoot = true, suBinary = suBinary)
        return if (res.exitCode == 0) {
            res.stdout.trim().substringBefore(' ').ifBlank { "Unavailable" }
        } else {
            "Root access denied / sha256sum unavailable"
        }
    }

    suspend fun readPreviewRoot(
        shell: RootShellManager,
        suBinary: String,
        file: File
    ): String? {
        val res = shell.execute("head -c 2048 ${shellQuote(file.absolutePath)}", useRoot = true, suBinary = suBinary)
        return if (res.exitCode == 0 && res.stdout.isNotBlank()) res.stdout else null
    }

    suspend fun stageFileToCache(
        shell: RootShellManager,
        suBinary: String,
        remoteFile: File,
        stagingDir: File
    ): Result<File> {
        if (!stagingDir.exists()) stagingDir.mkdirs()
        val staged = File(stagingDir, remoteFile.name)
        val cmd = "cp ${shellQuote(remoteFile.absolutePath)} ${shellQuote(staged.absolutePath)} && chmod 644 ${shellQuote(staged.absolutePath)}"
        val res = shell.execute(cmd, useRoot = true, suBinary = suBinary)
        return if (res.exitCode == 0 && staged.exists() && staged.canRead()) {
            Result.success(staged)
        } else {
            Result.failure(IllegalStateException(res.stderr.ifBlank { "Unable to stage root file" }))
        }
    }
}
