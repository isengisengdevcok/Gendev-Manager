package com.example.feature.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.AppContainer
import com.example.core.common.HashUtils
import com.example.core.storage.DiffLineType
import com.example.core.storage.FileCategory
import com.example.core.storage.FileItem
import com.example.core.storage.FilterCategory
import com.example.core.storage.SortBy
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.KeyValueRow
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary
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

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var imagePreviewFile by remember { mutableStateOf<File?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "imported_${System.currentTimeMillis()}"
            viewModel.importSafDocument(uri, name, uiState.activePaneIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Engineering Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                        }
                        IconButton(onClick = { showCreateFileDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "New File")
                        }
                        IconButton(onClick = { safImportLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Import via SAF")
                        }
                        IconButton(onClick = { viewModel.compareSelectedBetweenPanes() }) {
                            Icon(Icons.Filled.Difference, contentDescription = "Compare Selected Files")
                        }
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
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
                                    val activePath = if (uiState.activePaneIndex == 0) {
                                        uiState.leftPane.activeTab.currentPath
                                    } else {
                                        uiState.rightPane.activeTab.currentPath
                                    }
                                    onOpenTerminal(File(activePath))
                                }
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                DropdownMenuItem(
                                    text = { Text("Grant All Files Access") },
                                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        runCatching { context.startActivity(intent) }
                                    }
                                )
                            }
                        }
                    }
                }

                // Quick Root & Bookmark Ribbon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    container.storageRepository.getQuickRoots().forEach { (label, dir) ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.navigateTo(uiState.activePaneIndex, dir.absolutePath) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    bookmarks.forEach { bm ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.navigateTo(uiState.activePaneIndex, bm.path) },
                            label = { Text("★ ${bm.label}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                uiState.statusMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
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
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }

        // Batch Action Toolbar when items selected in active pane
        val activePane = if (uiState.activePaneIndex == 0) uiState.leftPane else uiState.rightPane
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
                        IconButton(onClick = { viewModel.deleteSelected(uiState.activePaneIndex) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Selected", tint = StatusDanger)
                        }
                        IconButton(onClick = { viewModel.clearSelection(uiState.activePaneIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                        }
                    }
                }
            }
        }

        // Adaptive Dual-Pane Container: side-by-side on >= 600dp, stacked top/bottom on compact phones
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 600.dp
            val onItemOpen: (Int, FileItem) -> Unit = { paneIdx, item ->
                if (item.isDirectory) {
                    viewModel.navigateTo(paneIdx, item.path)
                } else {
                    viewModel.recordRecentFile(item)
                    when (item.category) {
                        FileCategory.APK_BUNDLE -> onOpenApkStudio(item.path)
                        FileCategory.ARCHIVE -> viewModel.openArchiveInspector(File(item.path))
                        FileCategory.IMAGE -> imagePreviewFile = File(item.path)
                        else -> onOpenEditor(item.path)
                    }
                }
            }

            if (!uiState.dualPaneMode) {
                val pane = if (uiState.activePaneIndex == 0) uiState.leftPane else uiState.rightPane
                FilePaneView(
                    pane = pane,
                    isActive = true,
                    onActivate = {},
                    onNavigateUp = { viewModel.navigateUp(pane.paneIndex) },
                    onNavigateTo = { viewModel.navigateTo(pane.paneIndex, it) },
                    onSelectTab = { viewModel.selectTab(pane.paneIndex, it) },
                    onAddTab = { viewModel.addTab(pane.paneIndex) },
                    onCloseTab = { viewModel.closeTab(pane.paneIndex, it) },
                    onSearchChange = { q, r -> viewModel.updateSearch(pane.paneIndex, q, r) },
                    onSortChange = { viewModel.updateSort(pane.paneIndex, it) },
                    onFilterChange = { viewModel.updateFilter(pane.paneIndex, it) },
                    onToggleSelect = { viewModel.toggleSelection(pane.paneIndex, it) },
                    onOpenItem = { onItemOpen(pane.paneIndex, it) },
                    onProperties = { viewModel.inspectProperties(it) },
                    onRename = { renameTarget = it },
                    onInspectArchive = { viewModel.openArchiveInspector(File(it.path)) },
                    onOpenInEditor = { onOpenEditor(it.path) },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilePaneView(
                        pane = uiState.leftPane,
                        isActive = uiState.activePaneIndex == 0,
                        onActivate = { viewModel.setActivePane(0) },
                        onNavigateUp = { viewModel.navigateUp(0) },
                        onNavigateTo = { viewModel.navigateTo(0, it) },
                        onSelectTab = { viewModel.selectTab(0, it) },
                        onAddTab = { viewModel.addTab(0) },
                        onCloseTab = { viewModel.closeTab(0, it) },
                        onSearchChange = { q, r -> viewModel.updateSearch(0, q, r) },
                        onSortChange = { viewModel.updateSort(0, it) },
                        onFilterChange = { viewModel.updateFilter(0, it) },
                        onToggleSelect = { viewModel.toggleSelection(0, it) },
                        onOpenItem = { onItemOpen(0, it) },
                        onProperties = { viewModel.inspectProperties(it) },
                        onRename = { renameTarget = it },
                        onInspectArchive = { viewModel.openArchiveInspector(File(it.path)) },
                        onOpenInEditor = { onOpenEditor(it.path) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FilePaneView(
                        pane = uiState.rightPane,
                        isActive = uiState.activePaneIndex == 1,
                        onActivate = { viewModel.setActivePane(1) },
                        onNavigateUp = { viewModel.navigateUp(1) },
                        onNavigateTo = { viewModel.navigateTo(1, it) },
                        onSelectTab = { viewModel.selectTab(1, it) },
                        onAddTab = { viewModel.addTab(1) },
                        onCloseTab = { viewModel.closeTab(1, it) },
                        onSearchChange = { q, r -> viewModel.updateSearch(1, q, r) },
                        onSortChange = { viewModel.updateSort(1, it) },
                        onFilterChange = { viewModel.updateFilter(1, it) },
                        onToggleSelect = { viewModel.toggleSelection(1, it) },
                        onOpenItem = { onItemOpen(1, it) },
                        onProperties = { viewModel.inspectProperties(it) },
                        onRename = { renameTarget = it },
                        onInspectArchive = { viewModel.openArchiveInspector(File(it.path)) },
                        onOpenInEditor = { onOpenEditor(it.path) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilePaneView(
                        pane = uiState.leftPane,
                        isActive = uiState.activePaneIndex == 0,
                        onActivate = { viewModel.setActivePane(0) },
                        onNavigateUp = { viewModel.navigateUp(0) },
                        onNavigateTo = { viewModel.navigateTo(0, it) },
                        onSelectTab = { viewModel.selectTab(0, it) },
                        onAddTab = { viewModel.addTab(0) },
                        onCloseTab = { viewModel.closeTab(0, it) },
                        onSearchChange = { q, r -> viewModel.updateSearch(0, q, r) },
                        onSortChange = { viewModel.updateSort(0, it) },
                        onFilterChange = { viewModel.updateFilter(0, it) },
                        onToggleSelect = { viewModel.toggleSelection(0, it) },
                        onOpenItem = { onItemOpen(0, it) },
                        onProperties = { viewModel.inspectProperties(it) },
                        onRename = { renameTarget = it },
                        onInspectArchive = { viewModel.openArchiveInspector(File(it.path)) },
                        onOpenInEditor = { onOpenEditor(it.path) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    FilePaneView(
                        pane = uiState.rightPane,
                        isActive = uiState.activePaneIndex == 1,
                        onActivate = { viewModel.setActivePane(1) },
                        onNavigateUp = { viewModel.navigateUp(1) },
                        onNavigateTo = { viewModel.navigateTo(1, it) },
                        onSelectTab = { viewModel.selectTab(1, it) },
                        onAddTab = { viewModel.addTab(1) },
                        onCloseTab = { viewModel.closeTab(1, it) },
                        onSearchChange = { q, r -> viewModel.updateSearch(1, q, r) },
                        onSortChange = { viewModel.updateSort(1, it) },
                        onFilterChange = { viewModel.updateFilter(1, it) },
                        onToggleSelect = { viewModel.toggleSelection(1, it) },
                        onOpenItem = { onItemOpen(1, it) },
                        onProperties = { viewModel.inspectProperties(it) },
                        onRename = { renameTarget = it },
                        onInspectArchive = { viewModel.openArchiveInspector(File(it.path)) },
                        onOpenInEditor = { onOpenEditor(it.path) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
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
        AlertDialog(
            onDismissRequest = { viewModel.dismissProperties() },
            title = { Text(props.item.name) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    KeyValueRow("Path", props.item.path)
                    KeyValueRow("Size", HashUtils.formatBytes(props.item.sizeBytes))
                    KeyValueRow("Modified", HashUtils.formatTimestamp(props.item.lastModified))
                    KeyValueRow("Permissions", props.item.permissionsString)
                    KeyValueRow("SHA-256", props.sha256)
                    KeyValueRow("MD5", props.md5)
                    props.previewText?.let { text ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Preview:", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProperties() }) { Text("Close") }
            }
        )
    }

    imagePreviewFile?.let { imgFile ->
        AlertDialog(
            onDismissRequest = { imagePreviewFile = null },
            title = { Text(imgFile.name) },
            text = {
                AsyncImage(
                    model = imgFile,
                    contentDescription = imgFile.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = { imagePreviewFile = null }) { Text("Close") }
            }
        )
    }

    uiState.archiveInspectFile?.let { archive ->
        AlertDialog(
            onDismissRequest = { viewModel.closeArchiveInspector() },
            title = { Text("Archive: ${archive.name} (${uiState.archiveEntries.size})") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(uiState.archiveEntries.take(200)) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.path,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (entry.isDirectory) "DIR" else HashUtils.formatBytes(entry.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeArchiveInspector() }) { Text("Close") }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.extractCurrentArchive(uiState.activePaneIndex) }) {
                    Text("Extract All")
                }
            }
        )
    }

    uiState.duplicatesResult?.let { groups ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicates() },
            title = { Text("Duplicate Scanner (${groups.size} groups)") },
            text = {
                if (groups.isEmpty()) {
                    Text("No duplicate files found in this directory tree.")
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups) { g ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "${g.files.size} copies • ${HashUtils.formatBytes(g.sizeBytes)} each",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "SHA256: ${g.sha256.take(18)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanSecondary
                                )
                                g.files.forEach { f ->
                                    Text(f.path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDuplicates() }) { Text("Close") }
            }
        )
    }

    uiState.storageStatsResult?.let { stats ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissStorageAnalyzer() },
            title = { Text("Folder Storage Breakdown") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stats.isEmpty()) {
                        Text("No files found in this folder.")
                    } else {
                        stats.forEach { s ->
                            KeyValueRow(
                                label = "${s.label} (${s.fileCount})",
                                value = HashUtils.formatBytes(s.bytes)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissStorageAnalyzer() }) { Text("Close") }
            }
        )
    }

    uiState.diffLines?.let { lines ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiff() },
            title = { Text(uiState.diffTitle ?: "File Comparison") },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(lines) { line ->
                        val bg = when (line.type) {
                            DiffLineType.ADDED -> StatusSuccess.copy(alpha = 0.18f)
                            DiffLineType.REMOVED -> StatusDanger.copy(alpha = 0.18f)
                            DiffLineType.UNCHANGED -> Color.Transparent
                        }
                        val prefix = when (line.type) {
                            DiffLineType.ADDED -> "+"
                            DiffLineType.REMOVED -> "-"
                            DiffLineType.UNCHANGED -> " "
                        }
                        Text(
                            text = "$prefix ${line.content}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDiff() }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun FilePaneView(
    pane: PaneState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onSelectTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    onSearchChange: (String, Boolean) -> Unit,
    onSortChange: (SortBy) -> Unit,
    onFilterChange: (FilterCategory) -> Unit,
    onToggleSelect: (String) -> Unit,
    onOpenItem: (FileItem) -> Unit,
    onProperties: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onInspectArchive: (FileItem) -> Unit,
    onOpenInEditor: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tab = pane.activeTab
    var showSearchBar by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val borderColor = if (isActive) CyanSecondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable(onClick = onActivate),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Multi-Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                pane.tabs.forEachIndexed { idx, t ->
                    val selected = idx == pane.activeTabIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else Color.Transparent
                            )
                            .clickable { onSelectTab(idx) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (pane.tabs.size > 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close Tab",
                                modifier = Modifier.size(12.dp).clickable { onCloseTab(idx) }
                            )
                        }
                    }
                }
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New Tab",
                    tint = CyanSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onAddTab() }
                        .padding(2.dp)
                )
            }

            // Breadcrumb + Pane Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onActivate(); onNavigateUp() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Parent Folder", modifier = Modifier.size(18.dp))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val segments = tab.currentPath.split('/').filter { it.isNotEmpty() }
                    var acc = ""
                    segments.forEachIndexed { idx, seg ->
                        acc += "/$seg"
                        val target = acc
                        Text(
                            text = if (idx == 0) "/$seg" else " / $seg",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (idx == segments.lastIndex) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onActivate(); onNavigateTo(target) }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = { showSearchBar = !showSearchBar }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Search Pane", modifier = Modifier.size(18.dp))
                }
                Box {
                    Text(
                        text = tab.sortBy.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { sortMenuExpanded = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        SortBy.entries.forEach { sb ->
                            DropdownMenuItem(
                                text = { Text("Sort: ${sb.label}") },
                                onClick = { sortMenuExpanded = false; onSortChange(sb) }
                            )
                        }
                        FilterCategory.entries.forEach { fc ->
                            DropdownMenuItem(
                                text = { Text("Filter: ${fc.label}") },
                                onClick = { sortMenuExpanded = false; onFilterChange(fc) }
                            )
                        }
                    }
                }
            }

            if (showSearchBar) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = tab.searchQuery,
                        onValueChange = { onSearchChange(it, tab.recursiveSearch) },
                        placeholder = { Text("Filter / search...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = tab.recursiveSearch,
                        onClick = { onSearchChange(tab.searchQuery, !tab.recursiveSearch) },
                        label = { Text("Deep", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // File List
            if (tab.items.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Filled.Folder,
                    title = "Empty Directory",
                    message = "No matching files here.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(tab.items, key = { it.path }) { item ->
                        val isSelected = item.path in tab.selectedPaths
                        var rowMenu by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onActivate()
                                    onOpenItem(item)
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    onActivate()
                                    onToggleSelect(item.path)
                                },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = categoryIcon(item.category),
                                contentDescription = item.category.name,
                                tint = categoryColor(item.category),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.isDirectory) {
                                        "${item.childCount} items • ${item.permissionsString}"
                                    } else {
                                        "${HashUtils.formatBytes(item.sizeBytes)} • ${HashUtils.formatTimestamp(item.lastModified)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Box {
                                IconButton(onClick = { rowMenu = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Item Actions", modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Properties & SHA-256") },
                                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                        onClick = { rowMenu = false; onProperties(item) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                                        onClick = { rowMenu = false; onRename(item) }
                                    )
                                    if (!item.isDirectory) {
                                        DropdownMenuItem(
                                            text = { Text("Open in Code/Hex Editor") },
                                            leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null) },
                                            onClick = { rowMenu = false; onOpenInEditor(item) }
                                        )
                                    }
                                    if (item.category == FileCategory.APK_BUNDLE || item.category == FileCategory.ARCHIVE) {
                                        DropdownMenuItem(
                                            text = { Text("Inspect Archive Entries") },
                                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                            onClick = { rowMenu = false; onInspectArchive(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun categoryIcon(cat: FileCategory) = when (cat) {
    FileCategory.DIRECTORY -> Icons.Filled.Folder
    FileCategory.APK_BUNDLE -> Icons.Filled.Android
    FileCategory.ARCHIVE -> Icons.Filled.Archive
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.XML, FileCategory.JSON, FileCategory.SMALI, FileCategory.CODE -> Icons.Filled.Code
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun categoryColor(cat: FileCategory) = when (cat) {
    FileCategory.DIRECTORY -> CyanSecondary
    FileCategory.APK_BUNDLE -> EmeraldTertiary
    FileCategory.ARCHIVE -> StatusWarning
    FileCategory.IMAGE -> VioletPrimary
    FileCategory.XML, FileCategory.JSON, FileCategory.SMALI, FileCategory.CODE -> Color(0xFF38BDF8)
    else -> Color(0xFF94A3B8)
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) }
            ) { Text("Confirm") }
        }
    )
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}
