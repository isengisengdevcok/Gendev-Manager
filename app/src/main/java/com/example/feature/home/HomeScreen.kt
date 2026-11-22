package com.example.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.AppContainer
import com.example.core.common.HashUtils
import com.example.core.device.DeviceEnvironmentInfo
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.KeyValueRow
import com.example.core.ui.QuickActionTile
import com.example.core.ui.SectionHeaderRow
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.StatusInfo
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigateFiles: () -> Unit,
    onNavigateApkStudio: () -> Unit,
    onNavigateDexStudio: () -> Unit,
    onNavigateEditor: () -> Unit,
    onNavigateTerminal: () -> Unit,
    onNavigateAi: () -> Unit,
    onNavigateTools: () -> Unit
) {
    var envInfo by remember { mutableStateOf<DeviceEnvironmentInfo?>(null) }
    val recentFiles by container.database.recentFileDao().observeRecent().collectAsStateWithLifecycle(emptyList())
    val recentApks by container.database.recentFileDao().observeRecentApks().collectAsStateWithLifecycle(emptyList())
    val buildStatus by container.sharedBus.lastBuildStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            container.storageRepository.ensureWorkspaceInitialized()
            envInfo = container.deviceEnvironmentManager.inspectEnvironment()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroBannerCard(
                envInfo = envInfo,
                buildStatus = buildStatus,
                onOpenAi = onNavigateAi,
                onOpenFiles = onNavigateFiles
            )
        }

        item {
            SectionHeaderRow(
                title = "Engineering Suite",
                subtitle = "Universal Android Studio & Dual-Pane Control"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        title = "Dual-Pane Files",
                        subtitle = "Tabs, SAF, ZIP & Diff",
                        icon = Icons.Filled.FolderCopy,
                        accent = CyanSecondary,
                        onClick = onNavigateFiles,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_files_btn")
                    )
                    QuickActionTile(
                        title = "APK Studio",
                        subtitle = "Manifest, Sign & Build",
                        icon = Icons.Filled.Android,
                        accent = EmeraldTertiary,
                        onClick = onNavigateApkStudio,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_apk_btn")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        title = "DEX & Smali",
                        subtitle = "Disassemble & XRefs",
                        icon = Icons.Filled.Memory,
                        accent = VioletPrimary,
                        onClick = onNavigateDexStudio,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_dex_btn")
                    )
                    QuickActionTile(
                        title = "Code & Hex",
                        subtitle = "XML, JSON, Smali, Bin",
                        icon = Icons.Filled.Code,
                        accent = StatusWarning,
                        onClick = onNavigateEditor,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_editor_btn")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        title = "Root Terminal",
                        subtitle = "sh / su Live Shell",
                        icon = Icons.Filled.Terminal,
                        accent = StatusInfo,
                        onClick = onNavigateTerminal,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_terminal_btn")
                    )
                    QuickActionTile(
                        title = "GENDEV AI",
                        subtitle = "Hybrid Gemini + Local",
                        icon = Icons.Filled.AutoAwesome,
                        accent = VioletPrimary,
                        onClick = onNavigateAi,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_ai_btn")
                    )
                }
            }
        }

        item {
            envInfo?.let { info ->
                StorageAndDeviceCard(info = info, onInspectTools = onNavigateTools)
            }
        }

        item {
            SectionHeaderRow(
                title = "Recent APKs",
                subtitle = "Tap to inspect Manifest, Certificates & DEX",
                actionLabel = "APK Studio",
                onAction = onNavigateApkStudio
            )
            if (recentApks.isEmpty()) {
                GenDevGlassCard(
                    onClick = {
                        val sampleApk = java.io.File(container.storageRepository.workspaceDir, "Samples/GENDEV_Manager_self.apk")
                        if (sampleApk.exists()) {
                            container.sharedBus.openApkInStudio(sampleApk.absolutePath)
                        }
                        onNavigateApkStudio()
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Android,
                            contentDescription = "Sample APK",
                            tint = EmeraldTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Inspect Self APK or Installed Apps",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Samples/GENDEV_Manager_self.apk is pre-staged in Workspace for instant AXML & DEX engineering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentApks, key = { it.path }) { apk ->
                        Surface(
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    container.sharedBus.openApkInStudio(apk.path)
                                    onNavigateApkStudio()
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Android, contentDescription = null, tint = EmeraldTertiary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = apk.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = HashUtils.formatTimestamp(apk.openedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeaderRow(
                title = "Recent Files",
                actionLabel = "Browse",
                onAction = onNavigateFiles
            )
            if (recentFiles.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Filled.FolderCopy,
                    title = "No Recent Files Yet",
                    message = "Open files from the Dual-Pane File Manager or Workspace Samples to see them here."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentFiles.take(5).forEach { entry ->
                        GenDevGlassCard(
                            onClick = {
                                if (entry.name.endsWith(".apk", ignoreCase = true)) {
                                    container.sharedBus.openApkInStudio(entry.path)
                                    onNavigateApkStudio()
                                } else {
                                    container.sharedBus.openFileInEditor(entry.path)
                                    onNavigateEditor()
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = HashUtils.formatTimestamp(entry.openedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBannerCard(
    envInfo: DeviceEnvironmentInfo?,
    buildStatus: String,
    onOpenAi: () -> Unit,
    onOpenFiles: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_gendev_hero),
            contentDescription = "GENDEV Manager Engineering Hero Banner",
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66090C16),
                            Color(0xCC090C16),
                            Color(0xF2090C16)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GENDEV MANAGER",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "APK & DEX Engineering Studio • Free & Open",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                    StatusBadgePill(
                        label = if (envInfo?.rootAvailable == true) envInfo.rootImplementation.displayName else "Non-Root Safe",
                        color = if (envInfo?.rootAvailable == true) StatusSuccess else CyanSecondary,
                        icon = if (envInfo?.rootAvailable == true) Icons.Filled.Security else Icons.Filled.Shield
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadgePill(
                        label = buildStatus,
                        color = StatusInfo,
                        icon = Icons.Filled.Build
                    )
                    if (envInfo?.shizukuInstalled == true) {
                        StatusBadgePill(label = "Shizuku", color = EmeraldTertiary)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenFiles),
                        color = CyanSecondary.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.FolderCopy, contentDescription = null, tint = CyanSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dual Files", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenAi),
                        color = VioletPrimary.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GENDEV AI", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageAndDeviceCard(
    info: DeviceEnvironmentInfo,
    onInspectTools: () -> Unit
) {
    GenDevGlassCard(onClick = onInspectTools) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storage, contentDescription = "Storage", tint = CyanSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Device & Storage Matrix", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = info.primaryArchitecture,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { info.internalStorage.usedFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = CyanSecondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        KeyValueRow(
            label = "Internal Storage",
            value = "${HashUtils.formatBytes(info.internalStorage.usedBytes)} / ${HashUtils.formatBytes(info.internalStorage.totalBytes)} (${HashUtils.formatBytes(info.internalStorage.freeBytes)} free)"
        )
        KeyValueRow(
            label = "Device / OS",
            value = "${info.manufacturer} ${info.model} • Android ${info.androidVersion} (API ${info.sdkLevel})"
        )
        KeyValueRow(
            label = "ABIs Supported",
            value = info.supportedAbis.joinToString(", ")
        )
        KeyValueRow(
            label = "Memory (RAM)",
            value = "${HashUtils.formatBytes(info.availableRamBytes)} avail / ${HashUtils.formatBytes(info.totalRamBytes)}"
        )
    }
}
