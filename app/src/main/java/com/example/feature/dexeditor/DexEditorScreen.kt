package com.example.feature.dexeditor

import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.apk.InstalledAppSummary
import com.example.core.common.AppContainer
import com.example.core.common.HashUtils
import com.example.core.dex.DexClassInfo
import com.example.core.dex.DexParsedFile
import com.example.core.dex.DexReferenceHit
import com.example.core.editor.EditorLanguage
import com.example.core.editor.SyntaxHighlighter
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.GenDevGlassCard
import com.example.core.ui.KeyValueRow
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.EmeraldTertiary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.VioletPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DexEditorScreen(
    container: AppContainer,
    onOpenSmaliInEditor: (String) -> Unit,
    onAskAi: (label: String, content: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var apkOrDexPath by remember { mutableStateOf<String?>(null) }
    var availableDexEntries by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeDexEntry by remember { mutableStateOf("classes.dex") }
    var dexFile by remember { mutableStateOf<DexParsedFile?>(null) }
    var selectedClass by remember { mutableStateOf<DexClassInfo?>(null) }
    var smaliCode by remember { mutableStateOf("") }
    var pseudoJavaCode by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var appClassesOnly by remember { mutableStateOf(false) }
    var referenceQuery by remember { mutableStateOf("") }
    var stringFilter by remember { mutableStateOf("") }
    var referenceHits by remember { mutableStateOf<List<DexReferenceHit>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    var showAppsPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<InstalledAppSummary>>(emptyList()) }

    var patchTargetStr by remember { mutableStateOf("") }
    var patchReplacementStr by remember { mutableStateOf("") }
    var lastPatchedDexFile by remember { mutableStateOf<File?>(null) }

    fun selectClassAndDisassemble(parsed: DexParsedFile, cls: DexClassInfo?) {
        selectedClass = cls
        if (cls == null) {
            smaliCode = ""
            pseudoJavaCode = ""
            return
        }
        scope.launch {
            val (smali, javaPreview) = withContext(Dispatchers.Default) {
                val s = container.dexParser.disassembleClassToSmali(parsed, cls)
                val j = container.dexParser.generatePseudoJavaPreview(parsed, cls)
                s to j
            }
            smaliCode = smali
            pseudoJavaCode = javaPreview
        }
    }

    fun loadTargetFile(path: String, dexEntry: String? = null) {
        scope.launch {
            isLoading = true
            statusMsg = null
            val targetFile = File(path)
            val entries = withContext(Dispatchers.IO) {
                if (targetFile.extension.equals("dex", ignoreCase = true)) {
                    listOf(targetFile.name)
                } else {
                    container.dexParser.listDexEntriesInApk(targetFile)
                }
            }
            val chosenEntry = dexEntry ?: entries.firstOrNull() ?: "classes.dex"
            val res = withContext(Dispatchers.IO) {
                container.dexParser.parseFromFile(targetFile, chosenEntry)
            }
            res.onSuccess { parsed ->
                apkOrDexPath = path
                availableDexEntries = entries
                activeDexEntry = chosenEntry
                dexFile = parsed
                val firstInteresting = parsed.classes.firstOrNull {
                    !it.className.startsWith("androidx.") && !it.className.startsWith("kotlin.") && !it.className.startsWith("kotlinx.")
                } ?: parsed.classes.firstOrNull()
                selectClassAndDisassemble(parsed, firstInteresting)
            }.onFailure { err ->
                statusMsg = "Gagal memuat DEX: ${err.localizedMessage}"
            }
            isLoading = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                val name = queryDexPickerName(context, uri) ?: "imported_${System.currentTimeMillis()}.apk"
                val destDir = File(container.storageRepository.workspaceDir, "Imported_DEX")
                container.storageRepository.importUriToDir(uri, destDir, name)
                    .onSuccess { imported -> loadTargetFile(imported.absolutePath) }
                    .onFailure {
                        isLoading = false
                        statusMsg = "Impor file gagal: ${it.localizedMessage}"
                    }
            }
        }
    }

    LaunchedEffect(Unit) {
        val pending = container.sharedBus.consumePendingDexApk()
        if (pending != null) {
            loadTargetFile(pending)
        } else {
            val sample = container.storageRepository.ensureSelfApkSample()
            if (sample != null && sample.exists()) {
                loadTargetFile(sample.absolutePath)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DEX & Smali Pro Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = apkOrDexPath?.let { "${File(it).name} • $activeDexEntry" } ?: "Pilih APK atau file .dex",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    installedApps = container.apkEngineeringEngine.loadInstalledApps(includeSystem = false)
                                    isLoading = false
                                    showAppsPicker = true
                                }
                            },
                            modifier = Modifier.testTag("dex_pick_app_btn")
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apps")
                        }
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("dex_pick_file_btn")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("File")
                        }
                    }
                }

                if (availableDexEntries.size > 1 && apkOrDexPath != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Multi-DEX:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        availableDexEntries.forEach { entryName ->
                            FilterChip(
                                selected = activeDexEntry == entryName,
                                onClick = { apkOrDexPath?.let { loadTargetFile(it, entryName) } },
                                label = { Text(entryName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                statusMsg?.let { msg ->
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
                            .clickable { statusMsg = null }
                    )
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        val parsed = dexFile
        if (parsed == null) {
            EmptyStatePanel(
                icon = Icons.Filled.Memory,
                title = "Belum Ada DEX Dimuat",
                message = "Ketuk 'Apps' untuk pilih aplikasi terpasang atau 'File' untuk membuka APK / file .dex langsung (100% gratis, Multi-DEX, Smali, Pseudo-Java & String Patcher).",
                modifier = Modifier.weight(1f)
            )
        } else {
            val tabs = listOf("Classes", "Smali", "Pseudo-Java", "XRefs & String", "Patcher & Info")
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(t, maxLines = 1) }
                    )
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
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Cari class, package, method (${parsed.header.classCount})") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("dex_class_search")
                            )
                            FilterChip(
                                selected = appClassesOnly,
                                onClick = { appClassesOnly = !appClassesOnly },
                                label = { Text("App Only", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val filtered = remember(parsed, searchQuery, appClassesOnly) {
                            parsed.classes.asSequence()
                                .filter { c ->
                                    if (!appClassesOnly) true
                                    else !c.className.startsWith("androidx.") &&
                                        !c.className.startsWith("android.support.") &&
                                        !c.className.startsWith("kotlin.") &&
                                        !c.className.startsWith("kotlinx.") &&
                                        !c.className.startsWith("com.google.")
                                }
                                .filter { c ->
                                    if (searchQuery.isBlank()) true
                                    else c.className.contains(searchQuery, ignoreCase = true) ||
                                        c.allMethods.any { it.name.contains(searchQuery, ignoreCase = true) }
                                }
                                .take(200)
                                .toList()
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.descriptor }) { cls ->
                                GenDevGlassCard(
                                    onClick = {
                                        selectClassAndDisassemble(parsed, cls)
                                        selectedTab = 1
                                    }
                                ) {
                                    Text(
                                        text = cls.className,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${cls.allMethods.size} method • ${cls.allFields.size} field" +
                                            (cls.sourceFile?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                        selectedClass?.let { cls ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(cls.className.substringAfterLast('.')) }
                                )
                                Button(onClick = {
                                    val dir = File(container.storageRepository.workspaceDir, "Smali_Patches").apply { mkdirs() }
                                    val safeName = cls.className.substringAfterLast('.').replace('$', '_')
                                    val out = File(dir, "$safeName.smali")
                                    out.writeText(smaliCode)
                                    statusMsg = "Tersimpan ${out.name} -> dibuka di Code Editor"
                                    onOpenSmaliInEditor(out.absolutePath)
                                }) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit Smali")
                                }
                                OutlinedButton(onClick = {
                                    clipboard.setText(AnnotatedString(smaliCode))
                                    statusMsg = "Smali ${cls.className.substringAfterLast('.')} disalin ke clipboard"
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salin")
                                }
                                OutlinedButton(onClick = {
                                    onAskAi("Smali: ${cls.className}", smaliCode.take(8000))
                                }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tanya AI")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val highlighted = remember(smaliCode) {
                                SyntaxHighlighter.highlight(
                                    smaliCode,
                                    EditorLanguage.SMALI,
                                    Color(0xFFE2E8F0)
                                )
                            }
                            GenDevGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = highlighted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        } ?: EmptyStatePanel(
                            icon = Icons.Filled.Code,
                            title = "Pilih Class",
                            message = "Ketuk salah satu class di tab Classes untuk melihat Smali.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                2 -> {
                    Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                        selectedClass?.let { cls ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("${cls.className.substringAfterLast('.')}.java (Preview)") }
                                )
                                Button(onClick = {
                                    val dir = File(container.storageRepository.workspaceDir, "Java_Preview").apply { mkdirs() }
                                    val safeName = cls.className.substringAfterLast('.').replace('$', '_')
                                    val out = File(dir, "$safeName.java")
                                    out.writeText(pseudoJavaCode)
                                    statusMsg = "Diekspor ${out.name} ke Code Editor"
                                    onOpenSmaliInEditor(out.absolutePath)
                                }) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buka di Editor")
                                }
                                OutlinedButton(onClick = {
                                    clipboard.setText(AnnotatedString(pseudoJavaCode))
                                    statusMsg = "Pseudo-Java disalin ke clipboard"
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salin")
                                }
                                OutlinedButton(onClick = {
                                    onAskAi("Decompile/Audit: ${cls.className}", pseudoJavaCode.take(8000))
                                }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI Analisis")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val highlightedJava = remember(pseudoJavaCode) {
                                SyntaxHighlighter.highlight(
                                    pseudoJavaCode,
                                    EditorLanguage.detect("Preview.java"),
                                    Color(0xFFE2E8F0)
                                )
                            }
                            GenDevGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = highlightedJava,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        } ?: EmptyStatePanel(
                            icon = Icons.Filled.Code,
                            title = "Pilih Class",
                            message = "Pilih class dari tab Classes untuk dekompilasi Pseudo-Java.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                3 -> {
                    Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = referenceQuery,
                                onValueChange = { referenceQuery = it },
                                label = { Text("Cari XRef: simbol, URL, string, pemanggilan method") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                scope.launch {
                                    isLoading = true
                                    referenceHits = withContext(Dispatchers.Default) {
                                        container.dexParser.findReferences(parsed, referenceQuery)
                                    }
                                    isLoading = false
                                }
                            }) {
                                Icon(Icons.Filled.FindInPage, contentDescription = "Cari XRefs")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (referenceHits.isNotEmpty()) {
                            Text("Hasil XRefs (${referenceHits.size}):", style = MaterialTheme.typography.labelLarge, color = CyanSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(referenceHits) { hit ->
                                GenDevGlassCard(
                                    onClick = {
                                        val cls = parsed.classes.firstOrNull { it.className == hit.className }
                                        if (cls != null) {
                                            selectClassAndDisassemble(parsed, cls)
                                            selectedTab = 1
                                        }
                                    }
                                ) {
                                    Text(hit.className, style = MaterialTheme.typography.titleSmall, color = CyanSecondary)
                                    Text(hit.methodName, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        hit.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = stringFilter,
                                    onValueChange = { stringFilter = it },
                                    label = { Text("Filter String Pool (${parsed.strings.size}) — ketuk string untuk patch") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            val poolItems = parsed.strings.filter {
                                it.length in 2..120 && (stringFilter.isBlank() || it.contains(stringFilter, ignoreCase = true))
                            }.take(120)
                            items(poolItems) { s ->
                                Text(
                                    text = s,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            patchTargetStr = s
                                            patchReplacementStr = s
                                            selectedTab = 4
                                            statusMsg = "String dipilih untuk patch: $s"
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                4 -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            GenDevGlassCard(accentBorder = VioletPrimary.copy(alpha = 0.6f)) {
                                Text("Binary DEX String Patcher (SHA-1 & Adler32 Auto-Fix)", style = MaterialTheme.typography.titleMedium, color = CyanSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Patch string langsung pada binary ${parsed.dexName} dengan recalculation otomatis checksum Adler32 & signature SHA-1 agar tetap valid di Android ART.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = patchTargetStr,
                                    onValueChange = { patchTargetStr = it },
                                    label = { Text("String target (harus persis ada di DEX)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("dex_patch_target_input")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = patchReplacementStr,
                                    onValueChange = { patchReplacementStr = it },
                                    label = { Text("String pengganti (panjang <= target)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("dex_patch_replacement_input")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = patchTargetStr.isNotEmpty() && apkOrDexPath != null,
                                        onClick = {
                                            val srcPath = apkOrDexPath ?: return@Button
                                            scope.launch {
                                                isLoading = true
                                                val res = withContext(Dispatchers.IO) {
                                                    val srcFile = File(srcPath)
                                                    container.dexParser.extractDexBytes(srcFile, activeDexEntry).fold(
                                                        onSuccess = { rawDex ->
                                                            container.dexParser.patchStringInDexBytes(rawDex, patchTargetStr, patchReplacementStr)
                                                                .mapCatching { patchedBytes ->
                                                                    val outDir = File(container.storageRepository.workspaceDir, "Patched_DEX").apply { mkdirs() }
                                                                    val outDex = File(outDir, "${srcFile.nameWithoutExtension}_${activeDexEntry.substringBeforeLast('.')}_patched.dex")
                                                                    outDex.writeBytes(patchedBytes)
                                                                    outDex
                                                                }
                                                        },
                                                        onFailure = { Result.failure(it) }
                                                    )
                                                }
                                                res.onSuccess { outDex ->
                                                    lastPatchedDexFile = outDex
                                                    statusMsg = "Berhasil patch DEX & recalculate SHA-1/Adler32 -> ${outDex.name}"
                                                }.onFailure { err ->
                                                    statusMsg = "Patch gagal: ${err.localizedMessage}"
                                                }
                                                isLoading = false
                                            }
                                        },
                                        modifier = Modifier.testTag("dex_patch_apply_btn")
                                    ) {
                                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Patch & Simpan DEX")
                                    }
                                    lastPatchedDexFile?.let { patchedFile ->
                                        OutlinedButton(onClick = { loadTargetFile(patchedFile.absolutePath) }) {
                                            Text("Muat Hasil")
                                        }
                                    }
                                }
                                lastPatchedDexFile?.let { patchedFile ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tersimpan: ${patchedFile.absolutePath}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusSuccess
                                    )
                                }
                            }
                        }

                        item {
                            GenDevGlassCard {
                                Text("DEX Header Telemetry", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                KeyValueRow("Entry", parsed.dexName)
                                KeyValueRow("Magic", parsed.header.magic)
                                KeyValueRow("File Size", HashUtils.formatBytes(parsed.header.fileSize.toLong()))
                                KeyValueRow("String Pool", parsed.header.stringCount.toString())
                                KeyValueRow("Type IDs", parsed.header.typeCount.toString())
                                KeyValueRow("Proto IDs", parsed.header.protoCount.toString())
                                KeyValueRow("Field IDs", parsed.header.fieldCount.toString())
                                KeyValueRow("Method IDs", parsed.header.methodCount.toString())
                                KeyValueRow("Class Defs", parsed.header.classCount.toString())
                                KeyValueRow("Multi-DEX", availableDexEntries.joinToString().ifEmpty { parsed.dexName })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppsPicker) {
        AlertDialog(
            onDismissRequest = { showAppsPicker = false },
            title = { Text("Pilih Aplikasi untuk DEX Analysis") },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(installedApps, key = { it.packageName }) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable {
                                    showAppsPicker = false
                                    loadTargetFile(app.sourceApkPath)
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
                TextButton(onClick = { showAppsPicker = false }) { Text("Batal") }
            }
        )
    }
}

private fun queryDexPickerName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}
