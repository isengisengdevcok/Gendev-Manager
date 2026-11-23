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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePaneView(
    pane: PaneState,
    isActive: Boolean,
    isRootSide: Boolean,
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
    val accent = if (isRootSide && rootExplorerEnabled) StatusWarning else CyanSecondary
    val borderColor = if (isActive) {
        accent.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(if (isActive) 1.5.dp else 0.75.dp, borderColor), RoundedCornerShape(10.dp))
            .clickable(onClick = onActivate),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Pane Header: Role badge + Multi-Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isActive) accent.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isRootSide) "ROOT" else "BIASA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                pane.tabs.forEachIndexed { idx, t ->
                    val selected = idx == pane.activeTabIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .clickable { onActivate(); onSelectTab(idx) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (pane.tabs.size > 1) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close Tab",
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onCloseTab(idx) }
                            )
                        }
                    }
                }
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New Tab",
                    tint = accent,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .clickable { onActivate(); onAddTab() }
                        .padding(2.dp)
                        .testTag("add_tab_pane_${pane.paneIndex}")
                )
            }

            // Compact Path + GoToPath + Search + Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            color = if (tab.currentPath == "/") accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onActivate(); onNavigateTo("/") }
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        )
                    }
                    val segments = tab.currentPath.split('/').filter { it.isNotEmpty() }
                    var acc = ""
                    segments.forEachIndexed { idx, seg ->
                        acc += "/$seg"
                        val target = acc
                        Text(
                            text = if (idx == 0) seg else "/$seg",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (idx == segments.lastIndex) accent else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .size(28.dp)
                        .testTag("goto_path_pane_${pane.paneIndex}")
                ) {
                    Icon(Icons.Filled.EditLocationAlt, contentDescription = "Go to Path", modifier = Modifier.size(15.dp))
                }
                IconButton(
                    onClick = { onActivate(); showSearchBar = !showSearchBar },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("search_pane_${pane.paneIndex}")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search Pane", modifier = Modifier.size(15.dp))
                }
                Box {
                    Text(
                        text = tab.sortBy.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onActivate(); sortMenuExpanded = true }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
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

            // Compact Folder / File Counter Line (matches clear density)
            Text(
                text = "Folder: ${tab.folderCount}  File: ${tab.fileCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            if (showSearchBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = tab.searchQuery,
                        onValueChange = { onSearchChange(it, tab.recursiveSearch) },
                        placeholder = { Text("Cari...", style = MaterialTheme.typography.labelSmall) },
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

            val isRootRestrictedPath = !rootExplorerEnabled && (
                tab.currentPath == "/" ||
                    tab.currentPath.startsWith("/data") ||
                    tab.currentPath.startsWith("/system") ||
                    tab.currentPath.startsWith("/vendor") ||
                    tab.currentPath.startsWith("/mnt") ||
                    tab.currentPath.startsWith("/etc") ||
                    tab.currentPath.startsWith("/acct") ||
                    tab.currentPath.startsWith("/apex")
                )
            val isPhoneStorageBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !hasAllFilesAccess &&
                tab.currentPath.startsWith("/storage/emulated")

            // File List or Actionable State
            if (tab.items.isEmpty() || isRootRestrictedPath) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmptyStatePanel(
                        icon = if (isRootRestrictedPath) Icons.Filled.Security else Icons.Filled.Folder,
                        title = when {
                            isPhoneStorageBlocked -> "Penyimpanan HP Terkunci"
                            isRootRestrictedPath && rootAvailable -> "Butuh Izin Root (su)"
                            isRootRestrictedPath -> "Perangkat Non-Root"
                            else -> "Folder Kosong"
                        },
                        message = when {
                            isPhoneStorageBlocked -> "Izinkan Akses Semua File agar isi /storage/emulated/0 tampil."
                            isRootRestrictedPath && rootAvailable -> "Ketuk tombol di bawah untuk meminta izin Root (su) asli."
                            isRootRestrictedPath -> "su tidak terdeteksi. Gunakan Folder Biasa / Workspace / SAF."
                            else -> "Tidak ada file di sini."
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isPhoneStorageBlocked) {
                        FilledTonalButton(
                            onClick = onRequestPhoneUnlock,
                            modifier = Modifier.testTag("empty_unlock_phone_btn_${pane.paneIndex}")
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Izin File", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (isRootRestrictedPath) {
                        FilledTonalButton(
                            onClick = onRequestRootEnable,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = StatusWarning.copy(alpha = 0.22f),
                                contentColor = StatusWarning
                            ),
                            modifier = Modifier.testTag("empty_enable_root_btn_${pane.paneIndex}")
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (rootAvailable) "Aktifkan Root (su)" else "Coba Izin Root (su)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (!rootAvailable) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(onClick = onOpenPhoneOrWorkspace) {
                                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Buka Workspace", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        OutlinedButton(onClick = onOpenPhoneOrWorkspace) {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Workspace", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (tab.currentPath != "/") {
                        item(key = "__parent__") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onActivate(); onNavigateUp() }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "Parent Directory",
                                    tint = CyanSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "..",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.4.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                            )
                        }
                    }

                    items(tab.items, key = { it.path }) { item ->
                        val isSelected = item.path in tab.selectedPaths
                        var rowMenu by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        onActivate()
                                        if (tab.selectedPaths.isNotEmpty()) {
                                            onToggleSelect(item.path)
                                        } else {
                                            onOpenItem(item)
                                        }
                                    },
                                    onLongClick = {
                                        onActivate()
                                        onToggleSelect(item.path)
                                    }
                                )
                                .padding(horizontal = 7.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.CheckCircle else categoryIcon(item.category),
                                    contentDescription = item.category.name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else categoryColor(item.category),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(7.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val dateText = if (item.lastModified > 0L) {
                                    formatCompactTimestamp(item.lastModified)
                                } else {
                                    item.permissionsString
                                }
                                val subText = if (item.isDirectory) {
                                    if (item.isRootItem) dateText else "$dateText • ${item.childCount} item"
                                } else {
                                    "${HashUtils.formatBytes(item.sizeBytes)} • $dateText"
                                }
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (item.symlinkTarget != null) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Symlink -> ${item.symlinkTarget}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .padding(end = 2.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { onActivate(); rowMenu = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("item_menu_${item.name}")
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Item Actions",
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (isSelected) "Batal Pilih" else "Pilih Item") },
                                        leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                        onClick = { rowMenu = false; onToggleSelect(item.path) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Salin ke Panel Sebelah") },
                                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                        onClick = { rowMenu = false; onCopyToOtherPane(item) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Pindah ke Panel Sebelah") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                        onClick = { rowMenu = false; onMoveToOtherPane(item) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Properti & SHA-256") },
                                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                        onClick = { rowMenu = false; onProperties(item) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ubah Nama") },
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
                                            text = { Text("Buka di Code/Hex Editor") },
                                            leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null) },
                                            onClick = { rowMenu = false; onOpenInEditor(item) }
                                        )
                                    }
                                    if (item.category == FileCategory.APK_BUNDLE || item.category == FileCategory.ARCHIVE) {
                                        DropdownMenuItem(
                                            text = { Text("Isi Arsip / ZIP") },
                                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                            onClick = { rowMenu = false; onInspectArchive(item) }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Hapus", color = StatusDanger) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = StatusDanger) },
                                        onClick = { rowMenu = false; onDelete(item) }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.4.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatCompactTimestamp(epochMs: Long): String {
    return runCatching {
        SimpleDateFormat("yy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
    }.getOrDefault("")
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
