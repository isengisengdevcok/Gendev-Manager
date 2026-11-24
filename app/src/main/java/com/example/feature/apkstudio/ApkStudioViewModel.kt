package com.example.feature.apkstudio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.OfflineEngineeringAnalyzer
import com.example.core.apk.ApkComparisonResult
import com.example.core.apk.ApkInspectionResult
import com.example.core.apk.ApkRebuildResult
import com.example.core.apk.InstalledAppSummary
import com.example.core.common.AppContainer
import com.example.core.common.AppLogStore
import com.example.core.data.RecentFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ApkStudioUiState(
    val activeApk: ApkInspectionResult? = null,
    val installedApps: List<InstalledAppSummary> = emptyList(),
    val showAppPicker: Boolean = false,
    val showComparePicker: Boolean = false,
    val comparisonResult: ApkComparisonResult? = null,
    val rebuildResult: ApkRebuildResult? = null,
    val auditMarkdown: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

class ApkStudioViewModel(private val container: AppContainer) : ViewModel() {

    private val apkEngine = container.apkEngineeringEngine
    private val storageRepo = container.storageRepository

    private val _uiState = MutableStateFlow(ApkStudioUiState())
    val uiState: StateFlow<ApkStudioUiState> = _uiState.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        val pending = container.sharedBus.consumePendingApk()
        if (pending != null) {
            initialized = true
            inspectApkPath(pending)
            return
        }
        if (initialized || _uiState.value.activeApk != null) return
        initialized = true
        viewModelScope.launch {
            storageRepo.ensureWorkspaceInitialized()
            val selfApk = storageRepo.ensureSelfApkSample()
            if (selfApk != null && selfApk.exists() && _uiState.value.activeApk == null) {
                inspectApkPath(selfApk.absolutePath)
            }
        }
    }

    fun consumeBusIfPresent() {
        container.sharedBus.consumePendingApk()?.let {
            initialized = true
            inspectApkPath(it)
        }
    }

    fun inspectApkPath(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    statusMessage = null,
                    comparisonResult = null,
                    rebuildResult = null,
                    auditMarkdown = null
                )
            }
            val file = File(path)
            apkEngine.inspectApk(file)
                .onSuccess { result ->
                    container.database.recentFileDao().upsert(
                        RecentFileEntity(
                            path = file.absolutePath,
                            name = file.name,
                            category = "APK_BUNDLE"
                        )
                    )
                    _uiState.update { it.copy(activeApk = result, isLoading = false) }
                }
                .onFailure { err ->
                    AppLogStore.e("ApkStudio", "Inspect APK failed (${file.name}): ${err.localizedMessage}", err)
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = "Failed to inspect APK: ${err.localizedMessage}")
                    }
                }
        }
    }

    fun importAndInspectUri(uri: Uri, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val imported = storageRepo.importUriToDir(uri, File(storageRepo.workspaceDir, "Imported_APKs"), name)
            imported
                .onSuccess { inspectApkPath(it.absolutePath) }
                .onFailure {
                    AppLogStore.e("ApkStudio", "Import APK URI failed ($name): ${it.localizedMessage}", it)
                    _uiState.update { s -> s.copy(isLoading = false, statusMessage = "Import failed: ${it.localizedMessage}") }
                }
        }
    }

    fun openInstalledAppsPicker(forCompare: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = apkEngine.loadInstalledApps(includeSystem = false)
            _uiState.update {
                it.copy(
                    installedApps = apps,
                    isLoading = false,
                    showAppPicker = !forCompare,
                    showComparePicker = forCompare
                )
            }
        }
    }

    fun dismissPickers() {
        _uiState.update { it.copy(showAppPicker = false, showComparePicker = false) }
    }

    fun compareWithApp(otherApp: InstalledAppSummary) {
        val left = _uiState.value.activeApk ?: return
        _uiState.update { it.copy(showComparePicker = false, isLoading = true) }
        viewModelScope.launch {
            apkEngine.inspectApk(File(otherApp.sourceApkPath))
                .onSuccess { right ->
                    val cmp = apkEngine.compareApks(left, right)
                    _uiState.update { it.copy(comparisonResult = cmp, isLoading = false) }
                }
                .onFailure {
                    _uiState.update { s -> s.copy(isLoading = false, statusMessage = "Comparison failed: ${it.localizedMessage}") }
                }
        }
    }

    fun runStaticAudit() {
        val apk = _uiState.value.activeApk ?: return
        val report = OfflineEngineeringAnalyzer.analyzeApkInspection(apk)
        _uiState.update { it.copy(auditMarkdown = report) }
    }

    fun exportDecodedManifestToWorkspace(): File? {
        val apk = _uiState.value.activeApk ?: return null
        val xml = apk.manifest?.decodedXml ?: return null
        val outDir = File(storageRepo.workspaceDir, "Decoded_Manifests").apply { mkdirs() }
        val outFile = File(outDir, "${apk.fileName.substringBeforeLast('.')}_Manifest.xml")
        outFile.writeText(xml)
        _uiState.update { it.copy(statusMessage = "Exported decoded XML to ${outFile.name}") }
        return outFile
    }

    fun extractEntryToWorkspace(entryPath: String) {
        val apk = _uiState.value.activeApk ?: return
        viewModelScope.launch {
            val outDir = File(storageRepo.workspaceDir, "Extracted_${apk.fileName.substringBeforeLast('.')}")
            container.archiveEngine.extractEntry(File(apk.filePath), entryPath, outDir)
                .onSuccess {
                    _uiState.update { s -> s.copy(statusMessage = "Extracted ${it.name} to ${outDir.name}/") }
                }
                .onFailure {
                    _uiState.update { s -> s.copy(statusMessage = "Extract error: ${it.localizedMessage}") }
                }
        }
    }

    fun rebuildAndSignActiveApk(customNoteText: String) {
        val apk = _uiState.value.activeApk ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            container.sharedBus.updateBuildStatus("Rebuilding & Signing...")
            val src = File(apk.filePath)
            val outApk = File(storageRepo.rebuildsDir, "${src.nameWithoutExtension}_signed.apk")
            val replacements = if (customNoteText.isNotBlank()) {
                mapOf("assets/gendev_build_meta.txt" to customNoteText.toByteArray())
            } else emptyMap()

            apkEngine.rebuildAndSignApk(src, outApk, replacements = replacements)
                .onSuccess { res ->
                    container.sharedBus.updateBuildStatus("Signed ✓ ${res.outputApk.name}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rebuildResult = res,
                            statusMessage = "Rebuilt, 4-byte ZipAligned & RSA-2048 signed -> ${res.outputApk.name}"
                        )
                    }
                }
                .onFailure { err ->
                    AppLogStore.e("ApkStudio", "Rebuild/sign APK failed (${src.name}): ${err.localizedMessage}", err)
                    container.sharedBus.updateBuildStatus("Build Failed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Rebuild failed validation: ${err.localizedMessage}"
                        )
                    }
                }
        }
    }

    fun backupActiveApk() {
        val apk = _uiState.value.activeApk ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val src = File(apk.filePath)
                    val dest = File(storageRepo.backupsDir, "backup_${System.currentTimeMillis()}_${src.name}")
                    src.copyTo(dest, overwrite = true)
                    dest
                }
            }
            res.onSuccess {
                _uiState.update { s -> s.copy(statusMessage = "Backed up APK to Backups/${it.name}") }
            }.onFailure {
                _uiState.update { s -> s.copy(statusMessage = "Backup error: ${it.localizedMessage}") }
            }
        }
    }

    fun installApk(file: File) {
        apkEngine.launchSystemApkInstaller(file)
            .onFailure { err ->
                _uiState.update { it.copy(statusMessage = "Installer launch error: ${err.localizedMessage}") }
            }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
