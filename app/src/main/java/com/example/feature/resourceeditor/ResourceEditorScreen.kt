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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.AppContainer
import com.example.core.common.AppLogStore
import com.example.core.common.HashUtils
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LARGE_FILE_BYTE_THRESHOLD = 80_000L
private const val LARGE_TEXT_CHAR_THRESHOLD = 45_000
private const val LARGE_LINE_COUNT_THRESHOLD = 450
private const val MAX_EDITOR_READ_BYTES = 1_500_000L
private const val CHUNK_MAX_LINES = 100
private const val CHUNK_MAX_CHARS = 10_000

data class TextChunkSlice(
    val startLine: Int,
    val endLine: Int,
    val content: String
)

data class OpenEditorTab(
    val filePath: String,
    val fileName: String,
    val language: EditorLanguage,
    val encodingLabel: String,
    val fileSizeBytes: Long,
    val textContent: String,
    val rawBytes: ByteArray,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val isDirty: Boolean = false,
    val isReadOnly: Boolean = false,
    val isHexMode: Boolean = false,
    val isLargeFile: Boolean = false,
    val isLargeFileTruncated: Boolean = false,
    val isChunkMode: Boolean = false,
    val highlightingEnabled: Boolean = true,
    val wordWrap: Boolean = true,
    val activeChunkIndex: Int = 0,
    val chunks: List<TextChunkSlice> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenEditorTab) return false
        return filePath == other.filePath &&
            fileName == other.fileName &&
            language == other.language &&
            encodingLabel == other.encodingLabel &&
            fileSizeBytes == other.fileSizeBytes &&
            textContent == other.textContent &&
            rawBytes.contentEquals(other.rawBytes) &&
            undoStack == other.undoStack &&
            redoStack == other.redoStack &&
            isDirty == other.isDirty &&
            isReadOnly == other.isReadOnly &&
            isHexMode == other.isHexMode &&
            isLargeFile == other.isLargeFile &&
            isLargeFileTruncated == other.isLargeFileTruncated &&
            isChunkMode == other.isChunkMode &&
            highlightingEnabled == other.highlightingEnabled &&
            wordWrap == other.wordWrap &&
            activeChunkIndex == other.activeChunkIndex &&
            chunks == other.chunks
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + textContent.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        result = 31 * result + isHexMode.hashCode()
        result = 31 * result + activeChunkIndex
        return result
    }
}

internal fun splitIntoSafeChunks(text: String): List<TextChunkSlice> {
    if (text.isEmpty()) return listOf(TextChunkSlice(1, 1, ""))
    val rawLines = text.split('\n')
    val result = ArrayList<TextChunkSlice>()
    val sb = StringBuilder()
    var chunkStartLine = 1
    var linesInChunk = 0
    var currentLineNumber = 1

    for (rawLine in rawLines) {
        if (rawLine.length > CHUNK_MAX_CHARS) {
            if (linesInChunk > 0) {
                result.add(TextChunkSlice(chunkStartLine, currentLineNumber - 1, sb.toString()))
                sb.clear()
                linesInChunk = 0
            }
            var pos = 0
            while (pos < rawLine.length) {
                val end = minOf(pos + CHUNK_MAX_CHARS, rawLine.length)
                result.add(TextChunkSlice(currentLineNumber, currentLineNumber, rawLine.substring(pos, end)))
                pos = end
            }
            chunkStartLine = currentLineNumber + 1
            currentLineNumber++
            continue
        }

        val wouldExceedLines = linesInChunk >= CHUNK_MAX_LINES
        val wouldExceedChars = linesInChunk > 0 && sb.length + rawLine.length + 1 > CHUNK_MAX_CHARS
        if (wouldExceedLines || wouldExceedChars) {
            result.add(TextChunkSlice(chunkStartLine, currentLineNumber - 1, sb.toString()))
            sb.clear()
            linesInChunk = 0
            chunkStartLine = currentLineNumber
        }
        if (linesInChunk > 0) sb.append('\n')
        sb.append(rawLine)
        linesInChunk++
        currentLineNumber++
    }
    if (linesInChunk > 0 || result.isEmpty()) {
        result.add(TextChunkSlice(chunkStartLine, (currentLineNumber - 1).coerceAtLeast(chunkStartLine), sb.toString()))
    }
    return result
}

internal fun rebuildTextFromChunks(chunks: List<TextChunkSlice>): String {
    if (chunks.isEmpty()) return ""
    val sb = StringBuilder()
    for (i in chunks.indices) {
        sb.append(chunks[i].content)
        if (i < chunks.lastIndex) {
            // If next chunk starts on the same logical line (split long line), join without newline
            val curEnd = chunks[i].endLine
            val nextStart = chunks[i + 1].startLine
            if (nextStart > curEnd) sb.append('\n')
        }
    }
    return sb.toString()
}

