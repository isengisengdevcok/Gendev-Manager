package com.example.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.AppContainer
import com.example.core.common.AppLogLevel
import com.example.core.common.AppLogStore
import com.example.core.common.HashUtils
import com.example.core.device.DeviceEnvironmentInfo
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.QuickActionTile
import com.example.core.ui.SectionHeaderRow
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusInfo
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigateFiles: () -> Unit,
    onOpenPhoneDirectory: (String) -> Unit,
    onNavigateApkStudio: () -> Unit,
    onNavigateDexStudio: () -> Unit,
    onNavigateEditor: () -> Unit,
    onNavigateTerminal: () -> Unit,
    onNavigateAi: () -> Unit,
    onNavigateTools: () -> Unit
) {
    // Immediate zero-latency preview so cold start lands instantaneously on Home
    var envInfo by remember {
        mutableStateOf(container.deviceEnvironmentManager.inspectFastPreview())
    }
    val recentFiles by container.database.recentFileDao().observeRecent().collectAsStateWithLifecycle(emptyList())
    val recentApks by container.database.recentFileDao().observeRecentApks().collectAsStateWithLifecycle(emptyList())
    val buildStatus by container.sharedBus.lastBuildStatus.collectAsStateWithLifecycle()
    val appLogs by AppLogStore.logs.collectAsStateWithLifecycle()
    val errorCount = remember(appLogs) {
        appLogs.count { it.level == AppLogLevel.ERROR || it.level == AppLogLevel.CRASH }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            container.storageRepository.ensureWorkspaceInitialized()
            envInfo = container.deviceEnvironmentManager.inspectEnvironment()
        }
    }

    val phoneRootPath = remember {
        container.storageRepository.getPhoneStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .testTag("home_dashboard_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroBannerCard(
                    envInfo = envInfo,
                    buildStatus = buildStatus,
                    errorCount = errorCount,
                    onOpenAi = onNavigateAi,
                    onOpenFiles = onNavigateFiles
                )
            }

            item {
                PhoneDirectoryStorageCard(
                    info = envInfo,
                    phonePath = phoneRootPath,
                    onOpenPhoneDir = { onOpenPhoneDirectory(phoneRootPath) },
                    onInspectTools = onNavigateTools
                )
            }

            item {
                SectionHeaderRow(
                    title = "Engineering Suite",
                    subtitle = "Universal Android Studio & Dual-Pane Control"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "Dual-Pane Files",
                            subtitle = "Biasa | Root (su) & ZIP",
                            icon = Icons.Filled.FolderCopy,
                            accent = CyanSecondary,
                            onClick = onNavigateFiles,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
                                .fillMaxHeight()
                                .testTag("quick_apk_btn")
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "DEX & Smali",
                            subtitle = "Multi-DEX, Patch & Disasm",
                            icon = Icons.Filled.Memory,
                            accent = VioletPrimary,
                            onClick = onNavigateDexStudio,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("quick_dex_btn")
                        )
                        QuickActionTile(
                            title = "Code & Hex",
                            subtitle = "HTML, XML, JSON & Bin",
                            icon = Icons.Filled.Code,
                            accent = StatusWarning,
                            onClick = onNavigateEditor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("quick_editor_btn")
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "Root Terminal",
                            subtitle = "sh / su Live Shell",
                            icon = Icons.Filled.Terminal,
                            accent = StatusInfo,
                            onClick = onNavigateTerminal,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("quick_terminal_btn")
                        )
                        QuickActionTile(
                            title = "GENDEV AI",
                            subtitle = "Gemini Key & Source Log",
                            icon = Icons.Filled.AutoAwesome,
                            accent = VioletPrimary,
                            onClick = onNavigateAi,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("quick_ai_btn")
                        )
                    }
                }
            }

            item {
                SectionHeaderRow(
                    title = "Recent APKs",
                    subtitle = "Tap to inspect Manifest, Certificates & DEX",
                    actionLabel = "APK Studio",
                    onAction = onNavigateApkStudio
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (recentApks.isEmpty()) {
                    GenDevGlassCard(
                        accentBorder = EmeraldTertiary.copy(alpha = 0.35f),
                        onClick = {
                            val sampleApk = File(container.storageRepository.workspaceDir, "Samples/GENDEV_Manager_self.apk")
                            if (sampleApk.exists()) {
                                container.sharedBus.openApkInStudio(sampleApk.absolutePath)
                            }
                            onNavigateApkStudio()
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldTertiary.copy(alpha = 0.14f))
                                    .border(1.dp, EmeraldTertiary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Android,
                                    contentDescription = "Sample APK",
                                    tint = EmeraldTertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Inspect Self APK or Installed Apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ketuk untuk membuka APK Studio (self APK atau aplikasi terpasang).",
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
                                    .width(224.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        container.sharedBus.openApkInStudio(apk.path)
                                        onNavigateApkStudio()
                                    },
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                border = BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.32f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(EmeraldTertiary.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Android,
                                                contentDescription = null,
                                                tint = EmeraldTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = apk.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(6.dp))
                if (recentFiles.isEmpty()) {
                    GenDevGlassCard {
                        EmptyStatePanel(
                            icon = Icons.Filled.FolderCopy,
                            title = "No Recent Files Yet",
                            message = "Buka file dari Dual-Pane File Manager atau Direktori Phone."
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentFiles.take(5).forEach { entry ->
                            val isApk = entry.name.endsWith(".apk", ignoreCase = true)
                            val iconTint = if (isApk) EmeraldTertiary else CyanSecondary
                            GenDevGlassCard(
                                accentBorder = iconTint.copy(alpha = 0.25f),
                                onClick = {
                                    if (isApk) {
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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(iconTint.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isApk) Icons.Filled.Android else Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.path,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
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
}

@Composable
private fun HeroBannerCard(
    envInfo: DeviceEnvironmentInfo,
    buildStatus: String,
    errorCount: Int,
    onOpenAi: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val cardShape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        CyanSecondary.copy(alpha = 0.50f),
                        VioletPrimary.copy(alpha = 0.55f)
                    )
                ),
                shape = cardShape
            )
            .background(Color(0xFF070B16))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_gendev_hero),
            contentDescription = "GENDEV Manager Engineering Hero Banner",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x88070B16),
                            Color(0xCC070B16),
                            Color(0xF0070B16)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Brand Row: Modern GENDEV Logo + Title + Environment Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF070E20),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(listOf(CyanSecondary, VioletPrimary))
                    )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_launcher_fg),
                        contentDescription = "GENDEV Official Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .clip(RoundedCornerShape(11.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GENDEV MANAGER",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "APK • DEX/Smali • Root Studio",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusBadgePill(
                    label = if (envInfo.rootAvailable) envInfo.rootImplementation.displayName else "Non-Root",
                    color = if (envInfo.rootAvailable) StatusSuccess else CyanSecondary,
                    icon = if (envInfo.rootAvailable) Icons.Filled.Security else Icons.Filled.Shield
                )
            }

            // Live Telemetry Pill Strip (horizontally scrollable so pills never collide or wrap unevenly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadgePill(
                    label = buildStatus,
                    color = StatusInfo,
                    icon = Icons.Filled.Build
                )
                if (envInfo.shizukuInstalled) {
                    StatusBadgePill(label = "Shizuku Ready", color = EmeraldTertiary)
                }
                if (errorCount > 0) {
                    StatusBadgePill(
                        label = "$errorCount Source Log",
                        color = StatusDanger,
                        icon = Icons.Filled.BugReport,
                        modifier = Modifier.clickable(onClick = onOpenAi)
                    )
                }
            }

            // Equal-height Primary CTA Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .clickable(onClick = onOpenFiles),
                    color = CyanSecondary.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, CyanSecondary.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.FolderCopy,
                            contentDescription = null,
                            tint = CyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dual Files",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .clickable(onClick = onOpenAi),
                    color = VioletPrimary.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.60f)),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFE9D5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GENDEV AI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneDirectoryStorageCard(
    info: DeviceEnvironmentInfo,
    phonePath: String,
    onOpenPhoneDir: () -> Unit,
    onInspectTools: () -> Unit
) {
    val usedPct = (info.internalStorage.usedFraction.coerceIn(0f, 1f) * 100f).toInt()
    GenDevGlassCard(
        accentBorder = CyanSecondary.copy(alpha = 0.42f),
        onClick = onOpenPhoneDir
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanSecondary.copy(alpha = 0.14f))
                        .border(1.dp, CyanSecondary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = "Direktori Phone",
                        tint = CyanSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Direktori Phone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = phonePath,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadgePill(
                label = "$usedPct%",
                color = CyanSecondary,
                icon = Icons.AutoMirrored.Filled.OpenInNew
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { info.internalStorage.usedFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .clickable(onClick = onOpenPhoneDir)
                .testTag("phone_directory_storage_bar"),
            color = CyanSecondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3 Equal-width Metric Boxes (replaces uneven multi-line KeyValueRow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StorageStatMiniBox(
                label = "Terpakai",
                value = HashUtils.formatBytes(info.internalStorage.usedBytes),
                valueColor = CyanSecondary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StorageStatMiniBox(
                label = "Bebas",
                value = HashUtils.formatBytes(info.internalStorage.freeBytes),
                valueColor = EmeraldTertiary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StorageStatMiniBox(
                label = "Total",
                value = HashUtils.formatBytes(info.internalStorage.totalBytes),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${info.manufacturer} ${info.model} • Android ${info.androidVersion} (${info.primaryArchitecture})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onInspectTools,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Detail Device", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StorageStatMiniBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
