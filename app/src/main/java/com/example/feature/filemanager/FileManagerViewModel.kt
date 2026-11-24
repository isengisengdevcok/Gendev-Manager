package com.example.feature.filemanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppContainer
import com.example.core.common.AppLogStore
import com.example.core.common.HashUtils
import com.example.core.data.BookmarkEntity
import com.example.core.data.FileDisplaySize
import com.example.core.data.RecentFileEntity
import com.example.core.storage.ArchiveEntryItem
import com.example.core.storage.DuplicateGroup
import com.example.core.storage.FileDiffLine
import com.example.core.storage.FileItem
import com.example.core.storage.FilterCategory
import com.example.core.storage.RootFileBridge
import com.example.core.storage.SortBy
import com.example.core.storage.StorageCategoryStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PaneTabState(
    val id: String,
    val title: String,
    val currentPath: String,
    val items: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val recursiveSearch: Boolean = false,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val filterCategory: FilterCategory = FilterCategory.ALL,
    val backStack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val storageSummary: String = ""
) {
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()
    val folderCount: Int get() = items.count { it.isDirectory }
    val fileCount: Int get() = items.count { !it.isDirectory }
}

data class PaneState(
    val paneIndex: Int,
    val tabs: List<PaneTabState>,
    val activeTabIndex: Int = 0
) {
    val activeTab: PaneTabState
        get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

data class FilePropertiesModalData(
    val item: FileItem,
    val sha256: String,
    val md5: String,
    val previewText: String? = null
)

data class FileManagerUiState(
    val leftPane: PaneState,
    val rightPane: PaneState,
    val activePaneIndex: Int = 0,
    val dualPaneMode: Boolean = true,
    val showHidden: Boolean = false,
    val fileDisplaySize: FileDisplaySize = FileDisplaySize.STANDARD,
    val rootAvailable: Boolean = false,
    val suBinaryPath: String? = null,
    val rootImplLabel: String = "Non-Root",
    val rootExplorerEnabled: Boolean = false,
    val rootVerifiedUid0: Boolean = false,
    val rootRememberedGranted: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val statusMessage: String? = null,
    val propertiesModal: FilePropertiesModalData? = null,
    val archiveInspectFile: File? = null,
    val archiveEntries: List<ArchiveEntryItem> = emptyList(),
    val duplicatesResult: List<DuplicateGroup>? = null,
    val storageStatsResult: List<StorageCategoryStat>? = null,
    val diffTitle: String? = null,
    val diffLines: List<FileDiffLine>? = null,
    val isBusy: Boolean = false
)

class FileManagerViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.storageRepository
    private val archiveEngine = container.archiveEngine
    private val envManager = container.deviceEnvironmentManager
    private val rootShellManager = container.rootShellManager

    private val initialWorkspace = repo.workspaceDir.absolutePath
    private val initialSamples = File(repo.workspaceDir, "Samples").apply { mkdirs() }.absolutePath
    private val initialPhoneDir = repo.getPhoneStorageDirectory()?.takeIf { it.exists() }

    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            leftPane = PaneState(
                paneIndex = 0,
                tabs = listOf(
                    PaneTabState(
                        id = "l1",
                        title = if (initialPhoneDir != null) "Phone" else "Workspace",
                        currentPath = initialPhoneDir?.absolutePath ?: initialWorkspace
                    )
                )
            ),
            rightPane = PaneState(
                paneIndex = 1,
                tabs = listOf(
                    PaneTabState(
                        id = "r1",
                        title = "/",
                        currentPath = "/"
                    )
                )
            )
        )
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = container.database.bookmarkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Volatile
    private var isScreenActivated = false

    @Volatile
    private var rootAutoAttempted = false

    init {
        viewModelScope.launch {
            container.settingsDataStore.settingsFlow.collect { s ->
                val showHiddenChanged = _uiState.value.showHidden != s.showHiddenFiles
                _uiState.update {
                    it.copy(
                        dualPaneMode = s.dualPaneEnabled,
                        showHidden = s.showHiddenFiles,
                        fileDisplaySize = s.fileDisplaySize,
                        rootRememberedGranted = s.rootRememberedGranted
                    )
                }
                if (isScreenActivated && showHiddenChanged) {
                    refreshPane(0)
                    refreshPane(1)
                }
            }
        }
    }

    fun onScreenActive() {
        val firstActivation = !isScreenActivated
        isScreenActivated = true
        viewModelScope.launch {
            if (firstActivation) {
                repo.ensureWorkspaceInitialized()
            }
            refreshEnvironmentState()
        }
    }

    fun openDirectory(paneIndex: Int, path: String) {
        isScreenActivated = true
        setActivePane(paneIndex)
        navigateTo(paneIndex, path)
    }

    fun refreshEnvironmentState() {
        viewModelScope.launch {
            val env = withContext(Dispatchers.IO) { envManager.inspectEnvironment() }
            val sessionAlreadyVerified = rootShellManager.isRootSessionVerified || rootShellManager.isPersistentRootAlive()
            _uiState.update { state ->
                val verified = state.rootVerifiedUid0 || sessionAlreadyVerified
                val effectiveRootAvailable = env.rootAvailable || verified
                val shouldEnableRoot = if (effectiveRootAvailable && (verified || state.rootExplorerEnabled)) {
                    true
                } else {
                    false
                }
                state.copy(
                    rootAvailable = effectiveRootAvailable,
                    suBinaryPath = env.suBinaryPath ?: state.suBinaryPath,
                    rootImplLabel = if (env.rootAvailable) env.rootImplementation.displayName else state.rootImplLabel,
                    hasAllFilesAccess = env.allFilesAccessGranted,
                    rootExplorerEnabled = shouldEnableRoot,
                    rootVerifiedUid0 = verified
                )
            }
            val cur = _uiState.value
            if (!rootAutoAttempted && cur.rootRememberedGranted && cur.rootAvailable && !cur.rootVerifiedUid0) {
                rootAutoAttempted = true
                authorizeAndEnableRoot(pointRightPaneToSlash = false, silent = true)
            } else {
                refreshPane(0)
                refreshPane(1)
            }
        }
    }

    fun authorizeAndEnableRoot(pointRightPaneToSlash: Boolean = true, silent: Boolean = false) {
        val state = _uiState.value
        if (state.rootVerifiedUid0 && rootShellManager.isPersistentRootAlive()) {
            _uiState.update { it.copy(rootExplorerEnabled = true) }
            if (pointRightPaneToSlash) {
                navigateTo(1, "/")
            } else {
                refreshPane(1)
            }
            return
        }
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isBusy = true) }
            val suCmd = state.suBinaryPath ?: "su"
            val result = rootShellManager.verifyRootAccess(suCmd)
            val isUid0 = result.exitCode == 0 && result.stdout.contains("uid=0")
            if (isUid0) {
                container.settingsDataStore.setRootRememberedGranted(true)
                val resolvedLabel = if (state.rootImplLabel == "Non-Root (Standard User)" || state.rootImplLabel == "Non-Root") {
                    "Root (su • uid=0)"
                } else {
                    state.rootImplLabel
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        rootAvailable = true,
                        suBinaryPath = suCmd,
                        rootImplLabel = resolvedLabel,
                        rootVerifiedUid0 = true,
                        rootExplorerEnabled = true,
                        rootRememberedGranted = true,
                        statusMessage = if (silent) it.statusMessage else "Root aktif ($resolvedLabel • uid=0). Sesi su tunggal aktif."
                    )
                }
                if (pointRightPaneToSlash) {
                    val rightPath = _uiState.value.rightPane.activeTab.currentPath
                    if (rightPath == initialWorkspace || rightPath == initialSamples) {
                        navigateTo(1, "/")
                    } else {
                        refreshPane(1)
                    }
                    refreshPane(0)
                } else {
                    refreshPane(0)
                    refreshPane(1)
                }
            } else {
                if (silent) {
                    container.settingsDataStore.setRootRememberedGranted(false)
                }
                val err = result.stderr.ifBlank { result.stdout }.ifBlank { "Exit code ${result.exitCode}" }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        rootVerifiedUid0 = false,
                        rootExplorerEnabled = false,
                        statusMessage = if (silent) it.statusMessage else "Izin Root (su) ditolak atau tidak tersedia: $err"
                    )
                }
                if (silent) {
                    refreshPane(0)
                    refreshPane(1)
                }
            }
        }
    }

    fun setRootExplorerEnabled(enabled: Boolean) {
        if (enabled) {
            authorizeAndEnableRoot(pointRightPaneToSlash = false)
            return
        }
        rootShellManager.closePersistentRootSession()
        viewModelScope.launch { container.settingsDataStore.setRootRememberedGranted(false) }
        _uiState.update { it.copy(rootExplorerEnabled = false, rootVerifiedUid0 = false, rootRememberedGranted = false) }
        setStatus("Root Explorer dimatikan.")
        refreshPane(0)
        refreshPane(1)
    }

    fun setActivePane(index: Int) {
        _uiState.update { it.copy(activePaneIndex = index) }
    }

    fun toggleDualPane() {
        val next = !_uiState.value.dualPaneMode
        _uiState.update { it.copy(dualPaneMode = next) }
        viewModelScope.launch { container.settingsDataStore.setDualPane(next) }
    }

    fun toggleShowHidden() {
        val next = !_uiState.value.showHidden
        _uiState.update { it.copy(showHidden = next) }
        viewModelScope.launch {
            container.settingsDataStore.setShowHidden(next)
            refreshPane(0)
            refreshPane(1)
        }
    }

    fun updateFileDisplaySize(size: FileDisplaySize) {
        _uiState.update { it.copy(fileDisplaySize = size) }
        viewModelScope.launch { container.settingsDataStore.setFileDisplaySize(size) }
    }

    fun addTab(paneIndex: Int, path: String = repo.workspaceDir.absolutePath) {
        val dir = File(path)
        val tabId = "tab_${System.currentTimeMillis()}"
        val newTab = PaneTabState(id = tabId, title = dir.name.ifEmpty { "/" }, currentPath = dir.absolutePath)
        _uiState.update { state ->
            val pane = if (paneIndex == 0) state.leftPane else state.rightPane
            val updated = pane.copy(tabs = pane.tabs + newTab, activeTabIndex = pane.tabs.size)
            if (paneIndex == 0) state.copy(leftPane = updated) else state.copy(rightPane = updated)
        }
        refreshPane(paneIndex)
    }

    fun selectTab(paneIndex: Int, tabIndex: Int) {
        _uiState.update { state ->
            val pane = if (paneIndex == 0) state.leftPane else state.rightPane
            val updated = pane.copy(activeTabIndex = tabIndex.coerceIn(0, pane.tabs.lastIndex))
            if (paneIndex == 0) state.copy(leftPane = updated, activePaneIndex = 0)
            else state.copy(rightPane = updated, activePaneIndex = 1)
        }
        refreshPane(paneIndex)
    }

    fun closeTab(paneIndex: Int, tabIndex: Int) {
        _uiState.update { state ->
            val pane = if (paneIndex == 0) state.leftPane else state.rightPane
            if (pane.tabs.size <= 1) return@update state
            val nextTabs = pane.tabs.filterIndexed { i, _ -> i != tabIndex }
            val nextActive = pane.activeTabIndex.coerceAtMost(nextTabs.lastIndex)
            val updated = pane.copy(tabs = nextTabs, activeTabIndex = nextActive)
            if (paneIndex == 0) state.copy(leftPane = updated) else state.copy(rightPane = updated)
        }
        refreshPane(paneIndex)
    }

    fun navigateTo(paneIndex: Int, path: String) {
        val clean = if (path == "/") "/" else path.trimEnd('/')
        updateActiveTab(paneIndex) { tab ->
            val f = File(clean)
            val nextBack = if (tab.currentPath != clean) (tab.backStack + tab.currentPath).takeLast(30) else tab.backStack
            tab.copy(
                currentPath = clean,
                title = if (clean == "/") "/" else f.name.ifEmpty { "/" },
                selectedPaths = emptySet(),
                recursiveSearch = false,
                backStack = nextBack,
                forwardStack = if (tab.currentPath != clean) emptyList() else tab.forwardStack
            )
        }
        refreshPane(paneIndex)
    }

    fun goBack(paneIndex: Int) {
        val tab = getPane(paneIndex).activeTab
        val prev = tab.backStack.lastOrNull() ?: return
        updateActiveTab(paneIndex) { t ->
            val f = File(prev)
            t.copy(
                currentPath = prev,
                title = if (prev == "/") "/" else f.name.ifEmpty { "/" },
                selectedPaths = emptySet(),
                backStack = t.backStack.dropLast(1),
                forwardStack = (t.forwardStack + t.currentPath).takeLast(30)
            )
        }
        refreshPane(paneIndex)
    }

    fun goForward(paneIndex: Int) {
        val tab = getPane(paneIndex).activeTab
        val next = tab.forwardStack.lastOrNull() ?: return
        updateActiveTab(paneIndex) { t ->
            val f = File(next)
            t.copy(
                currentPath = next,
                title = if (next == "/") "/" else f.name.ifEmpty { "/" },
                selectedPaths = emptySet(),
                backStack = (t.backStack + t.currentPath).takeLast(30),
                forwardStack = t.forwardStack.dropLast(1)
            )
        }
        refreshPane(paneIndex)
    }

    fun navigateToPath(paneIndex: Int, rawPath: String) {
        val cleaned = rawPath.trim()
        if (cleaned.isEmpty()) {
            setStatus("Masukkan path direktori yang valid.")
            return
        }
        val normalized = if (cleaned == "/") "/" else cleaned.trimEnd('/')
        val state = _uiState.value
        val dir = File(normalized)
        if ((dir.exists() && dir.isDirectory && dir.canRead()) || (state.rootExplorerEnabled && state.suBinaryPath != null)) {
            navigateTo(paneIndex, normalized)
        } else if (state.rootAvailable) {
            setStatus("Path ini membutuhkan Root (su). Aktifkan Root Explorer terlebih dahulu.")
        } else {
            setStatus("Path tidak dapat dibaca tanpa Root atau izin All Files Access.")
        }
    }

    fun navigateUp(paneIndex: Int) {
        val cur = getPane(paneIndex).activeTab.currentPath
        if (cur == "/") {
            setStatus("Sudah berada di root (/)")
            return
        }
        val parent = File(cur).parentFile
        val state = _uiState.value
        if (parent != null && (parent.canRead() || (state.rootExplorerEnabled && state.suBinaryPath != null))) {
            navigateTo(paneIndex, parent.absolutePath)
        } else if (state.rootAvailable) {
            setStatus("Direktori induk membutuhkan Root (su). Ketuk 'Aktifkan Root (su)'.")
        } else {
            setStatus("Direktori induk dibatasi pada perangkat Non-Root.")
        }
    }

    fun updateSearch(paneIndex: Int, query: String, recursive: Boolean) {
        updateActiveTab(paneIndex) { it.copy(searchQuery = query, recursiveSearch = recursive) }
        refreshPane(paneIndex)
    }

    fun updateSort(paneIndex: Int, sortBy: SortBy) {
        updateActiveTab(paneIndex) { tab ->
            val nextAsc = if (tab.sortBy == sortBy) !tab.ascending else true
            tab.copy(sortBy = sortBy, ascending = nextAsc)
        }
        refreshPane(paneIndex)
    }

    fun updateFilter(paneIndex: Int, filter: FilterCategory) {
        updateActiveTab(paneIndex) { it.copy(filterCategory = filter) }
        refreshPane(paneIndex)
    }

    fun toggleSelection(paneIndex: Int, path: String) {
        updateActiveTab(paneIndex) { tab ->
            val next = if (path in tab.selectedPaths) tab.selectedPaths - path else tab.selectedPaths + path
            tab.copy(selectedPaths = next)
        }
    }

    fun clearSelection(paneIndex: Int) {
        updateActiveTab(paneIndex) { it.copy(selectedPaths = emptySet()) }
    }

    fun selectAll(paneIndex: Int) {
        updateActiveTab(paneIndex) { tab ->
            tab.copy(selectedPaths = tab.items.map { it.path }.toSet())
        }
    }

    fun refreshPane(paneIndex: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val tab = (if (paneIndex == 0) state.leftPane else state.rightPane).activeTab
            val dir = File(tab.currentPath)
            val items = if (tab.recursiveSearch && tab.searchQuery.isNotBlank()) {
                repo.searchRecursive(dir, tab.searchQuery, state.showHidden)
            } else {
                repo.listDirectory(
                    dir = dir,
                    showHidden = state.showHidden,
                    sortBy = tab.sortBy,
                    ascending = tab.ascending,
                    filterCategory = tab.filterCategory,
                    query = tab.searchQuery,
                    useRoot = state.rootExplorerEnabled,
                    suBinary = state.suBinaryPath
                )
            }
            val stats = withContext(Dispatchers.IO) { envManager.readStorageStats(dir) }
            val summary = if (stats.totalBytes > 0L) {
                "${HashUtils.formatBytes(stats.freeBytes)} bebas / ${HashUtils.formatBytes(stats.totalBytes)}"
            } else ""
            updateActiveTab(paneIndex) { it.copy(items = items, storageSummary = summary) }
        }
    }

    fun copySelectedToOppositePane(sourcePaneIndex: Int) {
        val targetPaneIndex = if (sourcePaneIndex == 0) 1 else 0
        val selected = getPane(sourcePaneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) {
            setStatus("Pilih minimal 1 file/folder terlebih dahulu (tekan lama baris item).")
            return
        }
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.copyFiles(selected, destDir, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { count ->
                setStatus("Disalin $count item ke ${destDir.name.ifEmpty { "/" }}")
                clearSelection(sourcePaneIndex)
                refreshPane(targetPaneIndex)
            }.onFailure { setStatus("Gagal menyalin: ${it.localizedMessage}") }
        }
    }

    fun copySingleToOppositePane(sourcePaneIndex: Int, item: FileItem) {
        val targetPaneIndex = if (sourcePaneIndex == 0) 1 else 0
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.copyFiles(listOf(File(item.path)), destDir, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess {
                setStatus("Disalin ${item.name} -> ${destDir.name.ifEmpty { "/" }}")
                refreshPane(targetPaneIndex)
            }.onFailure { setStatus("Gagal menyalin: ${it.localizedMessage}") }
        }
    }

    fun moveSelectedToOppositePane(sourcePaneIndex: Int) {
        val targetPaneIndex = if (sourcePaneIndex == 0) 1 else 0
        val selected = getPane(sourcePaneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) {
            setStatus("Pilih minimal 1 file/folder terlebih dahulu.")
            return
        }
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.moveFiles(selected, destDir, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { count ->
                setStatus("Dipindah $count item ke ${destDir.name.ifEmpty { "/" }}")
                clearSelection(sourcePaneIndex)
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Gagal memindah: ${it.localizedMessage}") }
        }
    }

    fun moveSingleToOppositePane(sourcePaneIndex: Int, item: FileItem) {
        val targetPaneIndex = if (sourcePaneIndex == 0) 1 else 0
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.moveFiles(listOf(File(item.path)), destDir, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess {
                setStatus("Dipindah ${item.name} -> ${destDir.name.ifEmpty { "/" }}")
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Gagal memindah: ${it.localizedMessage}") }
        }
    }

    fun deleteSelected(paneIndex: Int) {
        val selected = getPane(paneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) return
        val state = _uiState.value
        viewModelScope.launch {
            val res = repo.deleteFiles(
                files = selected,
                createBackup = true,
                useRoot = state.rootExplorerEnabled,
                suBinary = state.suBinaryPath
            )
            res.onSuccess { count ->
                setStatus("Dihapus $count item")
                clearSelection(paneIndex)
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Gagal menghapus: ${it.localizedMessage}") }
        }
    }

    fun deleteSingle(paneIndex: Int, item: FileItem) {
        val state = _uiState.value
        viewModelScope.launch {
            val res = repo.deleteFiles(
                files = listOf(File(item.path)),
                createBackup = true,
                useRoot = state.rootExplorerEnabled,
                suBinary = state.suBinaryPath
            )
            res.onSuccess {
                setStatus("Dihapus ${item.name}")
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Gagal menghapus: ${it.localizedMessage}") }
        }
    }

    fun createFolder(paneIndex: Int, name: String) {
        val parent = File(getPane(paneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            repo.createFolder(parent, name, state.rootExplorerEnabled, state.suBinaryPath)
                .onSuccess {
                    setStatus("Folder dibuat: ${it.name}")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Gagal membuat folder: ${it.localizedMessage}") }
        }
    }

    fun createFile(paneIndex: Int, name: String) {
        val parent = File(getPane(paneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            repo.createFile(parent, name, useRoot = state.rootExplorerEnabled, suBinary = state.suBinaryPath)
                .onSuccess {
                    setStatus("File dibuat: ${it.name}")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Gagal membuat file: ${it.localizedMessage}") }
        }
    }

    fun renameItem(paneIndex: Int, item: FileItem, newName: String) {
        val state = _uiState.value
        viewModelScope.launch {
            repo.renameFile(File(item.path), newName, state.rootExplorerEnabled, state.suBinaryPath)
                .onSuccess {
                    setStatus("Diubah nama menjadi ${it.name}")
                    refreshPane(0)
                    refreshPane(1)
                }
                .onFailure { setStatus("Gagal ubah nama: ${it.localizedMessage}") }
        }
    }

    fun chmodItem(paneIndex: Int, item: FileItem, octalMode: String) {
        val su = _uiState.value.suBinaryPath
        if (!_uiState.value.rootExplorerEnabled || su == null) {
            setStatus("chmod membutuhkan Root Explorer (su) aktif.")
            return
        }
        viewModelScope.launch {
            repo.chmodRoot(File(item.path), octalMode, su)
                .onSuccess {
                    setStatus("Permission ${item.name} -> $octalMode")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("chmod gagal: ${it.localizedMessage}") }
        }
    }

    fun stageFileIfNeeded(file: File, onReady: (File) -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.stageReadableCopyIfNeeded(file, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess(onReady)
                .onFailure { setStatus(it.localizedMessage ?: "Tidak bisa membuka file terproteksi") }
        }
    }

    fun compressSelected(paneIndex: Int, zipName: String) {
        val selected = getPane(paneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) {
            setStatus("Pilih file untuk dikompres.")
            return
        }
        val cleanName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
        val outZip = File(getPane(paneIndex).activeTab.currentPath, cleanName)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            archiveEngine.compressFiles(selected, outZip)
                .onSuccess {
                    setStatus("Dikompres ${selected.size} item -> ${it.name}")
                    clearSelection(paneIndex)
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Kompresi gagal: ${it.localizedMessage}") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun openArchiveInspector(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            archiveEngine.listArchiveEntries(file)
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(archiveInspectFile = file, archiveEntries = entries, isBusy = false)
                    }
                }
                .onFailure {
                    AppLogStore.e("FileManager", "Archive inspect failed (${file.name}): ${it.localizedMessage}", it)
                    _uiState.update { state -> state.copy(isBusy = false) }
                    setStatus("Error arsip: ${it.localizedMessage}")
                }
        }
    }

    fun closeArchiveInspector() {
        _uiState.update { it.copy(archiveInspectFile = null, archiveEntries = emptyList()) }
    }

    fun openArchiveEntry(archive: File, entryPath: String, onReady: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val previewDir = File(repo.workspaceDir, "Archive_Preview/${archive.nameWithoutExtension}")
            val res = archiveEngine.extractEntry(archive, entryPath, previewDir)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { extracted ->
                closeArchiveInspector()
                onReady(extracted)
            }.onFailure {
                setStatus("Gagal membuka entri arsip: ${it.localizedMessage}")
            }
        }
    }

    fun extractArchiveEntries(
        selectedPaths: Set<String>,
        targetDirPath: String,
        extractAll: Boolean,
        preserveSubdirs: Boolean = true
    ) {
        val archive = _uiState.value.archiveInspectFile ?: return
        val cleanTarget = targetDirPath.trim().ifEmpty { getPane(_uiState.value.activePaneIndex).activeTab.currentPath }
        val destDir = File(cleanTarget)
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val result: Result<Int> = withContext(Dispatchers.IO) {
                val normalWritable = (destDir.exists() && destDir.canWrite()) ||
                    (!destDir.exists() && canCreateDirectory(destDir))

                if (normalWritable) {
                    if (extractAll) {
                        archiveEngine.extractAll(archive, destDir)
                    } else {
                        archiveEngine.extractSelected(archive, selectedPaths, destDir, preserveSubdirs)
                    }
                } else if (state.rootExplorerEnabled && !state.suBinaryPath.isNullOrBlank()) {
                    val staging = File(repo.workspaceDir, ".zip_stage_${System.currentTimeMillis()}").apply { mkdirs() }
                    try {
                        val extractRes = if (extractAll) {
                            archiveEngine.extractAll(archive, staging)
                        } else {
                            archiveEngine.extractSelected(archive, selectedPaths, staging, preserveSubdirs)
                        }
                        extractRes.fold(
                            onSuccess = { extractedCount ->
                                val stagedChildren = staging.listFiles()?.toList().orEmpty()
                                if (stagedChildren.isEmpty()) {
                                    Result.success(0)
                                } else {
                                    RootFileBridge.copyRoot(rootShellManager, state.suBinaryPath, stagedChildren, destDir)
                                        .map { extractedCount }
                                }
                            },
                            onFailure = { Result.failure(it) }
                        )
                    } finally {
                        staging.deleteRecursively()
                    }
                } else {
                    Result.failure(
                        IllegalAccessException("Folder tujuan '${destDir.absolutePath}' tidak dapat ditulis (aktifkan Root jika folder sistem).")
                    )
                }
            }

            result.onSuccess { count ->
                closeArchiveInspector()
                setStatus("Diekstrak $count file ke ${destDir.absolutePath}")
                refreshPane(0)
                refreshPane(1)
            }.onFailure { err ->
                AppLogStore.e("FileManager", "Archive extract failed (${archive.name}): ${err.localizedMessage}", err)
                _uiState.update { it.copy(isBusy = false) }
                setStatus("Ekstraksi gagal: ${err.localizedMessage}")
            }
        }
    }

    private fun canCreateDirectory(dir: File): Boolean {
        var cur: File? = dir
        while (cur != null && !cur.exists()) {
            cur = cur.parentFile
        }
        return cur != null && cur.canWrite()
    }

    fun inspectProperties(item: FileItem) {
        val state = _uiState.value
        viewModelScope.launch {
            val file = File(item.path)
            val (sha, md5, preview) = withContext(Dispatchers.IO) {
                if (file.canRead()) {
                    val s = if (file.isFile) HashUtils.calculateHash(file, "SHA-256") else "Directory"
                    val m = if (file.isFile) HashUtils.calculateHash(file, "MD5") else "Directory"
                    val p = if (file.isFile && file.length() in 1..65_536) {
                        runCatching { file.readText().take(2000) }.getOrNull()
                    } else null
                    Triple(s, m, p)
                } else if (state.rootExplorerEnabled && state.suBinaryPath != null && !item.isDirectory) {
                    val s = repo.calculateRootSha256(file, state.suBinaryPath)
                    val p = repo.readRootPreview(file, state.suBinaryPath)
                    Triple(s, "Root-protected file", p)
                } else {
                    Triple("Protected", "Protected", null)
                }
            }
            _uiState.update {
                it.copy(propertiesModal = FilePropertiesModalData(item, sha, md5, preview))
            }
        }
    }

    fun dismissProperties() {
        _uiState.update { it.copy(propertiesModal = null) }
    }

    fun addBookmark(paneIndex: Int) {
        val tab = getPane(paneIndex).activeTab
        viewModelScope.launch {
            container.database.bookmarkDao().insert(
                BookmarkEntity(path = tab.currentPath, label = tab.title)
            )
            setStatus("Bookmark disimpan: ${tab.title}")
        }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch {
            container.database.bookmarkDao().deleteByPath(path)
        }
    }

    fun recordRecentFile(item: FileItem) {
        viewModelScope.launch {
            container.database.recentFileDao().upsert(
                RecentFileEntity(
                    path = item.path,
                    name = item.name,
                    category = item.category.name
                )
            )
        }
    }

    fun runDuplicateScan(paneIndex: Int) {
        val root = File(getPane(paneIndex).activeTab.currentPath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val groups = repo.findDuplicates(root)
            _uiState.update { it.copy(isBusy = false, duplicatesResult = groups) }
        }
    }

    fun dismissDuplicates() {
        _uiState.update { it.copy(duplicatesResult = null) }
    }

    fun runStorageAnalyzer(paneIndex: Int) {
        val root = File(getPane(paneIndex).activeTab.currentPath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val stats = repo.analyzeStorage(root)
            _uiState.update { it.copy(isBusy = false, storageStatsResult = stats) }
        }
    }

    fun dismissStorageAnalyzer() {
        _uiState.update { it.copy(storageStatsResult = null) }
    }

    fun compareSelectedBetweenPanes() {
        val leftSel = _uiState.value.leftPane.activeTab.selectedPaths.firstOrNull()?.let { File(it) }
        val rightSel = _uiState.value.rightPane.activeTab.selectedPaths.firstOrNull()?.let { File(it) }
        if (leftSel == null || rightSel == null || !leftSel.isFile || !rightSel.isFile) {
            setStatus("Pilih 1 file di Panel Kiri dan 1 file di Panel Kanan untuk membandingkan.")
            return
        }
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) { repo.diffFiles(leftSel, rightSel) }
            _uiState.update {
                it.copy(
                    diffTitle = "${leftSel.name}  ↔  ${rightSel.name}",
                    diffLines = lines
                )
            }
        }
    }

    fun dismissDiff() {
        _uiState.update { it.copy(diffTitle = null, diffLines = null) }
    }

    fun importSafDocument(uri: Uri, displayName: String, targetPaneIndex: Int) {
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repo.importUriToDir(uri, destDir, displayName)
                .onSuccess {
                    setStatus("Diimpor ${it.name} ke ${destDir.name}")
                    refreshPane(targetPaneIndex)
                }
                .onFailure { setStatus("Gagal impor: ${it.localizedMessage}") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun setStatus(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    private fun getPane(index: Int): PaneState =
        if (index == 0) _uiState.value.leftPane else _uiState.value.rightPane

    private fun updateActiveTab(paneIndex: Int, transform: (PaneTabState) -> PaneTabState) {
        _uiState.update { state ->
            val pane = if (paneIndex == 0) state.leftPane else state.rightPane
            val idx = pane.activeTabIndex.coerceIn(0, pane.tabs.lastIndex)
            val newTabs = pane.tabs.toMutableList().apply { this[idx] = transform(this[idx]) }
            val updatedPane = pane.copy(tabs = newTabs)
            if (paneIndex == 0) state.copy(leftPane = updatedPane) else state.copy(rightPane = updatedPane)
        }
    }
}
