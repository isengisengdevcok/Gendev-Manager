package com.example.feature.apkstudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AppContainer
import com.example.core.common.HashUtils
import com.example.core.editor.EditorLanguage
import com.example.core.editor.SyntaxHighlighter
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.KeyValueRow
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import java.io.File

@Composable
fun ApkStudioScreen(
    container: AppContainer,
    viewModel: ApkStudioViewModel,
    onOpenDexStudio: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onAskAi: (label: String, content: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var buildMetaText by remember { mutableStateOf("Built with GENDEV MANAGER") }

    LaunchedEffect(Unit) {
        viewModel.consumeBusIfPresent()
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAndInspectUri(uri, "imported_${System.currentTimeMillis()}.apk")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Picker Bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.activeApk?.fileName ?: "APK Engineering Studio",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.activeApk?.manifest?.packageName?.ifEmpty { "Select an APK, APKS, XAPK, or AAB" }
                                ?: "Load installed app or APK file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.openInstalledAppsPicker(forCompare = false) },
                            modifier = Modifier.testTag("apk_pick_installed_btn")
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apps")
                        }
                        OutlinedButton(
                            onClick = { apkPickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("apk_pick_file_btn")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("File")
                        }
                    }
                }

                uiState.statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                            .clickable { viewModel.clearStatus() }
                    )
                }

                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        val apk = uiState.activeApk
        if (apk == null) {
            EmptyStatePanel(
                icon = Icons.Filled.Android,
                title = "No APK Loaded",
                message = "Choose an installed app via 'Apps' or pick an APK/XAPK/AAB file via 'File'.",
                modifier = Modifier.weight(1f)
            )
        } else {
            val tabs = listOf("Overview", "Manifest", "Permissions", "Certs", "Resources", "Build & Sign")
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            GenDevGlassCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Package Metadata", style = MaterialTheme.typography.titleMedium)
                                    StatusBadgePill(
                                        label = apk.signature.statusLabel,
                                        color = if (apk.signature.verifiedIntegrity) StatusSuccess else StatusWarning,
                                        icon = Icons.Filled.VerifiedUser
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                KeyValueRow("Package ID", apk.manifest?.packageName?.ifEmpty { "Unknown" } ?: "N/A")
                                KeyValueRow("Version", "${apk.manifest?.versionName ?: "?"} (${apk.manifest?.versionCode ?: "?"})")
                                KeyValueRow("SDK Range", "min ${apk.manifest?.minSdk?.ifEmpty { "?" } ?: "?"} → target ${apk.manifest?.targetSdk?.ifEmpty { "?" } ?: "?"}")
                                KeyValueRow("APK Size", HashUtils.formatBytes(apk.fileSizeBytes))
                                KeyValueRow("DEX Files", apk.dexFiles.joinToString().ifEmpty { "None" })
                                KeyValueRow("Native ABIs", apk.nativeAbis.joinToString().ifEmpty { "Pure Dalvik/ART" })
                                KeyValueRow("ZIP Entries", apk.totalEntries.toString())
                                KeyValueRow("SHA-256", apk.sha256)
                                KeyValueRow("MD5", apk.md5)
                                apk.bundleNote?.let { note ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(note, style = MaterialTheme.typography.bodySmall, color = StatusWarning)
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { onOpenDexStudio(apk.filePath) }) {
                                    Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DEX & Smali")
                                }
                                OutlinedButton(onClick = { viewModel.runStaticAudit() }) {
                                    Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Static Audit")
                                }
                                OutlinedButton(onClick = {
                                    val summary = apk.manifest?.decodedXml?.take(6000) ?: "APK ${apk.fileName}"
                                    onAskAi("APK: ${apk.fileName}", summary)
                                }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ask GENDEV AI")
                                }
                                OutlinedButton(onClick = { viewModel.backupActiveApk() }) {
                                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Backup")
                                }
                                OutlinedButton(onClick = { viewModel.installApk(File(apk.filePath)) }) {
                                    Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install")
                                }
                            }
                        }

                        uiState.auditMarkdown?.let { report ->
                            item {
                                GenDevGlassCard(accentBorder = CyanSecondary) {
                                    Text("Static Security Audit Report", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = report,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    viewModel.exportDecodedManifestToWorkspace()?.let { file ->
                                        onOpenEditor(file.absolutePath)
                                    }
                                }) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit Decoded XML")
                                }
                                OutlinedButton(onClick = {
                                    apk.manifest?.decodedXml?.let { xml ->
                                        onAskAi("Manifest: ${apk.fileName}", xml.take(8000))
                                    }
                                }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI Analyze")
                                }
                            }
                        }
                        item {
                            val xml = apk.manifest?.decodedXml ?: "Manifest could not be decoded."
                            val highlighted = SyntaxHighlighter.highlight(
                                xml,
                                EditorLanguage.XML,
                                MaterialTheme.colorScheme.onSurface
                            )
                            GenDevGlassCard {
                                Text(
                                    text = highlighted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }

                    2 -> {
                        val manifest = apk.manifest
                        if (manifest == null) {
                            item { Text("Manifest unavailable.") }
                        } else {
                            item {
                                Text(
                                    "Permissions (${manifest.permissions.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(manifest.permissions) { perm ->
                                val sensitive = perm.contains("INSTALL") || perm.contains("SYSTEM_ALERT") ||
                                    perm.contains("MANAGE_EXTERNAL") || perm.contains("SMS") || perm.contains("ACCESSIBILITY")
                                GenDevGlassCard(
                                    accentBorder = if (sensitive) StatusDanger.copy(alpha = 0.6f) else null
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = perm,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (sensitive) {
                                            StatusBadgePill("Sensitive", StatusDanger)
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Activities (${manifest.activities.size}) • Services (${manifest.services.size}) • Receivers (${manifest.receivers.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(manifest.activities + manifest.services + manifest.receivers + manifest.providers) { comp ->
                                GenDevGlassCard {
                                    KeyValueRow("Component", comp.name)
                                    KeyValueRow("Exported", comp.exported?.toString() ?: "implicit")
                                    comp.permission?.let { KeyValueRow("Guard Perm", it) }
                                }
                            }
                        }
                    }

                    3 -> {
                        item {
                            GenDevGlassCard {
                                KeyValueRow("Verification", apk.signature.statusLabel)
                                KeyValueRow("v1 JAR Cert", if (apk.signature.isSignedV1) "Present" else "Missing")
                                KeyValueRow("v2/v3 Sig Block", if (apk.signature.hasV2OrV3Block) "Present" else "Not Detected")
                            }
                        }
                        if (apk.signature.certificates.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Filled.VerifiedUser,
                                    title = "No v1 X.509 Certificate Block",
                                    message = "This APK either uses v2/v3-only signing or is unsigned."
                                )
                            }
                        } else {
                            items(apk.signature.certificates) { cert ->
                                GenDevGlassCard(accentBorder = EmeraldTertiary.copy(alpha = 0.5f)) {
                                    Text(cert.aliasFile, style = MaterialTheme.typography.titleSmall, color = EmeraldTertiary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    KeyValueRow("Subject", cert.subject)
                                    KeyValueRow("Issuer", cert.issuer)
                                    KeyValueRow("Serial", cert.serialNumber)
                                    KeyValueRow("Algorithm", cert.sigAlgName)
                                    KeyValueRow("Valid", "${cert.validFrom} -> ${cert.validUntil}")
                                    KeyValueRow("SHA-256", cert.sha256)
                                    KeyValueRow("SHA-1", cert.sha1)
                                    KeyValueRow("MD5", cert.md5)
                                }
                            }
                        }
                    }

                    4 -> {
                        apk.resourceTable?.let { arsc ->
                            item {
                                GenDevGlassCard {
                                    Text("resources.arsc Binary Table", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    KeyValueRow("Package Name", arsc.packageName)
                                    KeyValueRow("Package ID", "0x%02x".format(arsc.packageId))
                                    KeyValueRow("Global Strings", arsc.globalStringCount.toString())
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        arsc.resourceTypes.forEach { t ->
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text("${t.typeName} (${t.entryCount})") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("APK Archive Entries (${apk.totalEntries})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(apk.entries.filter { !it.isDirectory }.take(150)) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.path,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${HashUtils.formatBytes(entry.sizeBytes)} (compressed ${HashUtils.formatBytes(entry.compressedSizeBytes)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = "Extract ${entry.name}",
                                    tint = CyanSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable { viewModel.extractEntryToWorkspace(entry.path) }
                                )
                            }
                        }
                    }

                    5 -> {
                        item {
                            GenDevGlassCard {
                                Text("Rebuild, ZipAlign & Sign APK", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Rebuilds the APK archive, 4-byte aligns uncompressed entries (resources.arsc & .so), signs with GENDEV RSA-2048 X.509 certificate, and verifies integrity.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = buildMetaText,
                                    onValueChange = { buildMetaText = it },
                                    label = { Text("Inject assets/gendev_build_meta.txt (optional)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.rebuildAndSignActiveApk(buildMetaText) },
                                        modifier = Modifier.testTag("rebuild_sign_apk_btn")
                                    ) {
                                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rebuild & Sign")
                                    }
                                    OutlinedButton(onClick = { viewModel.openInstalledAppsPicker(forCompare = true) }) {
                                        Icon(Icons.Filled.Difference, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Compare APK")
                                    }
                                }
                            }
                        }

                        uiState.rebuildResult?.let { res ->
                            item {
                                GenDevGlassCard(accentBorder = StatusSuccess) {
                                    Text("Validated Signed Output", style = MaterialTheme.typography.titleMedium, color = StatusSuccess)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    KeyValueRow("Output File", res.outputApk.name)
                                    KeyValueRow("Signature", res.signatureStatus)
                                    KeyValueRow("Entries", res.totalEntries.toString())
                                    KeyValueRow("SHA-256", res.sha256)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { viewModel.installApk(res.outputApk) }) {
                                        Icon(Icons.Filled.InstallMobile, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Launch Android Installer")
                                    }
                                }
                            }
                        }

                        uiState.comparisonResult?.let { cmp ->
                            item {
                                GenDevGlassCard(accentBorder = CyanSecondary) {
                                    Text("APK Comparison: ${cmp.leftName} ↔ ${cmp.rightName}", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    KeyValueRow("Size Delta", "${if (cmp.sizeDeltaBytes >= 0) "+" else ""}${HashUtils.formatBytes(cmp.sizeDeltaBytes)}")
                                    KeyValueRow("DEX Count", "${cmp.dexCountLeft} → ${cmp.dexCountRight}")
                                    KeyValueRow("Entries Delta", "+${cmp.addedEntriesCount} / -${cmp.removedEntriesCount}")
                                    KeyValueRow("Added Perms", cmp.addedPermissions.joinToString().ifEmpty { "None" })
                                    KeyValueRow("Removed Perms", cmp.removedPermissions.joinToString().ifEmpty { "None" })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAppPicker || uiState.showComparePicker) {
        val isCompare = uiState.showComparePicker
        AlertDialog(
            onDismissRequest = { viewModel.dismissPickers() },
            title = { Text(if (isCompare) "Compare With Installed App" else "Select Installed App APK") },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(uiState.installedApps, key = { it.packageName }) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable {
                                    if (isCompare) {
                                        viewModel.compareWithApp(app)
                                    } else {
                                        viewModel.dismissPickers()
                                        viewModel.inspectApkPath(app.sourceApkPath)
                                    }
                                }
                                .padding(10.dp)
                        ) {
                            Text(app.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${app.packageName} • v${app.versionName} • ${HashUtils.formatBytes(app.apkSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPickers() }) { Text("Cancel") }
            }
        )
    }
}
