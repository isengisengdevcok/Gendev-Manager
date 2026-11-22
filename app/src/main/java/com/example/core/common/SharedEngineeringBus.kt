package com.example.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class AttachedAiContext(
    val label: String,
    val content: String
)

/**
 * Coordinates cross-module engineering handoffs (Files -> APK Studio, APK -> DEX Studio,
 * Files -> Resource Editor, Any -> GENDEV AI, Files -> Terminal).
 */
class SharedEngineeringBus {
    private val _pendingApkPath = MutableStateFlow<String?>(null)
    val pendingApkPath = _pendingApkPath.asStateFlow()

    private val _pendingEditorFile = MutableStateFlow<String?>(null)
    val pendingEditorFile = _pendingEditorFile.asStateFlow()

    private val _pendingDexApkPath = MutableStateFlow<String?>(null)
    val pendingDexApkPath = _pendingDexApkPath.asStateFlow()

    private val _attachedAiContext = MutableStateFlow<AttachedAiContext?>(null)
    val attachedAiContext = _attachedAiContext.asStateFlow()

    private val _terminalWorkingDir = MutableStateFlow<String?>(null)
    val terminalWorkingDir = _terminalWorkingDir.asStateFlow()

    private val _lastBuildStatus = MutableStateFlow("Ready • Idle")
    val lastBuildStatus = _lastBuildStatus.asStateFlow()

    fun openApkInStudio(path: String) {
        _pendingApkPath.value = path
    }

    fun consumePendingApk(): String? {
        val v = _pendingApkPath.value
        _pendingApkPath.value = null
        return v
    }

    fun openFileInEditor(path: String) {
        _pendingEditorFile.value = path
    }

    fun consumePendingEditorFile(): String? {
        val v = _pendingEditorFile.value
        _pendingEditorFile.value = null
        return v
    }

    fun openApkInDexStudio(path: String) {
        _pendingDexApkPath.value = path
    }

    fun consumePendingDexApk(): String? {
        val v = _pendingDexApkPath.value
        _pendingDexApkPath.value = null
        return v
    }

    fun attachContextToAi(label: String, content: String) {
        _attachedAiContext.value = AttachedAiContext(label, content)
    }

    fun clearAiContext() {
        _attachedAiContext.value = null
    }

    fun setTerminalDir(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            _terminalWorkingDir.value = dir.absolutePath
        }
    }

    fun updateBuildStatus(status: String) {
        _lastBuildStatus.value = status
    }
}
