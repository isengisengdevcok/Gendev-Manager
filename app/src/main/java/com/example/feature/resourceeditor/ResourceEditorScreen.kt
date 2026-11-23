package com.example.feature.resourceeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.AppContainer
import com.example.core.data.EditorDraftEntity
import com.example.core.editor.EditorLanguage
import com.example.core.editor.EncodingDetector
import com.example.core.editor.HexDumpFormatter
import com.example.core.editor.SyntaxHighlighter
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.theme.CyanSecondary
import com.example.core.ui.theme.JetBrainsMonoFontFamily
import com.example.core.ui.theme.StatusSuccess
import com.example.core.ui.theme.StatusWarning
import com.example.core.ui.theme.VioletPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OpenEditorTab(
    val filePath: String,
    val fileName: String,
    val language: EditorLanguage,
    val encodingLabel: String,
    val textContent: String,
    val rawBytes: ByteArray,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val isDirty: Boolean = false,
    val isReadOnly: Boolean = false,
    val isHexMode: Boolean = false,
    val isLargeFileTruncated: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenEditorTab) return false
        return filePath == other.filePath &&
            fileName == other.fileName &&
            language == other.language &&
            encodingLabel == other.encodingLabel &&
            textContent == other.textContent &&
            rawBytes.contentEquals(other.rawBytes) &&
            undoStack == other.undoStack &&
            redoStack == other.redoStack &&
            isDirty == other.isDirty &&
            isReadOnly == other.isReadOnly &&
            isHexMode == other.isHexMode &&
            isLargeFileTruncated == other.isLargeFileTruncated
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + textContent.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        result = 31 * result + isHexMode.hashCode()
        return result
    }
}

private class SyntaxHighlightVisualTransformation(
    private val language: EditorLanguage,
    private val baseColor: Color,
    private val searchQuery: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = runCatching {
            SyntaxHighlighter.highlight(raw, language, baseColor, searchQuery)
        }.getOrElse { AnnotatedString(raw) }
        val safeAnnotated = if (highlighted.length == raw.length) {
            highlighted
        } else {
            AnnotatedString(raw)
        }
        return TransformedText(safeAnnotated, OffsetMapping.Identity)
    }
}

