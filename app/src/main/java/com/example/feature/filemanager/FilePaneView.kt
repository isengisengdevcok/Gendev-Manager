package com.example.feature.filemanager

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.HashUtils
import com.example.core.storage.FileCategory
import com.example.core.storage.FileItem
import com.example.core.storage.FilterCategory
import com.example.core.storage.SortBy
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePaneView(
    pane: PaneState,
    isActive: Boolean,
    rootExplorerEnabled: Boolean,
    rootAvailable: Boolean,
    hasAllFilesAccess: Boolean,
    onActivate: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onOpenGoToPath: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    onSearchChange: (String, Boolean) -> Unit,
    onSortChange: (SortBy) -> Unit,
    onFilterChange: (FilterCategory) -> Unit,
    onToggleSelect: (String) -> Unit,
    onOpenItem: (FileItem) -> Unit,
    onCopyToOtherPane: (FileItem) -> Unit,
    onMoveToOtherPane: (FileItem) -> Unit,
    onProperties: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onChmod: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onInspectArchive: (FileItem) -> Unit,
    onOpenInEditor: (FileItem) -> Unit,
    onRequestPhoneUnlock: () -> Unit,
    onRequestRootEnable: () -> Unit,
    onOpenPhoneOrWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tab = pane.activeTab
    var showSearchBar by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val borderColor = if (isActive) {
        if (rootExplorerEnabled) StatusWarning.copy(alpha = 0.78f) else CyanSecondary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

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
                            .clickable { onActivate(); onSelectTab(idx) }
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
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onCloseTab(idx) }
                            )
                        }
                    }
                }
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New Tab",
                    tint = CyanSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onActivate(); onAddTab() }
                        .padding(2.dp)
                        .testTag("add_tab_pane_${pane.paneIndex}")
                )
            }

            // Breadcrumb + Pane Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onActivate(); onNavigateUp() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("up_pane_${pane.paneIndex}")
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Parent Folder", modifier = Modifier.size(18.dp))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tab.currentPath.startsWith("/")) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tab.currentPath == "/") CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onActivate(); onNavigateTo("/") }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    val segments = tab.currentPath.split('/').filter { it.isNotEmpty() }
                    var acc = ""
                    segments.forEachIndexed { idx, seg ->
                        acc += "/$seg"
                        val target = acc
                        Text(
                            text = if (idx == 0) seg else " / $seg",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (idx == segments.lastIndex) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onActivate(); onNavigateTo(target) }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onActivate(); onOpenGoToPath() },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("goto_path_pane_${pane.paneIndex}")
                ) {
                    Icon(Icons.Filled.EditLocationAlt, contentDescription = "Go to Path", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { showSearchBar = !showSearchBar },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("search_pane_${pane.paneIndex}")
                ) {
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
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("sort_pane_${pane.paneIndex}")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
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

            // File List or Actionable Empty State
            if (tab.items.isEmpty()) {
                val isRootRestrictedPath = !rootExplorerEnabled && (
                    tab.currentPath == "/" ||
                        tab.currentPath.startsWith("/data") ||
                        tab.currentPath.startsWith("/system") ||
                        tab.currentPath.startsWith("/vendor")
                    )
                val isPhoneStorageBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !hasAllFilesAccess &&
                    tab.currentPath.startsWith("/storage/emulated")

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmptyStatePanel(
                        icon = if (isRootRestrictedPath) Icons.Filled.Security else Icons.Filled.Folder,
                        title = when {
                            isPhoneStorageBlocked -> "Phone Storage Locked"
                            isRootRestrictedPath -> "Root Explorer Required"
                            else -> "Empty Directory"
                        },
                        message = when {
                            isPhoneStorageBlocked -> "Grant All Files Access to browse /storage/emulated/0 directly."
                            isRootRestrictedPath && rootAvailable -> "Enable Root Explorer (su) in the bar above to browse ${tab.currentPath}."
                            isRootRestrictedPath -> "This device is Non-Root. System paths require su; use Phone Storage or Workspace."
                            else -> "No matching files here. Tap + to create or use Quick Roots above."
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPhoneStorageBlocked) {
                            FilledTonalButton(
                                onClick = onRequestPhoneUnlock,
                                modifier = Modifier.testTag("empty_unlock_phone_btn")
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unlock Phone Storage")
                            }
                        } else if (isRootRestrictedPath && rootAvailable) {
                            FilledTonalButton(
                                onClick = onRequestRootEnable,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = StatusWarning.copy(alpha = 0.2f),
                                    contentColor = StatusWarning
                                ),
                                modifier = Modifier.testTag("empty_enable_root_btn")
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enable Root (su)")
                            }
                        } else {
                            OutlinedButton(onClick = onOpenPhoneOrWorkspace) {
                                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Phone / Workspace")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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
                                .combinedClickable(
                                    onClick = {
                                        onActivate()
                                        onOpenItem(item)
                                    },
                                    onLongClick = {
                                        onActivate()
                                        onToggleSelect(item.path)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    onActivate()
                                    onToggleSelect(item.path)
                                },
                                modifier = Modifier.size(34.dp)
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
                                    text = if (item.symlinkTarget != null) "${item.name} -> ${item.symlinkTarget}" else item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val detailText = if (item.isDirectory) {
                                    val countPart = if (item.isRootItem) "Root dir" else "${item.childCount} items"
                                    "$countPart • ${item.permissionsString}"
                                } else {
                                    val timePart = if (item.lastModified > 0L) {
                                        HashUtils.formatTimestamp(item.lastModified)
                                    } else {
                                        item.permissionsString
                                    }
                                    "${HashUtils.formatBytes(item.sizeBytes)} • $timePart"
                                }
                                Text(
                                    text = detailText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { onActivate(); rowMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("item_menu_${item.name}")
                                ) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Item Actions", modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Copy to Other Pane") },
                                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                        onClick = { rowMenu = false; onCopyToOtherPane(item) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to Other Pane") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                        onClick = { rowMenu = false; onMoveToOtherPane(item) }
                                    )
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
                                    if (rootExplorerEnabled) {
                                        DropdownMenuItem(
                                            text = { Text("Permissions (chmod)") },
                                            leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null) },
                                            onClick = { rowMenu = false; onChmod(item) }
                                        )
                                    }
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
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = StatusDanger) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = StatusDanger) },
                                        onClick = { rowMenu = false; onDelete(item) }
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
