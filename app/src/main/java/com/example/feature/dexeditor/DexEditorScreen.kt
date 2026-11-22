package com.example.feature.dexeditor

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.core.ui.theme.JetBrainsMonoFontFamily
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
    var apkPath by remember { mutableStateOf<String?>(null) }
    var dexFile by remember { mutableStateOf<DexParsedFile?>(null) }
    var selectedClass by remember { mutableStateOf<DexClassInfo?>(null) }
    var smaliCode by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var referenceQuery by remember { mutableStateOf("") }
    var referenceHits by remember { mutableStateOf<List<DexReferenceHit>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    fun loadApkDex(path: String, dexEntry: String = "classes.dex") {
        scope.launch {
            isLoading = true
            statusMsg = null
            val res = withContext(Dispatchers.IO) {
                container.dexParser.parseFromApk(File(path), dexEntry)
            }
            res.onSuccess { parsed ->
                apkPath = path
                dexFile = parsed
                val firstInteresting = parsed.classes.firstOrNull {
                    !it.className.startsWith("androidx.") && !it.className.startsWith("kotlin.")
                } ?: parsed.classes.firstOrNull()
                selectedClass = firstInteresting
                smaliCode = if (firstInteresting != null) {
                    withContext(Dispatchers.Default) {
                        container.dexParser.disassembleClassToSmali(parsed, firstInteresting)
                    }
                } else ""
            }.onFailure { err ->
                statusMsg = "DEX parse failed: ${err.localizedMessage}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val pending = container.sharedBus.consumePendingDexApk()
        val targetPath = pending ?: File(container.storageRepository.workspaceDir, "Samples/GENDEV_Manager_self.apk")
            .takeIf { it.exists() }?.absolutePath
        if (targetPath != null) {
            loadApkDex(targetPath)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DEX & Smali Engineering Studio", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = apkPath?.let { File(it).name } ?: "No APK/DEX loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanSecondary
                        )
                    }
                    dexFile?.let { d ->
                        Text(
                            text = "${d.header.classCount} classes",
                            style = MaterialTheme.typography.labelMedium,
                            color = VioletPrimary
                        )
                    }
                }
                statusMsg?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = CyanSecondary)
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
                title = "No DEX Loaded",
                message = "Open an APK in APK Studio and tap 'DEX & Smali' to inspect classes, methods, fields, and disassembled bytecode.",
                modifier = Modifier.weight(1f)
            )
        } else {
            val tabs = listOf("Classes", "Smali", "XRefs", "DEX Info")
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
                }
            }

            when (selectedTab) {
                0 -> {
                    Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Filter classes, packages, methods...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("dex_class_search")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val filtered = remember(parsed, searchQuery) {
                            if (searchQuery.isBlank()) parsed.classes.take(150)
                            else parsed.classes.filter { c ->
                                c.className.contains(searchQuery, ignoreCase = true) ||
                                    c.allMethods.any { it.name.contains(searchQuery, ignoreCase = true) }
                            }.take(150)
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.descriptor }) { cls ->
                                GenDevGlassCard(
                                    onClick = {
                                        selectedClass = cls
                                        smaliCode = container.dexParser.disassembleClassToSmali(parsed, cls)
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
                                        text = "${cls.allMethods.size} methods • ${cls.allFields.size} fields",
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
                                    val out = File(dir, "${cls.className.substringAfterLast('.')}.smali")
                                    out.writeText(smaliCode)
                                    statusMsg = "Saved ${out.name} -> Opening in Code Editor"
                                    onOpenSmaliInEditor(out.absolutePath)
                                }) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit Smali")
                                }
                                OutlinedButton(onClick = {
                                    onAskAi("Smali: ${cls.className}", smaliCode.take(8000))
                                }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Explain with AI")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val highlighted = remember(smaliCode) {
                                SyntaxHighlighter.highlight(
                                    smaliCode,
                                    EditorLanguage.SMALI,
                                    androidx.compose.ui.graphics.Color(0xFFE2E8F0)
                                )
                            }
                            GenDevGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = highlighted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
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
                            OutlinedTextField(
                                value = referenceQuery,
                                onValueChange = { referenceQuery = it },
                                label = { Text("Find symbol, string, or method call") },
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
                                Icon(Icons.Filled.FindInPage, contentDescription = "Find XRefs")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(referenceHits) { hit ->
                                GenDevGlassCard(
                                    onClick = {
                                        val cls = parsed.classes.firstOrNull { it.className == hit.className }
                                        if (cls != null) {
                                            selectedClass = cls
                                            smaliCode = container.dexParser.disassembleClassToSmali(parsed, cls)
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
                            }
                        }
                        item {
                            Text("Sample String Pool (${parsed.strings.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(parsed.strings.filter { it.length in 3..80 }.take(60)) { s ->
                            Text(
                                text = s,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
