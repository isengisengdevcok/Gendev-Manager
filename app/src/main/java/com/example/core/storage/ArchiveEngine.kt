package com.example.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ArchiveEngine {

    suspend fun listArchiveEntries(archiveFile: File): Result<List<ArchiveEntryItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(archiveFile.exists() && archiveFile.isFile) { "Archive file does not exist" }
                ZipFile(archiveFile).use { zip ->
                    zip.entries().asSequence().map { entry ->
                        ArchiveEntryItem(
                            path = entry.name,
                            name = entry.name.trimEnd('/').substringAfterLast('/'),
                            isDirectory = entry.isDirectory,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            crc = entry.crc,
                            method = entry.method
                        )
                    }.sortedWith(compareBy<ArchiveEntryItem> { !it.isDirectory }.thenBy { it.path }).toList()
                }
            }
        }

    suspend fun readEntryBytes(archiveFile: File, entryPath: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                ZipFile(archiveFile).use { zip ->
                    val entry = zip.getEntry(entryPath) ?: error("Entry not found: $entryPath")
                    zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }

    suspend fun extractEntry(
        archiveFile: File,
        entryPath: String,
        targetDir: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!targetDir.exists()) targetDir.mkdirs()
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath) ?: error("Entry not found: $entryPath")
                val outName = entryPath.trimEnd('/').substringAfterLast('/')
                val outFile = safeResolve(targetDir, outName)
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                outFile
            }
        }
    }

    suspend fun extractSelected(
        archiveFile: File,
        selectedPaths: Set<String>,
        targetDir: File,
        preserveSubdirs: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(selectedPaths.isNotEmpty()) { "Pilih minimal 1 file atau folder di dalam ZIP" }
            if (!targetDir.exists()) targetDir.mkdirs()
            val selectedDirs = selectedPaths.filter { it.endsWith("/") }
            var count = 0
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val matched = entry.name in selectedPaths ||
                        selectedDirs.any { dirPrefix -> entry.name.startsWith(dirPrefix) }
                    if (!matched) continue

                    val relPath = if (preserveSubdirs) {
                        entry.name
                    } else {
                        entry.name.trimEnd('/').substringAfterLast('/')
                    }
                    if (relPath.isBlank()) continue

                    val target = safeResolve(targetDir, relPath)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        count++
                    }
                }
            }
            count
        }
    }

    suspend fun extractAll(archiveFile: File, targetDir: File): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!targetDir.exists()) targetDir.mkdirs()
                var count = 0
                ZipFile(archiveFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val target = safeResolve(targetDir, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            count++
                        }
                    }
                }
                count
            }
        }

    suspend fun compressFiles(sources: List<File>, outputZip: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                outputZip.parentFile?.mkdirs()
                ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                    for (src in sources) {
                        addFileToZip(zos, src, src.name)
                    }
                }
                outputZip
            }
        }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            val dirEntryName = if (entryName.endsWith("/")) entryName else "$entryName/"
            zos.putNextEntry(ZipEntry(dirEntryName))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(zos, child, "$dirEntryName${child.name}")
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    private fun safeResolve(baseDir: File, relativePath: String): File {
        val baseCanonical = baseDir.canonicalPath
        val resolved = File(baseDir, relativePath)
        val resolvedCanonical = resolved.canonicalPath
        if (!resolvedCanonical.startsWith(baseCanonical + File.separator) && resolvedCanonical != baseCanonical) {
            throw SecurityException("Blocked Zip Slip path traversal: $relativePath")
        }
        return resolved
    }
}