private class SyntaxHighlightVisualTransformation(
    private val language: EditorLanguage,
    private val baseColor: Color,
    private val searchQuery: String,
    private val highlightingEnabled: Boolean
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = runCatching {
            SyntaxHighlighter.highlight(raw, language, baseColor, searchQuery, highlightingEnabled)
        }.onFailure { err ->
            AppLogStore.e("SyntaxHighlighter", "Highlight error (${language.label}, len=${raw.length})", err)
        }.getOrElse { AnnotatedString(raw) }
        val safeAnnotated = if (highlighted.length == raw.length) highlighted else AnnotatedString(raw)
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

    var isLoadingFile by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showVirtualLineJump by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var codeFolded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var hexEditTargetOffset by remember { mutableStateOf<Int?>(null) }

    suspend fun loadFileIntoTab(path: String): OpenEditorTab? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) {
                AppLogStore.w("ResourceEditor", "File tidak dapat dibaca atau hilang: $path")
                return@withContext null
            }
            val actualSize = file.length().coerceAtLeast(0L)
            val truncated = actualSize > MAX_EDITOR_READ_BYTES
            val bytes = file.inputStream().use { inp ->
                val targetLen = minOf(actualSize, MAX_EDITOR_READ_BYTES).toInt()
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

            val lineCount = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
            val isLarge = lang != EditorLanguage.HEX && (
                actualSize > LARGE_FILE_BYTE_THRESHOLD ||
                    text.length > LARGE_TEXT_CHAR_THRESHOLD ||
                    lineCount > LARGE_LINE_COUNT_THRESHOLD
                )
            val chunks = if (isLarge) splitIntoSafeChunks(text) else emptyList()

            AppLogStore.updateContext("CodeEditor", file.absolutePath)
            AppLogStore.i(
                "ResourceEditor",
                "Opened ${file.name} (${HashUtils.formatBytes(actualSize)}, $lineCount lines, lang=${lang.label}, chunkMode=$isLarge)"
            )

            OpenEditorTab(
                filePath = file.absolutePath,
                fileName = file.name,
                language = lang,
                encodingLabel = encLabel,
                fileSizeBytes = actualSize,
                textContent = text,
                rawBytes = if (isLarge) ByteArray(0) else bytes,
                isDirty = draft != null,
                isHexMode = lang == EditorLanguage.HEX,
                isLargeFile = isLarge,
                isLargeFileTruncated = truncated,
                isChunkMode = isLarge,
                highlightingEnabled = !isLarge,
                wordWrap = true,
                activeChunkIndex = 0,
                chunks = chunks
            )
        } catch (oom: OutOfMemoryError) {
            AppLogStore.e("ResourceEditor", "OutOfMemoryError saat membuka $path", oom)
            statusMessage = "Memori penuh saat memuat file besar. Gunakan mode Hex atau potong file."
            null
        } catch (err: Throwable) {
            AppLogStore.e("ResourceEditor", "Gagal membuka $path", err)
            statusMessage = "Gagal membuka file: ${err.localizedMessage ?: "Izin/IO error"}"
            null
        }
    }

    fun openFile(path: String) {
        val existingIdx = tabs.indexOfFirst { it.filePath == path }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            return
        }
        scope.launch {
            isLoadingFile = true
            val loaded = loadFileIntoTab(path)
            isLoadingFile = false
            if (loaded != null && tabs.none { it.filePath == loaded.filePath }) {
                tabs.add(loaded)
                activeTabIndex = tabs.lastIndex
                if (loaded.isDirty) {
                    statusMessage = "Draft auto-save dipulihkan untuk ${loaded.fileName}"
                } else if (loaded.isLargeFile) {
                    statusMessage = "File besar (${HashUtils.formatBytes(loaded.fileSizeBytes)}): Mode Virtual Chunk aktif & Highlight dimatikan agar bebas lag."
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
                    .onFailure {
                        AppLogStore.e("ResourceEditor", "Gagal impor URI $uri", it)
                        statusMessage = "Gagal impor file: ${it.localizedMessage}"
                    }
            }
        }
    }

    fun updateActiveTab(transform: (OpenEditorTab) -> OpenEditorTab) {
        if (activeTabIndex in tabs.indices) {
            tabs[activeTabIndex] = transform(tabs[activeTabIndex])
        }
    }

    // Debounced auto-save draft to avoid Room IO per keystroke
    val activeTabSnapshot = tabs.getOrNull(activeTabIndex)
    LaunchedEffect(activeTabSnapshot?.filePath, activeTabSnapshot?.textContent, activeTabSnapshot?.isDirty) {
        val tab = activeTabSnapshot ?: return@LaunchedEffect
        if (!tab.isHexMode && tab.isDirty && !tab.isLargeFileTruncated && tab.textContent.length <= 250_000) {
            delay(900)
            withContext(Dispatchers.IO) {
                runCatching {
                    container.database.editorDraftDao().saveDraft(
                        EditorDraftEntity(
                            filePath = tab.filePath,
                            content = tab.textContent,
                            languageName = tab.language.name
                        )
                    )
                }.onFailure { err ->
                    AppLogStore.w("ResourceEditor", "Gagal simpan draft ${tab.fileName}", err)
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
        if (tab.isLargeFileTruncated && !tab.isHexMode) {
            statusMessage = "File > 1.5 MB dibuka parsial (baca aman). Gunakan Hex/potong file untuk mencegah kehilangan ekor file."
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
                        val bytesToWrite = if (tab.rawBytes.isNotEmpty()) tab.rawBytes else tab.textContent.toByteArray()
                        file.writeBytes(bytesToWrite)
                    } else {
                        val finalContent = if (tab.isChunkMode && tab.chunks.isNotEmpty()) {
                            rebuildTextFromChunks(tab.chunks)
                        } else {
                            tab.textContent
                        }
                        file.writeText(finalContent)
                    }
                    runCatching { container.database.editorDraftDao().clearDraft(tab.filePath) }
                }
            }
            res.onSuccess {
                if (activeTabIndex in tabs.indices) {
                    val cur = tabs[activeTabIndex]
                    val syncedText = if (cur.isChunkMode && cur.chunks.isNotEmpty()) rebuildTextFromChunks(cur.chunks) else cur.textContent
                    tabs[activeTabIndex] = cur.copy(isDirty = false, textContent = syncedText)
                }
                AppLogStore.i("ResourceEditor", "Saved ${tab.fileName} cleanly")
                statusMessage = "Tersimpan: ${tab.fileName} (backup tersimpan)"
            }.onFailure { err ->
                AppLogStore.e("ResourceEditor", "Gagal menyimpan ${tab.fileName}", err)
                statusMessage = "Gagal menyimpan: ${err.localizedMessage}"
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
                            if (current != null && current.undoStack.isNotEmpty() && !current.isReadOnly && !current.isChunkMode) {
                                val prev = current.undoStack.last()
                                updateActiveTab {
                                    it.copy(
                                        textContent = prev,
                                        undoStack = it.undoStack.dropLast(1),
                                        redoStack = (it.redoStack + it.textContent).takeLast(12),
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
                            if (current != null && current.redoStack.isNotEmpty() && !current.isReadOnly && !current.isChunkMode) {
                                val next = current.redoStack.last()
                                updateActiveTab {
                                    it.copy(
                                        textContent = next,
                                        undoStack = (it.undoStack + it.textContent).takeLast(12),
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
                    if (current != null) {
                        IconButton(
                            onClick = {
                                updateActiveTab { tab ->
                                    val nextChunkMode = !tab.isChunkMode
                                    if (nextChunkMode) {
                                        tab.copy(
                                            isChunkMode = true,
                                            isHexMode = false,
                                            chunks = if (tab.chunks.isNotEmpty()) tab.chunks else splitIntoSafeChunks(tab.textContent),
                                            activeChunkIndex = 0
                                        )
                                    } else {
                                        val merged = if (tab.chunks.isNotEmpty()) rebuildTextFromChunks(tab.chunks) else tab.textContent
                                        tab.copy(isChunkMode = false, textContent = merged)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("editor_chunk_toggle_btn")
                        ) {
                            Icon(
                                Icons.Filled.ViewStream,
                                contentDescription = "Toggle Virtual Chunk Mode",
                                tint = if (current.isChunkMode) CyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                updateActiveTab { it.copy(highlightingEnabled = !it.highlightingEnabled) }
                            },
                            modifier = Modifier.testTag("editor_highlight_toggle_btn")
                        ) {
                            Icon(
                                Icons.Filled.Palette,
                                contentDescription = "Toggle Syntax Highlight",
                                tint = if (current.highlightingEnabled) VioletPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { updateActiveTab { it.copy(wordWrap = !it.wordWrap) } }) {
                            Icon(
                                Icons.AutoMirrored.Filled.WrapText,
                                contentDescription = "Toggle Word Wrap",
                                tint = if (current.wordWrap) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { codeFolded = !codeFolded }) {
                            Icon(
                                if (codeFolded) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                                contentDescription = "Toggle Code Folding"
                            )
                        }
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
                                    val textSnapshot = if (tab.isChunkMode && tab.chunks.isNotEmpty()) {
                                        rebuildTextFromChunks(tab.chunks)
                                    } else {
                                        tab.textContent
                                    }
                                    val syncedBytes = if (switchingToHex && (tab.rawBytes.isEmpty() || tab.isDirty)) {
                                        textSnapshot.take(120_000).toByteArray()
                                    } else {
                                        tab.rawBytes
                                    }
                                    tab.copy(isHexMode = switchingToHex, rawBytes = syncedBytes, textContent = textSnapshot)
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
                            } else if (current.isChunkMode && current.chunks.isNotEmpty()) {
                                current.chunks.getOrNull(current.activeChunkIndex)?.content?.take(8000).orEmpty()
                            } else {
                                current.textContent.take(8000)
                            }
                            onAskAi("Editor: ${current.fileName}", snippet)
                        }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Explain with AI", tint = VioletPrimary)
                        }
                    }
                }

                if (isLoadingFile) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
                                if (cur.isChunkMode && cur.chunks.isNotEmpty()) {
                                    val cIdx = cur.activeChunkIndex.coerceIn(cur.chunks.indices)
                                    val updatedChunk = cur.chunks[cIdx].copy(
                                        content = cur.chunks[cIdx].content.replace(findQuery, replaceQuery, ignoreCase = false)
                                    )
                                    val nextChunks = cur.chunks.toMutableList().also { it[cIdx] = updatedChunk }
                                    updateActiveTab { it.copy(chunks = nextChunks, isDirty = true) }
                                    statusMessage = "Diganti pada chunk #${cIdx + 1}"
                                } else {
                                    val next = cur.textContent.replace(findQuery, replaceQuery, ignoreCase = false)
                                    updateActiveTab {
                                        it.copy(
                                            undoStack = if (it.isLargeFile) emptyList() else (it.undoStack + it.textContent).takeLast(12),
                                            redoStack = emptyList(),
                                            textContent = next,
                                            isDirty = true
                                        )
                                    }
                                    statusMessage = "Diganti kemunculan '$findQuery'"
                                }
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
                message = "Buka HTML, XML, JSON, Smali, Shell, CSS/JS, Markdown, atau Binary Hex dari File Manager atau ikon folder di atas.",
                modifier = Modifier.weight(1f)
            )
        } else {
            if (activeTab.isLargeFile && !activeTab.isHexMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusWarning.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("large_file_warning_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(15.dp))
                    Text(
                        text = "File besar (${HashUtils.formatBytes(activeTab.fileSizeBytes)}): Mode Virtual Chunk aktif & Highlight ${if (activeTab.highlightingEnabled) "ON" else "OFF"} agar bebas Force Close.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (activeTab.isHexMode) "HEX" else activeTab.language.label} • ${activeTab.encodingLabel}${if (activeTab.isChunkMode) " • CHUNK" else ""}${if (activeTab.isReadOnly) " • READ-ONLY" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        activeTab.isHexMode -> "${activeTab.rawBytes.size} bytes"
                        activeTab.isChunkMode && activeTab.chunks.isNotEmpty() -> {
                            val c = activeTab.chunks[activeTab.activeChunkIndex.coerceIn(activeTab.chunks.indices)]
                            "Baris ${c.startLine}-${c.endLine} (${activeTab.activeChunkIndex + 1}/${activeTab.chunks.size})"
                        }
                        else -> "${HashUtils.formatBytes(activeTab.fileSizeBytes)}"
                    },
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
                        itemsIndexed(rows, key = { _, r -> r.offset }) { _, row ->
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
            } else if (activeTab.isChunkMode && activeTab.chunks.isNotEmpty()) {
                val safeChunkIdx = activeTab.activeChunkIndex.coerceIn(activeTab.chunks.indices)
                val currentChunk = activeTab.chunks[safeChunkIdx]
                val chunkLineCount = remember(currentChunk.content) {
                    if (currentChunk.content.isEmpty()) 1 else currentChunk.content.count { it == '\n' } + 1
                }
                val chunkLineNumbers = remember(currentChunk.startLine, chunkLineCount) {
                    (0 until chunkLineCount).joinToString("\n") { "${currentChunk.startLine + it}" }
                }
                val visualTransform = remember(activeTab.language, findQuery, activeTab.highlightingEnabled) {
                    SyntaxHighlightVisualTransformation(
                        language = activeTab.language,
                        baseColor = Color(0xFFE2E8F0),
                        searchQuery = findQuery,
                        highlightingEnabled = activeTab.highlightingEnabled
                    )
                }

                // Chunk navigation bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            if (safeChunkIdx > 0) {
                                updateActiveTab { it.copy(activeChunkIndex = safeChunkIdx - 1) }
                            }
                        },
                        enabled = safeChunkIdx > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Chunk", modifier = Modifier.size(16.dp))
                        Text("Prev", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { showVirtualLineJump = true }) {
                        Text(
                            "Chunk ${safeChunkIdx + 1}/${activeTab.chunks.size} • Lompat Baris",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (safeChunkIdx < activeTab.chunks.lastIndex) {
                                updateActiveTab { it.copy(activeChunkIndex = safeChunkIdx + 1) }
                            }
                        },
                        enabled = safeChunkIdx < activeTab.chunks.lastIndex
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Chunk", modifier = Modifier.size(16.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF070A12))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = chunkLineNumbers,
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF475569)
                        ),
                        modifier = Modifier.width(46.dp).padding(start = 6.dp, end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (!activeTab.wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                            .padding(end = 12.dp)
                    ) {
                        BasicTextField(
                            value = currentChunk.content,
                            onValueChange = { nextChunkText ->
                                if (!activeTab.isReadOnly) {
                                    val updatedChunks = activeTab.chunks.toMutableList()
                                    updatedChunks[safeChunkIdx] = currentChunk.copy(content = nextChunkText)
                                    updateActiveTab {
                                        it.copy(chunks = updatedChunks, isDirty = true)
                                    }
                                }
                            },
                            readOnly = activeTab.isReadOnly,
                            visualTransformation = visualTransform,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFFE2E8F0)
                            ),
                            cursorBrush = SolidColor(CyanSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (!activeTab.wordWrap) Modifier.widthIn(min = 340.dp) else Modifier)
                                .testTag("code_editor_input")
                        )
                    }
                }
            } else {
                val displayText = remember(activeTab.textContent, codeFolded) {
                    if (!codeFolded) activeTab.textContent
                    else activeTab.textContent.lines().filterIndexed { i, line ->
                        i == 0 || !line.startsWith("    ")
                    }.joinToString("\n")
                }
                val linesCount = remember(displayText) {
                    if (displayText.isEmpty()) 1 else displayText.count { it == '\n' } + 1
                }
                val lineNumbersText = remember(linesCount) {
                    (1..linesCount).joinToString("\n")
                }
                val visualTransform = remember(activeTab.language, findQuery, activeTab.highlightingEnabled) {
                    SyntaxHighlightVisualTransformation(
                        language = activeTab.language,
                        baseColor = Color(0xFFE2E8F0),
                        searchQuery = findQuery,
                        highlightingEnabled = activeTab.highlightingEnabled
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
                    Text(
                        text = lineNumbersText,
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF475569)
                        ),
                        modifier = Modifier.width(42.dp).padding(start = 6.dp, end = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (!activeTab.wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                            .padding(end = 14.dp)
                    ) {
                        BasicTextField(
                            value = displayText,
                            onValueChange = { nextVal ->
                                if (!activeTab.isReadOnly && !codeFolded) {
                                    val withIndent = if (!activeTab.isLargeFile) {
                                        applyAutoIndent(activeTab.textContent, nextVal)
                                    } else {
                                        nextVal
                                    }
                                    updateActiveTab {
                                        it.copy(
                                            undoStack = if (it.isLargeFile || it.textContent.length > 35_000) emptyList() else (it.undoStack + it.textContent).takeLast(12),
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
                                .fillMaxWidth()
                                .then(if (!activeTab.wordWrap) Modifier.widthIn(min = 340.dp) else Modifier)
                                .testTag("code_editor_input")
                        )
                    }
                }
            }
        }
    }

    val active = tabs.getOrNull(activeTabIndex)
    if (showVirtualLineJump && active != null && active.chunks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showVirtualLineJump = false },
            title = { Text("Pilih Chunk / Lompat Baris") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(active.chunks) { idx, slice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (idx == active.activeChunkIndex) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable {
                                    updateActiveTab { it.copy(activeChunkIndex = idx) }
                                    showVirtualLineJump = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Chunk #${idx + 1} (Baris ${slice.startLine}–${slice.endLine})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "${slice.content.length} chr",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVirtualLineJump = false }) { Text("Tutup") }
            }
        )
    }

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
