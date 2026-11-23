package com.example.feature.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.common.HashUtils
import com.example.core.storage.ArchiveEntryItem
import com.example.core.storage.DiffLineType
import com.example.core.storage.DuplicateGroup
import com.example.core.storage.FileDiffLine
import com.example.core.storage.FileItem
import com.example.core.storage.StorageCategoryStat
import com.example.core.ui.KeyValueRow
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import java.io.File

@Composable
fun TextInputDialog(
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_input_dialog_field")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                modifier = Modifier.testTag("text_input_dialog_confirm")
            ) { Text("Confirm") }
        }
    )
}

@Composable
fun GoToPathDialog(
    initialPath: String,
    nonRootPresets: List<Pair<String, String>>,
    rootPresets: List<Pair<String, String>>,
    rootExplorerEnabled: Boolean,
    rootAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pathText by remember { mutableStateOf(initialPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Directory Path") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Absolute path") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goto_path_input")
                )
                Text(
                    text = "Non-Root Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    nonRootPresets.forEach { (label, target) ->
                        FilterChip(
                            selected = pathText == target,
                            onClick = { pathText = target },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    text = if (rootExplorerEnabled) {
                        "Root Explorer Presets (su active)"
                    } else if (rootAvailable) {
                        "Root Presets (enable Root toggle first)"
                    } else {
                        "System paths (su required on rooted devices)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rootExplorerEnabled) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rootPresets.forEach { (label, target) ->
                        FilterChip(
                            selected = pathText == target,
                            onClick = { pathText = target },
                            enabled = rootExplorerEnabled,
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { if (pathText.isNotBlank()) onConfirm(pathText.trim()) },
                modifier = Modifier.testTag("goto_path_confirm")
            ) { Text("Open") }
        }
    )
}

@Composable
fun ChmodDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var mode by remember { mutableStateOf("644") }
    val valid = mode.trim().matches(Regex("^[0-7]{3,4}$"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions (chmod)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${item.name} • current: ${item.permissionsString}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = mode,
                    onValueChange = { if (it.length <= 4) mode = it },
                    label = { Text("Octal mode (e.g. 644, 755)") },
                    singleLine = true,
                    isError = !valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chmod_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "644" to "644 (rw-r--r--)",
                        "755" to "755 (rwxr-xr-x)",
                        "600" to "600 (rw-------)",
                        "777" to "777 (rwxrwxrwx)"
                    ).forEach { (octal, desc) ->
                        FilterChip(
                            selected = mode == octal,
                            onClick = { mode = octal },
                            label = { Text(desc, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(mode.trim()) },
                modifier = Modifier.testTag("chmod_confirm")
            ) { Text("Apply") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    targetNames: List<String>,
    rootModeActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (targetNames.size == 1) "Delete ${targetNames.first()}?" else "Delete ${targetNames.size} items?",
                color = StatusDanger
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rootModeActive) {
                    Text(
                        text = "Root Explorer (su) is active. Root deletions run directly on the filesystem and cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Files up to 20 MB are backed up to Workspace/Backups before removal. Folders will be removed recursively.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                targetNames.take(5).forEach { name ->
                    Text(
                        text = "• $name",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (targetNames.size > 5) {
                    Text(
                        text = "...and ${targetNames.size - 5} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete_btn")
            ) {
                Text("Delete", color = StatusDanger)
            }
        }
    )
}

@Composable
fun RootModeDialog(
    rootAvailable: Boolean,
    rootImplLabel: String,
    suBinaryPath: String?,
    onDismiss: () -> Unit,
    onConfirmEnable: () -> Unit,
    onOpenPhoneStorageOrUnlock: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (rootAvailable) "Enable Root Explorer (su)?" else "Device Running in Non-Root Mode"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rootAvailable) {
                    Text(
                        text = "Detected $rootImplLabel (${suBinaryPath ?: "su"}). Enabling Root Explorer grants access to /, /data, /system, /vendor, and chmod operations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Warning: Modifying system partitions or critical Android directories can cause bootloops or app crashes. Only proceed if you understand the risk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "No su binary (Magisk, KernelSU, APatch) was detected on this device. GENDEV MANAGER never fakes root access.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "You can still browse Phone Storage (/storage/emulated/0), Download, Documents, App Workspace, and import any file or APK via SAF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            if (!rootAvailable) {
                TextButton(onClick = {
                    onDismiss()
                    onOpenPhoneStorageOrUnlock()
                }) {
                    Text("Phone Storage")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmEnable,
                modifier = Modifier.testTag("confirm_enable_root_btn")
            ) {
                Text(
                    text = if (rootAvailable) "Authorize Root (su)" else "Coba Izin Root (su)",
                    color = StatusWarning
                )
            }
        }
    )
}

@Composable
fun PropertiesDialog(
    props: FilePropertiesModalData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(props.item.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                KeyValueRow("Path", props.item.path)
                props.item.symlinkTarget?.let { target ->
                    KeyValueRow("Symlink ->", target)
                }
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ImagePreviewDialog(
    imgFile: File,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ArchiveInspectorDialog(
    archive: File,
    entries: List<ArchiveEntryItem>,
    onDismiss: () -> Unit,
    onExtractAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive: ${archive.name} (${entries.size})") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(entries.take(200)) { entry ->
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        confirmButton = {
            TextButton(onClick = onExtractAll) {
                Text("Extract All")
            }
        }
    )
}

@Composable
fun DuplicatesDialog(
    groups: List<DuplicateGroup>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Scanner (${groups.size} groups)") },
        text = {
            if (groups.isEmpty()) {
                Text("No duplicate files found in this directory tree.")
            } else {
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                Text(
                                    text = f.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun StorageStatsDialog(
    stats: List<StorageCategoryStat>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun DiffDialog(
    title: String,
    lines: List<FileDiffLine>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
