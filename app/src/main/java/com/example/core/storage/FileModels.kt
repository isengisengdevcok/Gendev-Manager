package com.example.core.storage

enum class FileCategory {
    DIRECTORY,
    APK_BUNDLE,
    ARCHIVE,
    XML,
    JSON,
    DEX,
    SMALI,
    CODE,
    IMAGE,
    BINARY,
    TEXT
}

enum class SortBy(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Modified"),
    TYPE("Type")
}

enum class FilterCategory(val label: String) {
    ALL("All"),
    FOLDERS("Folders"),
    APKS("APKs & Bundles"),
    ARCHIVES("Archives"),
    CODE("Code & Config"),
    IMAGES("Images")
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canExecute: Boolean,
    val childCount: Int = 0,
    val extension: String = "",
    val category: FileCategory = FileCategory.TEXT,
    val safUriString: String? = null
) {
    val permissionsString: String
        get() {
            val d = if (isDirectory) "d" else "-"
            val r = if (canRead) "r" else "-"
            val w = if (canWrite) "w" else "-"
            val x = if (canExecute) "x" else "-"
            return "$d$r$w$x------"
        }
}

data class ArchiveEntryItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val crc: Long,
    val method: Int
)

data class DuplicateGroup(
    val sha256: String,
    val sizeBytes: Long,
    val files: List<FileItem>
)

data class StorageCategoryStat(
    val label: String,
    val bytes: Long,
    val fileCount: Int
)

enum class DiffLineType {
    UNCHANGED,
    ADDED,
    REMOVED
}

data class FileDiffLine(
    val leftLineNumber: Int?,
    val rightLineNumber: Int?,
    val content: String,
    val type: DiffLineType
)
