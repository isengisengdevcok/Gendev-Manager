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

    private val initialWorkspace = repo.workspaceDir.absolutePath
    private val initialSamples = File(repo.workspaceDir, "Samples").apply { mkdirs() }.absolutePath

    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            leftPane = PaneState(
                paneIndex = 0,
                tabs = listOf(
                    PaneTabState(id = "l1", title = "Workspace", currentPath = initialWorkspace)
                )
            ),
            rightPane = PaneState(
                paneIndex = 1,
                tabs = listOf(
                    PaneTabState(id = "r1", title = "Samples", currentPath = initialSamples)
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
            container.settingsDataStore.settingsFlow.collect { s ->
                _uiState.update { it.copy(dualPaneMode = s.dualPaneEnabled, showHidden = s.showHiddenFiles) }
                refreshPane(0)
                refreshPane(1)
            }
        }
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
        updateActiveTab(paneIndex) { tab ->
            val f = File(path)
            tab.copy(
                currentPath = path,
                title = f.name.ifEmpty { "/" },
                selectedPaths = emptySet(),
                recursiveSearch = false
            )
        }
        refreshPane(paneIndex)
    }

    fun navigateUp(paneIndex: Int) {
        val cur = getPane(paneIndex).activeTab.currentPath
        val parent = File(cur).parentFile
        if (parent != null && parent.canRead()) {
            navigateTo(paneIndex, parent.absolutePath)
        } else {
            setStatus("Parent directory is not readable without root/SAF access.")
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
                    query = tab.searchQuery
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
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.copyFiles(selected, destDir)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { count ->
                setStatus("Copied $count item(s) to ${destDir.name}")
                clearSelection(sourcePaneIndex)
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
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = repo.moveFiles(selected, destDir)
            _uiState.update { it.copy(isBusy = false) }
            res.onSuccess { count ->
                setStatus("Moved $count item(s) to ${destDir.name}")
                clearSelection(sourcePaneIndex)
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Move failed: ${it.localizedMessage}") }
        }
    }

    fun deleteSelected(paneIndex: Int) {
        val selected = getPane(paneIndex).activeTab.selectedPaths.map { File(it) }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val res = repo.deleteFiles(selected, createBackup = true)
            res.onSuccess { count ->
                setStatus("Deleted $count item(s) (files backed up in Backups/)")
                clearSelection(paneIndex)
                refreshPane(0)
                refreshPane(1)
            }.onFailure { setStatus("Delete failed: ${it.localizedMessage}") }
        }
    }

    fun createFolder(paneIndex: Int, name: String) {
        val parent = File(getPane(paneIndex).activeTab.currentPath)
        viewModelScope.launch {
            repo.createFolder(parent, name)
                .onSuccess {
                    setStatus("Created folder ${it.name}")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Create folder error: ${it.localizedMessage}") }
        }
    }

    fun createFile(paneIndex: Int, name: String) {
        val parent = File(getPane(paneIndex).activeTab.currentPath)
        viewModelScope.launch {
            repo.createFile(parent, name)
                .onSuccess {
                    setStatus("Created file ${it.name}")
                    refreshPane(paneIndex)
                }
                .onFailure { setStatus("Create file error: ${it.localizedMessage}") }
        }
    }

    fun renameItem(paneIndex: Int, item: FileItem, newName: String) {
        viewModelScope.launch {
            repo.renameFile(File(item.path), newName)
                .onSuccess {
                    setStatus("Renamed to ${it.name}")
                    refreshPane(0)
                    refreshPane(1)
                }
                .onFailure { setStatus("Rename failed: ${it.localizedMessage}") }
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
        viewModelScope.launch {
            val file = File(item.path)
            val (sha, md5, preview) = withContext(Dispatchers.IO) {
                val s = if (file.isFile) HashUtils.calculateHash(file, "SHA-256") else "Directory"
                val m = if (file.isFile) HashUtils.calculateHash(file, "MD5") else "Directory"
                val p = if (file.isFile && file.length() in 1..65_536) {
                    runCatching { file.readText().take(2000) }.getOrNull()
                } else null
                Triple(s, m, p)
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
