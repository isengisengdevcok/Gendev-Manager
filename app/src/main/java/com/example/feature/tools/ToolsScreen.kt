package com.example.feature.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.apk.InstalledAppSummary
import com.example.core.common.AppContainer
import com.example.core.common.AppLogLevel
import com.example.core.common.AppLogStore
import com.example.core.common.HashUtils
import com.example.core.device.BatteryDetails
import com.example.core.device.DeviceEnvironmentInfo
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.KeyValueRow
import com.example.core.ui.StatusBadgePill
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusDanger
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun ToolsScreen(
    container: AppContainer,
    onInspectApk: (String) -> Unit,
    onAskAi: (label: String, content: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledAppSummary>>(emptyList()) }
    var includeSystemApps by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var envInfo by remember { mutableStateOf<DeviceEnvironmentInfo?>(null) }
    var battery by remember { mutableStateOf<BatteryDetails?>(null) }
    var logcatLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var logFilter by remember { mutableStateOf("ALL") }
    var showAppSourceLogs by remember { mutableStateOf(true) }
    val appSourceLogs by AppLogStore.logs.collectAsStateWithLifecycle()
    var networkSummary by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun loadApps() {
        scope.launch {
            isBusy = true
            apps = container.apkEngineeringEngine.loadInstalledApps(includeSystem = includeSystemApps)
            isBusy = false
        }
    }

    fun loadLogcat() {
        scope.launch {
            isBusy = true
            val res = container.rootShellManager.execute("logcat -d -t 150")
            logcatLines = if (res.stdout.isNotBlank()) {
                res.stdout.lines()
            } else {
                listOf("Logcat returned empty or restricted by device SELinux policy (${res.stderr})")
            }
            isBusy = false
        }
    }

    fun loadSystemAndNetwork() {
        scope.launch {
            withContext(Dispatchers.IO) {
                envInfo = container.deviceEnvironmentManager.inspectEnvironment()
                battery = container.deviceEnvironmentManager.readBatteryDetails()
                networkSummary = readNetworkDiagnostics(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
        loadSystemAndNetwork()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("Android Engineering Tools", style = MaterialTheme.typography.titleLarge)
                statusMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = CyanSecondary)
                }
                if (isBusy) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        val tabs = listOf("Apps & Backup", "Device & Battery", "Logcat Viewer", "Network")
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = selectedTab == i, onClick = {
                    selectedTab = i
                    if (i == 2 && logcatLines.isEmpty()) loadLogcat()
                    if (i == 1 || i == 3) loadSystemAndNetwork()
                }, text = { Text(t) })
            }
        }

        when (selectedTab) {
            0 -> {
                Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = appSearch,
                            onValueChange = { appSearch = it },
                            placeholder = { Text("Search installed packages...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("tools_app_search")
                        )
                        FilterChip(
                            selected = includeSystemApps,
                            onClick = { includeSystemApps = !includeSystemApps; loadApps() },
                            label = { Text("System") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = remember(apps, appSearch) {
                        if (appSearch.isBlank()) apps
                        else apps.filter {
                            it.appName.contains(appSearch, ignoreCase = true) ||
                                it.packageName.contains(appSearch, ignoreCase = true)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            GenDevGlassCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            app.appName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${app.packageName} • v${app.versionName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${HashUtils.formatBytes(app.apkSizeBytes)} • targetSdk ${app.targetSdk}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanSecondary
                                        )
                                    }
                                    if (app.isSystemApp) {
                                        StatusBadgePill("System", StatusWarning)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onInspectApk(app.sourceApkPath) }) {
                                        Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("APK Studio")
                                    }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            container.apkEngineeringEngine.backupInstalledApk(
                                                app,
                                                container.storageRepository.backupsDir
                                            ).onSuccess {
                                                statusMessage = "Backed up ${it.name} to Backups/"
                                            }.onFailure {
                                                statusMessage = "Backup failed: ${it.localizedMessage}"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Backup APK")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    envInfo?.let { info ->
                        item {
                            GenDevGlassCard {
                                Text("Hardware & Compatibility", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                KeyValueRow("Manufacturer", info.manufacturer)
                                KeyValueRow("Brand / Model", "${info.brand} ${info.model}")
                                KeyValueRow("Device Codename", info.deviceName)
                                KeyValueRow("Android OS", "${info.androidVersion} (API ${info.sdkLevel})")
                                KeyValueRow("Security Patch", info.securityPatch)
                                KeyValueRow("CPU ABIs", info.supportedAbis.joinToString())
                                KeyValueRow("Root Engine", info.rootImplementation.displayName)
                                KeyValueRow("su Path", info.suBinaryPath ?: "Not Found")
                                KeyValueRow("Shizuku", if (info.shizukuInstalled) "Installed" else "Not Installed")
                                KeyValueRow("All Files Access", if (info.allFilesAccessGranted) "Granted" else "Scoped / SAF")
                            }
                        }
                        item {
                            GenDevGlassCard {
                                Text("Supported Operations", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                info.supportedOperations.forEach { op ->
                                    Text("• $op", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    battery?.let { b ->
                        item {
                            GenDevGlassCard {
                                Text("Battery Telemetry", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                KeyValueRow("Charge Level", "${b.levelPercent}% (${if (b.isCharging) "Charging" else "Discharging"})")
                                KeyValueRow("Temperature", "${b.temperatureCelsius} °C")
                                KeyValueRow("Voltage", "${b.voltageMv} mV")
                                KeyValueRow("Chemistry", b.technology)
                            }
                        }
                    }
                }
            }

            2 -> {
                Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showAppSourceLogs,
                            onClick = { showAppSourceLogs = true },
                            label = { Text("Source Log (${appSourceLogs.size})") }
                        )
                        FilterChip(
                            selected = !showAppSourceLogs,
                            onClick = {
                                showAppSourceLogs = false
                                if (logcatLines.isEmpty()) loadLogcat()
                            },
                            label = { Text("System Logcat") }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("ALL", "E/", "W/", "I/").forEach { lvl ->
                            FilterChip(
                                selected = logFilter == lvl,
                                onClick = { logFilter = lvl },
                                label = { Text(lvl.trimEnd('/')) }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (showAppSourceLogs) {
                            OutlinedButton(onClick = { AppLogStore.clearLogs() }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear App Source Logs", modifier = Modifier.size(16.dp))
                            }
                        } else {
                            OutlinedButton(onClick = { loadLogcat() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Logcat", modifier = Modifier.size(16.dp))
                            }
                        }
                        Button(onClick = {
                            if (showAppSourceLogs) {
                                onAskAi("App Source Log Diagnostic", AppLogStore.buildAiDiagnosticSnapshot())
                            } else {
                                val snippet = logcatLines.takeLast(80).joinToString("\n")
                                onAskAi("Device Logcat Dump", snippet)
                            }
                        }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showAppSourceLogs) {
                        val shownApp = remember(appSourceLogs, logFilter) {
                            appSourceLogs.filter { entry ->
                                when (logFilter) {
                                    "E/" -> entry.level == AppLogLevel.ERROR || entry.level == AppLogLevel.CRASH
                                    "W/" -> entry.level == AppLogLevel.WARN
                                    "I/" -> entry.level == AppLogLevel.INFO
                                    else -> true
                                }
                            }.reversed()
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF060911))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(shownApp, key = { it.id }) { entry ->
                                val c = when (entry.level) {
                                    AppLogLevel.CRASH, AppLogLevel.ERROR -> StatusDanger
                                    AppLogLevel.WARN -> StatusWarning
                                    AppLogLevel.INFO -> EmeraldTertiary
                                }
                                Column {
                                    Text(
                                        text = "[${entry.formattedTime}] [${entry.level.tag}] [${entry.source}] ${entry.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = c
                                    )
                                    entry.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
                                        Text(
                                            text = stack.take(350),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val shown = remember(logcatLines, logFilter) {
                            if (logFilter == "ALL") logcatLines
                            else logcatLines.filter { it.contains(" $logFilter") }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF060911))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(shown) { line ->
                                val c = when {
                                    line.contains(" E/") || line.contains("FATAL") -> StatusDanger
                                    line.contains(" W/") -> StatusWarning
                                    line.contains(" I/") -> EmeraldTertiary
                                    else -> androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                                }
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = c
                                )
                            }
                        }
                    }
                }
            }

            3 -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        GenDevGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Network Diagnostics", style = MaterialTheme.typography.titleMedium)
                                StatusBadgePill(
                                    label = if (networkSummary.any { it.first == "Connectivity" && it.second != "Offline" }) "Online" else "Offline",
                                    color = StatusSuccess
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            networkSummary.forEach { (k, v) ->
                                KeyValueRow(k, v)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readNetworkDiagnostics(context: Context): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val active = cm?.activeNetwork
    val caps = cm?.getNetworkCapabilities(active)
    val transport = when {
        caps == null -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Other Active Network"
    }
    out.add("Connectivity" to transport)
    out.add("Validated Internet" to (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true).toString())

    val ips = mutableListOf<String>()
    runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val nif = interfaces.nextElement()
            if (!nif.isUp || nif.isLoopback) continue
            val addrs = nif.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address) {
                    ips.add("${nif.name}: ${addr.hostAddress}")
                }
            }
        }
    }
    out.add("Local IPv4 Interfaces" to (ips.joinToString(", ").ifEmpty { "None" }))
    return out
}
