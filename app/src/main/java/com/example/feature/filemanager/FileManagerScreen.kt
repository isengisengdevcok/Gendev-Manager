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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.VerticalDivider
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
    var showQuickAddMenu by remember { mutableStateOf(false) }
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

    fun openPhoneOrWorkspace(paneIdx: Int = 0) {
        val phoneDir = container.storageRepository.getPhoneStorageDirectory()
        val target = if (phoneDir != null && phoneDir.exists() && phoneDir.canRead()) {
            phoneDir.absolutePath
        } else {
            container.storageRepository.workspaceDir.absolutePath
        }
        viewModel.navigateTo(paneIdx, target)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironmentState()
                val phoneDir = container.storageRepository.getPhoneStorageDirectory()
                if (pendingPhoneUnlock && phoneDir != null && phoneDir.exists() && phoneDir.canRead()) {
                    pendingPhoneUnlock = false
                    viewModel.navigateTo(0, phoneDir.absolutePath)
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
    val activeTab = activePane.activeTab
    val activePath = activeTab.currentPath

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact Top Header: Active Path + Summary + Root/Non-Root Controls + Quick Jumps
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header Row: Active Path + Stats + Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showGoToPathDialog = true }
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (activePath == "/") "/" else "$activePath/",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val countLine = "Folder: ${activeTab.folderCount}  File: ${activeTab.fileCount}" +
                            if (activeTab.storageSummary.isNotBlank()) "  •  ${activeTab.storageSummary}" else ""
                        Text(
                            text = countLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleDualPane() },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("toggle_dual_pane_btn")
                        ) {
                            Icon(
                                Icons.Filled.ViewColumn,
                                contentDescription = "Toggle Dual Pane",
                                tint = if (uiState.dualPaneMode) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.refreshPane(0)
                                viewModel.refreshPane(1)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("refresh_panes_btn")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(19.dp))
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("files_overflow_btn")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More File Tools", modifier = Modifier.size(19.dp))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Go to Path...") },
                                    leadingIcon = { Icon(Icons.Filled.EditLocationAlt, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; showGoToPathDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import via SAF") },
                                    leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; safImportLauncher.launch(arrayOf("*/*")) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (uiState.showHidden) "Hide Dotfiles" else "Show Hidden Files") },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = { showOverflowMenu = false; viewModel.toggleShowHidden() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Compare Selected (Left vs Right)") },
                                    leadingIcon = { Icon(Icons.Filled.Difference, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; viewModel.compareSelectedBetweenPanes() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmark Active Folder") },
                                    leadingIcon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; viewModel.addBookmark(uiState.activePaneIndex) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Find Duplicate Files") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; viewModel.runDuplicateScan(uiState.activePaneIndex) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Storage Analyzer") },
                                    leadingIcon = { Icon(Icons.Filled.Analytics, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; viewModel.runStorageAnalyzer(uiState.activePaneIndex) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open Terminal Here") },
                                    leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onOpenTerminal(File(activePath)) }
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !uiState.hasAllFilesAccess) {
                                    DropdownMenuItem(
                                        text = { Text("Grant All Files Access") },
                                        leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                        onClick = { showOverflowMenu = false; openAllFilesPermissionSettings() }
                                    )
                                }
                            }
                        }
                    }
                }

                // Single Combined Scrollable Strip: Non-Root / Root Status + Quick Directories
                val quickRoots = remember(
                    uiState.hasAllFilesAccess,
                    uiState.leftPane.activeTab.currentPath,
                    uiState.rightPane.activeTab.currentPath
                ) {
                    container.storageRepository.getQuickRoots()
                }
                val rootQuickRoots = remember { container.storageRepository.getRootQuickRoots() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadgePill(
                        label = "Kiri: Biasa",
                        color = CyanSecondary,
                        icon = Icons.Filled.PhoneAndroid,
                        modifier = Modifier
                            .clickable {
                                viewModel.setActivePane(0)
                                openPhoneOrWorkspace(0)
                            }
                            .testTag("non_root_mode_pill")
                    )

                    FilterChip(
                        selected = uiState.rootExplorerEnabled,
                        onClick = {
                            if (uiState.rootExplorerEnabled) {
                                viewModel.setActivePane(1)
                                viewModel.navigateTo(1, "/")
                            } else {
                                showRootModeDialog = true
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = "Root Explorer",
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = when {
                                    uiState.rootVerifiedUid0 && uiState.rootExplorerEnabled -> "Kanan: Root (${uiState.rootImplLabel})"
                                    uiState.rootAvailable -> "Kanan: Root (su) • Ketuk Izin"
                                    else -> "Non-Root Device"
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !uiState.hasAllFilesAccess) {
                        FilterChip(
                            selected = false,
                            onClick = { openAllFilesPermissionSettings() },
                            leadingIcon = {
                                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("Unlock Storage", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag("unlock_phone_storage_chip")
                        )
                    }

                    quickRoots.forEach { (label, dir) ->
                        FilterChip(
                            selected = activePath == dir.absolutePath,
                            onClick = { viewModel.navigateTo(uiState.activePaneIndex, dir.absolutePath) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("quick_root_${label.lowercase().replace(' ', '_')}")
                        )
                    }

                    if (uiState.rootExplorerEnabled) {
                        rootQuickRoots.forEach { (label, dir) ->
                            FilterChip(
                                selected = activePath == dir.absolutePath,
                                onClick = {
                                    val targetPane = if (uiState.dualPaneMode) 1 else uiState.activePaneIndex
                                    viewModel.setActivePane(targetPane)
                                    viewModel.navigateTo(targetPane, dir.absolutePath)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldTertiary.copy(alpha = 0.22f),
                                    selectedLabelColor = EmeraldTertiary
                                ),
                                modifier = Modifier.testTag("root_dir_${dir.name.ifEmpty { "slash" }}")
                            )
                        }
                    }

                    bookmarks.forEach { bm ->
                        FilterChip(
                            selected = activePath == bm.path,
                            onClick = { viewModel.navigateTo(uiState.activePaneIndex, bm.path) },
                            label = { Text("★ ${bm.label}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                uiState.statusMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss status",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.clearStatus() }
                        )
                    }
                }
                if (uiState.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Batch Selection Toolbar when items selected in active pane
        val selectedCount = activeTab.selectedPaths.size
        AnimatedVisibility(visible = selectedCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$selectedCount dipilih",
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

        // Side-by-Side Dual-Pane Split View (ALWAYS Side-by-Side when Dual-Pane Mode is enabled, even on portrait phones!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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
            fun RenderPane(pane: PaneState, isRootSide: Boolean, paneModifier: Modifier) {
                FilePaneView(
                    pane = pane,
                    isActive = !uiState.dualPaneMode || uiState.activePaneIndex == pane.paneIndex,
                    isRootSide = isRootSide,
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
                    onOpenPhoneOrWorkspace = { openPhoneOrWorkspace(pane.paneIndex) },
                    modifier = paneModifier
                )
            }

            if (!uiState.dualPaneMode) {
                val pane = if (uiState.activePaneIndex == 0) uiState.leftPane else uiState.rightPane
                RenderPane(pane, isRootSide = pane.paneIndex == 1, paneModifier = Modifier.fillMaxSize().padding(4.dp))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 3.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    RenderPane(
                        pane = uiState.leftPane,
                        isRootSide = false,
                        paneModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    RenderPane(
                        pane = uiState.rightPane,
                        isRootSide = true,
                        paneModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // Ergonomic Bottom Action Bar (Back | Forward | + New | Copy/Swap Pane | Up)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.goBack(uiState.activePaneIndex) },
                    enabled = activeTab.canGoBack,
                    modifier = Modifier.testTag("bottom_back_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (activeTab.canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = { viewModel.goForward(uiState.activePaneIndex) },
                    enabled = activeTab.canGoForward,
                    modifier = Modifier.testTag("bottom_forward_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (activeTab.canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
                Box {
                    IconButton(
                        onClick = { showQuickAddMenu = true },
                        modifier = Modifier.testTag("bottom_add_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Create File or Folder")
                    }
                    DropdownMenu(
                        expanded = showQuickAddMenu,
                        onDismissRequest = { showQuickAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Folder Baru") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                            onClick = { showQuickAddMenu = false; showCreateFolderDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("File Baru") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { showQuickAddMenu = false; showCreateFileDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Impor File (SAF)") },
                            leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                            onClick = { showQuickAddMenu = false; safImportLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (selectedCount > 0) {
                            viewModel.copySelectedToOppositePane(uiState.activePaneIndex)
                        } else {
                            val nextPane = if (uiState.activePaneIndex == 0) 1 else 0
                            viewModel.setActivePane(nextPane)
                        }
                    },
                    modifier = Modifier.testTag("bottom_swap_copy_btn")
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = if (selectedCount > 0) "Copy Selected to Opposite Pane" else "Switch Active Pane",
                        tint = if (selectedCount > 0) CyanSecondary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { viewModel.navigateUp(uiState.activePaneIndex) },
                    modifier = Modifier.testTag("bottom_up_btn")
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Parent Directory")
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "Folder Baru",
            label = "Nama folder",
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
            title = "File Baru",
            label = "Nama file (contoh: notes.xml)",
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
            title = "Buat Arsip ZIP",
            label = "Nama arsip",
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
                viewModel.authorizeAndEnableRoot(pointRightPaneToSlash = true)
            },
            onOpenPhoneStorageOrUnlock = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !uiState.hasAllFilesAccess) {
                    openAllFilesPermissionSettings()
                } else {
                    openPhoneOrWorkspace(0)
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
        val names = activeTab.selectedPaths.map { File(it).name.ifEmpty { it } }
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
            title = "Ubah Nama ${target.name}",
            label = "Nama baru",
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
