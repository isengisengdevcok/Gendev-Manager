package com.example.feature.filemanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppContainer
import com.example.core.common.HashUtils
import com.example.core.data.BookmarkEntity
import com.example.core.data.RecentFileEntity
import com.example.core.storage.ArchiveEntryItem
import com.example.core.storage.DuplicateGroup
import com.example.core.storage.FileDiffLine
import com.example.core.storage.FileItem
import com.example.core.storage.FilterCategory
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
    val filterCategory: FilterCategory = FilterCategory.ALL
)

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
    val rootAvailable: Boolean = false,
    val suBinaryPath: String? = null,
    val rootImplLabel: String = "Non-Root",
    val rootExplorerEnabled: Boolean = false,
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

    private val initialWorkspace = repo.workspaceDir.absolutePath
    private val initialSamples = File(repo.workspaceDir, "Samples").apply { mkdirs() }.absolutePath
    private val initialPhoneDir = repo.getPhoneStorageDirectory()?.takeIf { it.exists() && it.canRead() }

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
                        title = if (initialPhoneDir != null) "Workspace" else "Samples",
                        currentPath = if (initialPhoneDir != null) initialWorkspace else initialSamples
                    )
                )
            )
        )
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = container.database.bookmarkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.ensureWorkspaceInitialized()
            refreshEnvironmentState()
            container.settingsDataStore.settingsFlow.collect { s ->
                _uiState.update { it.copy(dualPaneMode = s.dualPaneEnabled, showHidden = s.showHiddenFiles) }
                refreshPane(0)
                refreshPane(1)
            }
        }
    }

    fun refreshEnvironmentState() {
        viewModelScope.launch {
            val env = withContext(Dispatchers.IO) { envManager.inspectEnvironment() }
            _uiState.update { state ->
                state.copy(
                    rootAvailable = env.rootAvailable,
                    suBinaryPath = env.suBinaryPath,
                    rootImplLabel = env.rootImplementation.displayName,
                    hasAllFilesAccess = env.allFilesAccessGranted,
                    rootExplorerEnabled = if (env.rootAvailable) state.rootExplorerEnabled else false
                )
            }
            refreshPane(0)
            refreshPane(1)
        }
    }

    fun setRootExplorerEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (enabled && (!state.rootAvailable || state.suBinaryPath == null)) {
            setStatus("Root (su) binary not detected. Non-Root / SAF mode remains active.")
            return
        }
        _uiState.update { it.copy(rootExplorerEnabled = enabled) }
        if (enabled) {
            setStatus("Root Explorer enabled (${state.rootImplLabel}). Browse / , /data , /system with care.")
        } else {
            setStatus("Switched to standard Non-Root File Manager.")
        }
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
            tab.copy(
                currentPath = clean,
                title = if (clean == "/") "/" else f.name.ifEmpty { "/" },
                selectedPaths = emptySet(),
                recursiveSearch = false
            )
        }
        refreshPane(paneIndex)
    }

    fun navigateToPath(paneIndex: Int, rawPath: String) {
        val cleaned = rawPath.trim()
        if (cleaned.isEmpty()) {
            setStatus("Enter a valid directory path.")
            return
        }
        val normalized = if (cleaned == "/") "/" else cleaned.trimEnd('/')
        val state = _uiState.value
        val dir = File(normalized)
        if ((dir.exists() && dir.isDirectory && dir.canRead()) || (state.rootExplorerEnabled && state.suBinaryPath != null)) {
            navigateTo(paneIndex, normalized)
        } else if (state.rootAvailable) {
            setStatus("Path requires Root Explorer (su). Enable Root toggle at the top first.")
        } else {
            setStatus("Path is not readable without Root or All Files Access.")
        }
    }

    fun navigateUp(paneIndex: Int) {
        val cur = getPane(paneIndex).activeTab.currentPath
        if (cur == "/") {
            setStatus("Already at root (/)")
            return
        }
        val parent = File(cur).parentFile
        val state = _uiState.value
        if (parent != null && (parent.canRead() || (state.rootExplorerEnabled && state.suBinaryPath != null))) {
            navigateTo(paneIndex, parent.absolutePath)
        } else if (state.rootAvailable) {
            setStatus("Parent directory requires Root Explorer (su). Enable Root toggle above.")
        } else {
            setStatus("Parent directory is restricted in Non-Root mode (use Quick Roots above).")
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
            updateActiveTab(paneIndex) { it.copy(items = items) }
        }
    }

    fun copySelectedToOppositePane(sourcePaneIndex: Int) {
        val targetPaneIndex = if (sourcePaneIndex == 0) 1 else 0
        val selected = getPane(sourcePaneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) {
            setStatus("Select one or more items first.")
            return
        }
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.copyFiles(selected, destDir, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { count ->
                setStatus("Copied $count item(s) to ${destDir.name.ifEmpty { "/" }}")
                clearSelection(sourcePaneIndex)
                refreshPane(targetPaneIndex)
            }.onFailure { setStatus("Copy failed: ${it.localizedMessage}") }
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
                setStatus("Copied ${item.name} -> ${destDir.name.ifEmpty { "/" }}")
                refreshPane(targetPaneIndex)
            }.onFailure { setStatus("Copy failed: ${it.localizedMessage}") }
        }
    }

    fun moveSelectedToOppositePane(sourcePaneIndex: Int) {
        val targetPaneIndex = if (sourcePaneIndex == 0) 1 else 0
        val selected = getPane(sourcePaneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) {
            setStatus("Select one or more items first.")
            return
        }
        val destDir = File(getPane(targetPaneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.moveFiles(selected, destDir, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { count ->
                setStatus("Moved $count item(s) to ${destDir.name.ifEmpty { "/" }}")
                clearSelection(sourcePaneIndex)
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Move failed: ${it.localizedMessage}") }
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
                setStatus("Moved ${item.name} -> ${destDir.name.ifEmpty { "/" }}")
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Move failed: ${it.localizedMessage}") }
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
                setStatus("Deleted $count item(s)")
                clearSelection(paneIndex)
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Delete failed: ${it.localizedMessage}") }
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
                setStatus("Deleted ${item.name}")
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Delete failed: ${it.localizedMessage}") }
        }
    }

    fun createFolder(paneIndex: Int, name: String) {
        val parent = File(getPane(paneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            repo.createFolder(parent, name, state.rootExplorerEnabled, state.suBinaryPath)
                .onSuccess {
                    setStatus("Created folder ${it.name}")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Create folder error: ${it.localizedMessage}") }
        }
    }

    fun createFile(paneIndex: Int, name: String) {
        val parent = File(getPane(paneIndex).activeTab.currentPath)
        val state = _uiState.value
        viewModelScope.launch {
            repo.createFile(parent, name, useRoot = state.rootExplorerEnabled, suBinary = state.suBinaryPath)
                .onSuccess {
                    setStatus("Created file ${it.name}")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Create file error: ${it.localizedMessage}") }
        }
    }

    fun renameItem(paneIndex: Int, item: FileItem, newName: String) {
        val state = _uiState.value
        viewModelScope.launch {
            repo.renameFile(File(item.path), newName, state.rootExplorerEnabled, state.suBinaryPath)
                .onSuccess {
                    setStatus("Renamed to ${it.name}")
                    refreshPane(0)
                    refreshPane(1)
                }
                .onFailure { setStatus("Rename failed: ${it.localizedMessage}") }
        }
    }

    fun chmodItem(paneIndex: Int, item: FileItem, octalMode: String) {
        val su = _uiState.value.suBinaryPath
        if (!_uiState.value.rootExplorerEnabled || su == null) {
            setStatus("chmod requires Root Explorer (su) to be active.")
            return
        }
        viewModelScope.launch {
            repo.chmodRoot(File(item.path), octalMode, su)
                .onSuccess {
                    setStatus("Updated permissions of ${item.name} to $octalMode")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("chmod failed: ${it.localizedMessage}") }
        }
    }

    fun stageFileIfNeeded(file: File, onReady: (File) -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.stageReadableCopyIfNeeded(file, state.rootExplorerEnabled, state.suBinaryPath)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess(onReady)
                .onFailure { setStatus(it.localizedMessage ?: "Cannot open protected file") }
        }
    }

    fun compressSelected(paneIndex: Int, zipName: String) {
        val selected = getPane(paneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) {
            setStatus("Select files to compress.")
            return
        }
        val cleanName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
        val outZip = File(getPane(paneIndex).activeTab.currentPath, cleanName)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            archiveEngine.compressFiles(selected, outZip)
                .onSuccess {
                    setStatus("Compressed ${selected.size} item(s) -> ${it.name}")
                    clearSelection(paneIndex)
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Compression failed: ${it.localizedMessage}") }
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
                    _uiState.update { state -> state.copy(isBusy = false) }
                    setStatus("Archive error: ${it.localizedMessage}")
                }
        }
    }

    fun closeArchiveInspector() {
        _uiState.update { it.copy(archiveInspectFile = null, archiveEntries = emptyList()) }
    }

    fun extractCurrentArchive(paneIndex: Int) {
        val archive = _uiState.value.archiveInspectFile ?: return
        val destDir = File(getPane(paneIndex).activeTab.currentPath, archive.nameWithoutExtension)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            archiveEngine.extractAll(archive, destDir)
                .onSuccess { count ->
                    closeArchiveInspector()
                    setStatus("Extracted $count entries to ${destDir.name}/")
                    refreshPane(0)
                    refreshPane(1)
                }
                .onFailure {
                    _uiState.update { s -> s.copy(isBusy = false) }
                    setStatus("Extraction failed: ${it.localizedMessage}")
                }
        }
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
            setStatus("Bookmarked ${tab.title}")
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
            setStatus("Select 1 file in Left Pane and 1 file in Right Pane to compare.")
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
                    setStatus("Imported ${it.name} into ${destDir.name}")
                    refreshPane(targetPaneIndex)
                }
                .onFailure { setStatus("Import error: ${it.localizedMessage}") }
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