@Composable
fun ResourceEditorScreen(
    container: AppContainer,
    onAskAi: (label: String, content: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val tabs = remember { mutableStateListOf<OpenEditorTab>() }
    var activeTabIndex by remember { mutableIntStateOf(0) }

    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var codeFolded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var hexEditTargetOffset by remember { mutableStateOf<Int?>(null) }

    suspend fun loadFileIntoTab(path: String): OpenEditorTab? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) return@withContext null
            val maxRead = 250_000L
            val truncated = file.length() > maxRead
            val bytes = file.inputStream().use { inp ->
                val targetLen = minOf(file.length().coerceAtLeast(0L), maxRead).toInt()
                val buf = ByteArray(targetLen)
                var readTotal = 0
                while (readTotal < buf.size) {
                    val r = inp.read(buf, readTotal, buf.size - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                if (readTotal < buf.size) buf.copyOf(readTotal) else buf
            }
            val lang = EditorLanguage.detect(file.name, bytes)
            val (encLabel, charset) = EncodingDetector.detectEncoding(bytes)
            val draft = runCatching { container.database.editorDraftDao().getDraft(file.absolutePath) }.getOrNull()
            val text = draft?.content ?: if (lang == EditorLanguage.HEX) "" else String(bytes, charset)
            OpenEditorTab(
                filePath = file.absolutePath,
                fileName = file.name,
                language = lang,
                encodingLabel = encLabel,
                textContent = text,
                rawBytes = bytes,
                isDirty = draft != null,
                isHexMode = lang == EditorLanguage.HEX,
                isLargeFileTruncated = truncated
            )
        }.onFailure { err ->
            statusMessage = "Gagal membuka file: ${err.localizedMessage ?: "Izin/IO error"}"
        }.getOrNull()
    }

    fun openFile(path: String) {
        val existingIdx = tabs.indexOfFirst { it.filePath == path }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            return
        }
        scope.launch {
            val loaded = loadFileIntoTab(path) ?: return@launch
            if (tabs.none { it.filePath == loaded.filePath }) {
                tabs.add(loaded)
                activeTabIndex = tabs.lastIndex
                if (loaded.isDirty) {
                    statusMessage = "Draft auto-save dipulihkan untuk ${loaded.fileName}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { container.storageRepository.ensureWorkspaceInitialized() }
        val pending = container.sharedBus.consumePendingEditorFile()
        if (pending != null) {
            openFile(pending)
        } else if (tabs.isEmpty()) {
            val samplePaths = listOf(
                File(container.storageRepository.workspaceDir, "Samples/AndroidManifest_sample.xml"),
                File(container.storageRepository.workspaceDir, "Samples/build_profile.json"),
                File(container.storageRepository.workspaceDir, "Samples/DemoHook.smali")
            ).filter { it.exists() }
            for (sample in samplePaths) {
                if (tabs.none { it.filePath == sample.absolutePath }) {
                    loadFileIntoTab(sample.absolutePath)?.let { tabs.add(it) }
                }
            }
            if (tabs.isNotEmpty()) activeTabIndex = 0
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val destDir = File(container.storageRepository.workspaceDir, "Editor_Imports")
                container.storageRepository.importUriToDir(uri, destDir, "doc_${System.currentTimeMillis()}.txt")
                    .onSuccess { openFile(it.absolutePath) }
                    .onFailure { statusMessage = "Gagal impor file: ${it.localizedMessage}" }
            }
        }
    }

    fun updateActiveTab(transform: (OpenEditorTab) -> OpenEditorTab) {
        if (activeTabIndex in tabs.indices) {
            val updated = transform(tabs[activeTabIndex])
            tabs[activeTabIndex] = updated
            if (!updated.isHexMode && updated.isDirty) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        container.database.editorDraftDao().saveDraft(
                            EditorDraftEntity(
                                filePath = updated.filePath,
                                content = updated.textContent,
                                languageName = updated.language.name
                            )
                        )
                    }
                }
            }
        }
    }

    fun saveActiveTab() {
        val tab = tabs.getOrNull(activeTabIndex) ?: return
        if (tab.isReadOnly) {
            statusMessage = "Mode Read-Only aktif."
            return
        }
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(tab.filePath)
                    if (file.exists()) {
                        val bkp = File(container.storageRepository.backupsDir, "edit_${System.currentTimeMillis()}_${file.name}.bak")
                        runCatching { file.copyTo(bkp, overwrite = true) }
                    }
                    if (tab.isHexMode) {
                        file.writeBytes(tab.rawBytes)
                    } else {
                        file.writeText(tab.textContent)
                    }
                    runCatching { container.database.editorDraftDao().clearDraft(tab.filePath) }
                }
            }
            res.onSuccess {
                if (activeTabIndex in tabs.indices) {
                    tabs[activeTabIndex] = tabs[activeTabIndex].copy(isDirty = false)
                }
                statusMessage = "Tersimpan: ${tab.fileName} (backup tersimpan)"
            }.onFailure {
                statusMessage = "Gagal menyimpan: ${it.localizedMessage}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val current = tabs.getOrNull(activeTabIndex)
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = "Open File")
                    }
                    IconButton(
                        onClick = { saveActiveTab() },
                        enabled = current != null && !current.isReadOnly,
                        modifier = Modifier.testTag("editor_save_btn")
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Save File",
                            tint = if (current?.isDirty == true) StatusWarning else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            if (current != null && current.undoStack.isNotEmpty() && !current.isReadOnly) {
                                val prev = current.undoStack.last()
                                updateActiveTab {
                                    it.copy(
                                        textContent = prev,
                                        undoStack = it.undoStack.dropLast(1),
                                        redoStack = it.redoStack + it.textContent,
                                        isDirty = true
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = {
                            if (current != null && current.redoStack.isNotEmpty() && !current.isReadOnly) {
                                val next = current.redoStack.last()
                                updateActiveTab {
                                    it.copy(
                                        textContent = next,
                                        undoStack = it.undoStack + it.textContent,
                                        redoStack = it.redoStack.dropLast(1),
                                        isDirty = true
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { showFindReplace = !showFindReplace }) {
                        Icon(Icons.Filled.FindReplace, contentDescription = "Find & Replace")
                    }
                    IconButton(onClick = { codeFolded = !codeFolded }) {
                        Icon(
                            if (codeFolded) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                            contentDescription = "Toggle Code Folding"
                        )
                    }
                    if (current != null) {
                        IconButton(onClick = { updateActiveTab { it.copy(isReadOnly = !it.isReadOnly) } }) {
                            Icon(
                                if (current.isReadOnly) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = "Toggle Read-Only",
                                tint = if (current.isReadOnly) StatusWarning else StatusSuccess
                            )
                        }
                        IconButton(
                            onClick = {
                                updateActiveTab { tab ->
                                    val switchingToHex = !tab.isHexMode
                                    val syncedBytes = if (switchingToHex && tab.rawBytes.isEmpty() && tab.textContent.isNotEmpty()) {
                                        tab.textContent.toByteArray()
                                    } else if (switchingToHex && !tab.isHexMode && tab.isDirty) {
                                        tab.textContent.toByteArray()
                                    } else {
                                        tab.rawBytes
                                    }
                                    tab.copy(isHexMode = switchingToHex, rawBytes = syncedBytes)
                                }
                            },
                            modifier = Modifier.testTag("editor_hex_toggle_btn")
                        ) {
                            Icon(
                                Icons.Filled.Hexagon,
                                contentDescription = "Toggle Hex Mode",
                                tint = if (current.isHexMode) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            val snippet = if (current.isHexMode) {
                                HexDumpFormatter.toRows(current.rawBytes, maxBytes = 512)
                                    .joinToString("\n") { r ->
                                        "%06X: %s  %s".format(
                                            r.offset,
                                            r.bytes.joinToString(" ") { HexDumpFormatter.formatByteHex(it) },
                                            HexDumpFormatter.formatAsciiRow(r.bytes)
                                        )
                                    }
                            } else {
                                current.textContent.take(8000)
                            }
                            onAskAi("Editor: ${current.fileName}", snippet)
                        }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Explain with AI", tint = VioletPrimary)
                        }
                    }
                }

                if (tabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tabs.forEachIndexed { idx, t ->
                            val selected = idx == activeTabIndex
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { activeTabIndex = idx }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${t.fileName}${if (t.isDirty) " •" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) CyanSecondary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close ${t.fileName}",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            if (idx in tabs.indices) {
                                                tabs.removeAt(idx)
                                                activeTabIndex = activeTabIndex.coerceAtMost((tabs.size - 1).coerceAtLeast(0))
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                if (showFindReplace) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            label = { Text("Find") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            label = { Text("Replace") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val cur = tabs.getOrNull(activeTabIndex)
                            if (cur != null && findQuery.isNotEmpty() && !cur.isReadOnly && !cur.isHexMode) {
                                val next = cur.textContent.replace(findQuery, replaceQuery, ignoreCase = false)
                                updateActiveTab {
                                    it.copy(
                                        undoStack = (it.undoStack + it.textContent).takeLast(30),
                                        redoStack = emptyList(),
                                        textContent = next,
                                        isDirty = true
                                    )
                                }
                                statusMessage = "Diganti kemunculan '$findQuery'"
                            }
                        }) {
                            Text("All")
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary,
                        modifier = Modifier.padding(top = 4.dp).clickable { statusMessage = null }
                    )
                }
            }
        }

        val activeTab = tabs.getOrNull(activeTabIndex)
        if (activeTab == null) {
            EmptyStatePanel(
                icon = Icons.Filled.Code,
                title = "Belum Ada File Terbuka",
                message = "Buka XML, JSON, Smali, Shell, HTML/CSS/JS, Markdown, atau Binary Hex dari File Manager atau ikon folder di atas.",
                modifier = Modifier.weight(1f)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (activeTab.isHexMode) "HEX" else activeTab.language.label} • ${activeTab.encodingLabel}${if (activeTab.isReadOnly) " • READ-ONLY" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (activeTab.isHexMode) "${activeTab.rawBytes.size} bytes"
                    else "${activeTab.textContent.lines().size} baris",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanSecondary
                )
            }

            if (activeTab.isHexMode) {
                val rows = remember(activeTab.rawBytes) {
                    HexDumpFormatter.toRows(activeTab.rawBytes)
                }
                if (rows.isEmpty()) {
                    EmptyStatePanel(
                        icon = Icons.Filled.Hexagon,
                        title = "File Binary Kosong (0 bytes)",
                        message = "Tidak ada byte untuk ditampilkan pada mode Hex.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF070A12))
                            .padding(8.dp)
                            .testTag("hex_viewer_list")
                    ) {
                        items(rows, key = { it.offset }) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "%06X".format(row.offset),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = VioletPrimary,
                                    modifier = Modifier.width(62.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    row.bytes.forEachIndexed { idx, b ->
                                        val absOff = row.offset + idx
                                        Text(
                                            text = HexDumpFormatter.formatByteHex(b),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = CyanSecondary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .clickable(enabled = !activeTab.isReadOnly) {
                                                    hexEditTargetOffset = absOff
                                                }
                                                .padding(horizontal = 2.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = HexDumpFormatter.formatAsciiRow(row.bytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            } else {
                val displayText = remember(activeTab.textContent, codeFolded) {
                    if (!codeFolded) activeTab.textContent
                    else activeTab.textContent.lines().filterIndexed { i, line ->
                        i == 0 || !line.startsWith("    ")
                    }.joinToString("\n")
                }
                val linesCount = remember(displayText) { displayText.lines().size.coerceAtLeast(1) }
                val visualTransform = remember(activeTab.language, findQuery) {
                    SyntaxHighlightVisualTransformation(
                        language = activeTab.language,
                        baseColor = Color(0xFFE2E8F0),
                        searchQuery = findQuery
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF070A12))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .padding(end = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..linesCount) {
                            Text(
                                text = "$i",
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    color = Color(0xFF475569)
                                )
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .padding(end = 16.dp)
                    ) {
                        BasicTextField(
                            value = displayText,
                            onValueChange = { nextVal ->
                                if (!activeTab.isReadOnly && !codeFolded) {
                                    val withIndent = applyAutoIndent(activeTab.textContent, nextVal)
                                    updateActiveTab {
                                        it.copy(
                                            undoStack = (it.undoStack + it.textContent).takeLast(30),
                                            redoStack = emptyList(),
                                            textContent = withIndent,
                                            isDirty = true
                                        )
                                    }
                                }
                            },
                            readOnly = activeTab.isReadOnly || codeFolded,
                            visualTransformation = visualTransform,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFFE2E8F0)
                            ),
                            cursorBrush = SolidColor(CyanSecondary),
                            modifier = Modifier
                                .widthIn(min = 340.dp)
                                .testTag("code_editor_input")
                        )
                    }
                }
            }
        }
    }

    val active = tabs.getOrNull(activeTabIndex)
    hexEditTargetOffset?.let { offset ->
        if (active != null && offset in active.rawBytes.indices) {
            var hexInput by remember(offset) { mutableStateOf(HexDumpFormatter.formatByteHex(active.rawBytes[offset])) }
            AlertDialog(
                onDismissRequest = { hexEditTargetOffset = null },
                title = { Text("Patch Byte @ 0x%06X".format(offset)) },
                text = {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.take(2).uppercase() },
                        label = { Text("Hex Byte (00-FF)") },
                        singleLine = true
                    )
                },
                dismissButton = {
                    TextButton(onClick = { hexEditTargetOffset = null }) { Text("Batal") }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parsed = hexInput.toIntOrNull(16)
                        if (parsed != null && parsed in 0..255) {
                            val copy = active.rawBytes.copyOf()
                            copy[offset] = parsed.toByte()
                            updateActiveTab { it.copy(rawBytes = copy, isDirty = true) }
                            hexEditTargetOffset = null
                        }
                    }) { Text("Terapkan") }
                }
            )
        }
    }
}

private fun applyAutoIndent(previous: String, updated: String): String {
    if (updated.length != previous.length + 1) return updated
    val diffIdx = updated.zip(previous).indexOfFirst { (a, b) -> a != b }
        .let { if (it == -1) updated.lastIndex else it }
    if (updated.getOrNull(diffIdx) != '\n') return updated
    val lineStart = previous.lastIndexOf('\n', (diffIdx - 1).coerceAtLeast(0)) + 1
    val prevLine = previous.substring(lineStart, diffIdx.coerceAtMost(previous.length))
    val leadingSpaces = prevLine.takeWhile { it == ' ' || it == '\t' }
    val extra = if (prevLine.trimEnd().endsWith("{") || prevLine.trimEnd().endsWith(">")) "  " else ""
    val indent = leadingSpaces + extra
    if (indent.isEmpty()) return updated
    return updated.substring(0, diffIdx + 1) + indent + updated.substring(diffIdx + 1)
}
