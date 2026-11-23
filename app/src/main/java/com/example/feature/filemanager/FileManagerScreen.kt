package com.example.feature.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AppContainer
import com.example.core.storage.FileCategory
import com.example.core.storage.FileItem
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import java.io.File

@Composable
fun FileManagerScreen(
    container: AppContainer,
    viewModel: FileManagerViewModel,
    onOpenApkStudio: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenTerminal: (File) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var showRootModeDialog by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var singleDeleteTarget by remember { mutableStateOf<Pair<Int, FileItem>?>(null) }
    var chmodTarget by remember { mutableStateOf<Pair<Int, FileItem>?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var imagePreviewFile by remember { mutableStateOf<File?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingPhoneUnlock by remember { mutableStateOf(false) }

    fun openAllFilesPermissionSettings() {
        pendingPhoneUnlock = true
        val perAppIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        val launched = runCatching { context.startActivity(perAppIntent) }.isSuccess
        if (!launched) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    fun openPhoneOrWorkspace() {
        val phoneDir = container.storageRepository.getPhoneStorageDirectory()
        val target = if (phoneDir != null && phoneDir.exists() && phoneDir.canRead()) {
            phoneDir.absolutePath
        } else {
            container.storageRepository.workspaceDir.absolutePath
        }
        viewModel.navigateTo(uiState.activePaneIndex, target)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironmentState()
                val phoneDir = container.storageRepository.getPhoneStorageDirectory()
                if (pendingPhoneUnlock && phoneDir != null && phoneDir.exists() && phoneDir.canRead()) {
                    pendingPhoneUnlock = false
                    viewModel.navigateTo(uiState.activePaneIndex, phoneDir.absolutePath)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "imported_${System.currentTimeMillis()}"
            viewModel.importSafDocument(uri, name, uiState.activePaneIndex)
        }
    }

    val activePane = if (uiState.activePaneIndex == 0) uiState.leftPane else uiState.rightPane
    val activePath = activePane.activeTab.currentPath

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Engineering Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleDualPane() },
                            modifier = Modifier.testTag("toggle_dual_pane_btn")
                        ) {
                            Icon(
                                Icons.Filled.ViewColumn,
                                contentDescription = "Toggle Dual Pane",
                                tint = if (uiState.dualPaneMode) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.testTag("new_folder_btn")
                        ) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                        }
                        IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier.testTag("new_file_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New File")
                        }
                        IconButton(
                            onClick = { safImportLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("saf_import_btn")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Import via SAF")
                        }
                        IconButton(
                            onClick = { showGoToPathDialog = true },
                            modifier = Modifier.testTag("toolbar_goto_path_btn")
                        ) {
                            Icon(Icons.Filled.EditLocationAlt, contentDescription = "Go to Path")
                        }
                        IconButton(
                            onClick = { viewModel.compareSelectedBetweenPanes() },
                            modifier = Modifier.testTag("compare_panes_btn")
                        ) {
                            Icon(Icons.Filled.Difference, contentDescription = "Compare Selected Files")
                        }
                        IconButton(
                            onClick = {
                                viewModel.refreshPane(0)
                                viewModel.refreshPane(1)
                            },
                            modifier = Modifier.testTag("refresh_panes_btn")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Panes")
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("files_overflow_btn")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More File Tools")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (uiState.showHidden) "Hide Dotfiles" else "Show Hidden Files") },
                                leadingIcon = {
                                    Icon(
                                        if (uiState.showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleShowHidden()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmark Active Folder") },
                                leadingIcon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.addBookmark(uiState.activePaneIndex)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Find Duplicate Files") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.runDuplicateScan(uiState.activePaneIndex)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Storage Analyzer") },
                                leadingIcon = { Icon(Icons.Filled.Analytics, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.runStorageAnalyzer(uiState.activePaneIndex)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Terminal Here") },
                                leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenTerminal(File(activePath))
                                }
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !uiState.hasAllFilesAccess) {
                                DropdownMenuItem(
                                    text = { Text("Grant All Files Access") },
                                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        openAllFilesPermissionSettings()
                                    }
                                )
                            }
                        }
                    }
                }

                // Interactive Non-Root vs Root Explorer Mode Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadgePill(
                        label = "Non-Root • Phone & SAF",
                        color = if (!uiState.rootExplorerEnabled) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Filled.PhoneAndroid,
                        modifier = Modifier
                            .clickable {
                                if (uiState.rootExplorerEnabled) {
                                    viewModel.setRootExplorerEnabled(false)
                                }
                                openPhoneOrWorkspace()
                            }
                            .testTag("non_root_mode_pill")
                    )

                    FilterChip(
                        selected = uiState.rootExplorerEnabled,
                        onClick = {
                            if (uiState.rootExplorerEnabled) {
                                viewModel.setRootExplorerEnabled(false)
                            } else {
                                showRootModeDialog = true
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = "Toggle Root Explorer",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = when {
                                    uiState.rootExplorerEnabled -> "Root Active (${uiState.rootImplLabel})"
                                    uiState.rootAvailable -> "Root Mode (su) • Tap to Enable"
                                    else -> "No Root (su)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StatusWarning.copy(alpha = 0.22f),
                            selectedLabelColor = StatusWarning,
                            selectedLeadingIconColor = StatusWarning
                        ),
                        modifier = Modifier.testTag("root_explorer_toggle_chip")
                    )
                }

                // Non-Root Quick Roots + Bookmarks Ribbon
                val quickRoots = remember(
                    uiState.hasAllFilesAccess,
                    uiState.leftPane.activeTab.currentPath,
                    uiState.rightPane.activeTab.currentPath
                ) {
                    container.storageRepository.getQuickRoots()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !uiState.hasAllFilesAccess) {
                        FilterChip(
                            selected = false,
                            onClick = { openAllFilesPermissionSettings() },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.LockOpen,
                                    contentDescription = "Unlock Phone Storage",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text("Phone Storage (Unlock)", style = MaterialTheme.typography.labelSmall)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("unlock_phone_storage_chip")
                        )
                    }
                    quickRoots.forEach { (label, dir) ->
                        val isPhoneRoot = label == "Phone Storage"
                        FilterChip(
                            selected = activePath == dir.absolutePath,
                            onClick = { viewModel.navigateTo(uiState.activePaneIndex, dir.absolutePath) },
                            leadingIcon = if (isPhoneRoot) {
                                {
                                    Icon(
                                        Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("quick_root_${label.lowercase().replace(' ', '_')}")
                        )
                    }
                    bookmarks.forEach { bm ->
                        FilterChip(
                            selected = activePath == bm.path,
                            onClick = { viewModel.navigateTo(uiState.activePaneIndex, bm.path) },
                            label = { Text("★ ${bm.label}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Root Directories Ribbon (shown when Root Explorer su mode is active)
                val rootQuickRoots = remember { container.storageRepository.getRootQuickRoots() }
                AnimatedVisibility(visible = uiState.rootExplorerEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rootQuickRoots.forEach { (label, dir) ->
                            FilterChip(
                                selected = activePath == dir.absolutePath,
                                onClick = { viewModel.navigateTo(uiState.activePaneIndex, dir.absolutePath) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldTertiary.copy(alpha = 0.22f),
                                    selectedLabelColor = EmeraldTertiary,
                                    selectedLeadingIconColor = EmeraldTertiary
                                ),
                                modifier = Modifier.testTag("root_dir_${dir.name.ifEmpty { "slash" }}")
                            )
                        }
                    }
                }

                uiState.statusMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss status",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.clearStatus() }
                        )
                    }
                }
                if (uiState.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Batch Action Toolbar when items selected in active pane
        val selectedCount = activePane.activeTab.selectedPaths.size
        if (selectedCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row {
                        IconButton(onClick = { viewModel.selectAll(uiState.activePaneIndex) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { viewModel.copySelectedToOppositePane(uiState.activePaneIndex) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy to Opposite Pane")
                        }
                        IconButton(onClick = { viewModel.moveSelectedToOppositePane(uiState.activePaneIndex) }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to Opposite Pane")
                        }
                        IconButton(onClick = { showCompressDialog = true }) {
                            Icon(Icons.Filled.Archive, contentDescription = "Compress to ZIP")
                        }
                        IconButton(onClick = { confirmBatchDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Selected", tint = StatusDanger)
                        }
                        IconButton(onClick = { viewModel.clearSelection(uiState.activePaneIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                        }
                    }
                }
            }
        }

        // Adaptive Dual-Pane Container: side-by-side on >= 600dp, stacked on compact phones
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWide = maxWidth >= 600.dp

            val onItemOpen: (Int, FileItem) -> Unit = { paneIdx, item ->
                if (item.isDirectory) {
                    viewModel.navigateTo(paneIdx, item.path)
                } else {
                    viewModel.recordRecentFile(item)
                    viewModel.stageFileIfNeeded(File(item.path)) { readableFile ->
                        when (item.category) {
                            FileCategory.APK_BUNDLE -> onOpenApkStudio(readableFile.absolutePath)
                            FileCategory.ARCHIVE -> viewModel.openArchiveInspector(readableFile)
                            FileCategory.IMAGE -> imagePreviewFile = readableFile
                            else -> onOpenEditor(readableFile.absolutePath)
                        }
                    }
                }
            }

            val onEditStaged: (FileItem) -> Unit = { item ->
                viewModel.recordRecentFile(item)
                viewModel.stageFileIfNeeded(File(item.path)) { readableFile ->
                    onOpenEditor(readableFile.absolutePath)
                }
            }

            val onInspectArchiveStaged: (FileItem) -> Unit = { item ->
                viewModel.stageFileIfNeeded(File(item.path)) { readableFile ->
                    viewModel.openArchiveInspector(readableFile)
                }
            }

            @Composable
            fun RenderPane(pane: PaneState, paneModifier: Modifier) {
                FilePaneView(
                    pane = pane,
                    isActive = !uiState.dualPaneMode || uiState.activePaneIndex == pane.paneIndex,
                    rootExplorerEnabled = uiState.rootExplorerEnabled,
                    rootAvailable = uiState.rootAvailable,
                    hasAllFilesAccess = uiState.hasAllFilesAccess,
                    onActivate = { viewModel.setActivePane(pane.paneIndex) },
                    onNavigateUp = { viewModel.navigateUp(pane.paneIndex) },
                    onNavigateTo = { viewModel.navigateTo(pane.paneIndex, it) },
                    onOpenGoToPath = {
                        viewModel.setActivePane(pane.paneIndex)
                        showGoToPathDialog = true
                    },
                    onSelectTab = { viewModel.selectTab(pane.paneIndex, it) },
                    onAddTab = { viewModel.addTab(pane.paneIndex) },
                    onCloseTab = { viewModel.closeTab(pane.paneIndex, it) },
                    onSearchChange = { q, r -> viewModel.updateSearch(pane.paneIndex, q, r) },
                    onSortChange = { viewModel.updateSort(pane.paneIndex, it) },
                    onFilterChange = { viewModel.updateFilter(pane.paneIndex, it) },
                    onToggleSelect = { viewModel.toggleSelection(pane.paneIndex, it) },
                    onOpenItem = { onItemOpen(pane.paneIndex, it) },
                    onCopyToOtherPane = { viewModel.copySingleToOppositePane(pane.paneIndex, it) },
                    onMoveToOtherPane = { viewModel.moveSingleToOppositePane(pane.paneIndex, it) },
                    onProperties = { viewModel.inspectProperties(it) },
                    onRename = { renameTarget = it },
                    onChmod = { chmodTarget = pane.paneIndex to it },
                    onDelete = { singleDeleteTarget = pane.paneIndex to it },
                    onInspectArchive = onInspectArchiveStaged,
                    onOpenInEditor = onEditStaged,
                    onRequestPhoneUnlock = { openAllFilesPermissionSettings() },
                    onRequestRootEnable = { showRootModeDialog = true },
                    onOpenPhoneOrWorkspace = { openPhoneOrWorkspace() },
                    modifier = paneModifier
                )
            }

            if (!uiState.dualPaneMode) {
                val pane = if (uiState.activePaneIndex == 0) uiState.leftPane else uiState.rightPane
                RenderPane(pane, Modifier.fillMaxSize())
            } else if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RenderPane(uiState.leftPane, Modifier.weight(1f).fillMaxHeight())
                    RenderPane(uiState.rightPane, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RenderPane(uiState.leftPane, Modifier.weight(1f).fillMaxWidth())
                    RenderPane(uiState.rightPane, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "New Folder",
            label = "Folder name",
            initialValue = "",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(uiState.activePaneIndex, name)
            }
        )
    }

    if (showCreateFileDialog) {
        TextInputDialog(
            title = "New File",
            label = "File name (e.g. notes.xml)",
            initialValue = "",
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { name ->
                showCreateFileDialog = false
                viewModel.createFile(uiState.activePaneIndex, name)
            }
        )
    }

    if (showCompressDialog) {
        TextInputDialog(
            title = "Create ZIP Archive",
            label = "Archive name",
            initialValue = "archive_${System.currentTimeMillis() % 10000}.zip",
            onDismiss = { showCompressDialog = false },
            onConfirm = { name ->
                showCompressDialog = false
                viewModel.compressSelected(uiState.activePaneIndex, name)
            }
        )
    }

    if (showGoToPathDialog) {
        val nonRootPresets = remember(uiState.hasAllFilesAccess) {
            container.storageRepository.getQuickRoots().map { it.first to it.second.absolutePath }
        }
        val rootPresets = remember {
            container.storageRepository.getRootQuickRoots().map { it.first to it.second.absolutePath }
        }
        GoToPathDialog(
            initialPath = activePath,
            nonRootPresets = nonRootPresets,
            rootPresets = rootPresets,
            rootExplorerEnabled = uiState.rootExplorerEnabled,
            rootAvailable = uiState.rootAvailable,
            onDismiss = { showGoToPathDialog = false },
            onConfirm = { target ->
                showGoToPathDialog = false
                viewModel.navigateToPath(uiState.activePaneIndex, target)
            }
        )
    }

    if (showRootModeDialog) {
        RootModeDialog(
            rootAvailable = uiState.rootAvailable,
            rootImplLabel = uiState.rootImplLabel,
            suBinaryPath = uiState.suBinaryPath,
            onDismiss = { showRootModeDialog = false },
            onConfirmEnable = {
                showRootModeDialog = false
                viewModel.setRootExplorerEnabled(true)
            },
            onOpenPhoneStorageOrUnlock = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !uiState.hasAllFilesAccess) {
                    openAllFilesPermissionSettings()
                } else {
                    openPhoneOrWorkspace()
                }
            }
        )
    }

    chmodTarget?.let { (paneIdx, target) ->
        ChmodDialog(
            item = target,
            onDismiss = { chmodTarget = null },
            onConfirm = { mode ->
                chmodTarget = null
                viewModel.chmodItem(paneIdx, target, mode)
            }
        )
    }

    if (confirmBatchDelete) {
        val names = activePane.activeTab.selectedPaths.map { File(it).name.ifEmpty { it } }
        DeleteConfirmDialog(
            targetNames = names,
            rootModeActive = uiState.rootExplorerEnabled,
            onDismiss = { confirmBatchDelete = false },
            onConfirm = {
                confirmBatchDelete = false
                viewModel.deleteSelected(uiState.activePaneIndex)
            }
        )
    }

    singleDeleteTarget?.let { (paneIdx, item) ->
        DeleteConfirmDialog(
            targetNames = listOf(item.name),
            rootModeActive = uiState.rootExplorerEnabled,
            onDismiss = { singleDeleteTarget = null },
            onConfirm = {
                singleDeleteTarget = null
                viewModel.deleteSingle(paneIdx, item)
            }
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename ${target.name}",
            label = "New name",
            initialValue = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameItem(uiState.activePaneIndex, target, newName)
            }
        )
    }

    uiState.propertiesModal?.let { props ->
        PropertiesDialog(props = props, onDismiss = { viewModel.dismissProperties() })
    }

    imagePreviewFile?.let { imgFile ->
        ImagePreviewDialog(imgFile = imgFile, onDismiss = { imagePreviewFile = null })
    }

    uiState.archiveInspectFile?.let { archive ->
        ArchiveInspectorDialog(
            archive = archive,
            entries = uiState.archiveEntries,
            onDismiss = { viewModel.closeArchiveInspector() },
            onExtractAll = { viewModel.extractCurrentArchive(uiState.activePaneIndex) }
        )
    }

    uiState.duplicatesResult?.let { groups ->
        DuplicatesDialog(groups = groups, onDismiss = { viewModel.dismissDuplicates() })
    }

    uiState.storageStatsResult?.let { stats ->
        StorageStatsDialog(stats = stats, onDismiss = { viewModel.dismissStorageAnalyzer() })
    }

    uiState.diffLines?.let { lines ->
        DiffDialog(
            title = uiState.diffTitle ?: "File Comparison",
            lines = lines,
            onDismiss = { viewModel.dismissDiff() }
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}
