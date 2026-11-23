package com.example.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AppContainer
import com.example.core.data.TerminalHistoryEntity
import com.example.core.device.DeviceEnvironmentInfo
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
import java.io.File

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

data class TerminalSessionState(
    val id: String,
    val title: String,
    val workingDir: File,
    val useRoot: Boolean = false,
    val lines: List<TerminalLine> = listOf(
        TerminalLine("GENDEV Shell Session initialized. Standard Android sh ready.")
    )
)

@Composable
fun TerminalScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val history by container.database.terminalHistoryDao().observeHistory().collectAsStateWithLifecycle(emptyList())
    val busDir by container.sharedBus.terminalWorkingDir.collectAsStateWithLifecycle()

    var envInfo by remember { mutableStateOf<DeviceEnvironmentInfo?>(null) }
    val sessions = remember {
        mutableStateListOf(
            TerminalSessionState(
                id = "s1",
                title = "sh #1",
                workingDir = container.storageRepository.workspaceDir
            )
        )
    }
    var activeIdx by remember { mutableIntStateOf(0) }
    var commandInput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var showRootConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        envInfo = withContext(Dispatchers.IO) { container.deviceEnvironmentManager.inspectEnvironment() }
    }

    LaunchedEffect(busDir) {
        busDir?.let { path ->
            val dir = File(path)
            if (dir.exists() && activeIdx in sessions.indices) {
                val cur = sessions[activeIdx]
                sessions[activeIdx] = cur.copy(
                    workingDir = dir,
                    lines = cur.lines + TerminalLine("cd ${dir.absolutePath}", isCommand = true)
                )
            }
        }
    }

    val templates = listOf(
        "id",
        "uname -a",
        "getprop ro.product.model",
        "df -h /data",
        "ls -la",
        "pm list packages -3",
        "logcat -d -t 40"
    )

    fun runCommand(cmdText: String) {
        val trimmed = cmdText.trim()
        if (trimmed.isEmpty() || activeIdx !in sessions.indices) return
        val session = sessions[activeIdx]
        val promptPrefix = if (session.useRoot) "# " else "$ "
        sessions[activeIdx] = session.copy(
            lines = session.lines + TerminalLine("$promptPrefix$trimmed", isCommand = true)
        )
        commandInput = ""
        scope.launch {
            isRunning = true
            container.database.terminalHistoryDao().insert(TerminalHistoryEntity(command = trimmed))
            container.rootShellManager.streamCommand(
                command = trimmed,
                workingDir = session.workingDir,
                useRoot = session.useRoot,
                suBinary = envInfo?.suBinaryPath
            ).collect { out ->
                val cur = sessions[activeIdx]
                sessions[activeIdx] = cur.copy(
                    lines = cur.lines + TerminalLine(out.text, isError = out.isError)
                )
            }
            isRunning = false
        }
    }

    val activeSession = sessions.getOrElse(activeIdx) { sessions.first() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header & Multi-Session Bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Terminal Engine", style = MaterialTheme.typography.titleLarge)
                        StatusBadgePill(
                            label = if (activeSession.useRoot) "ROOT (su)" else "USER (sh)",
                            color = if (activeSession.useRoot) StatusDanger else StatusSuccess,
                            icon = Icons.Filled.Security
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val suEnabled = envInfo?.rootAvailable == true || container.rootShellManager.isRootSessionVerified
                        Text("su", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = activeSession.useRoot,
                            enabled = suEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (container.rootShellManager.isRootSessionVerified) {
                                        sessions[activeIdx] = activeSession.copy(
                                            useRoot = true,
                                            title = "root #${activeIdx + 1}",
                                            lines = activeSession.lines + TerminalLine("Using existing verified root (su) session.")
                                        )
                                    } else {
                                        showRootConfirmDialog = true
                                    }
                                } else {
                                    sessions[activeIdx] = activeSession.copy(useRoot = false, title = "sh #${activeIdx + 1}")
                                }
                            }
                        )
                        IconButton(onClick = {
                            sessions[activeIdx] = activeSession.copy(lines = emptyList())
                        }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Output")
                        }
                        IconButton(onClick = {
                            val logFile = File(container.storageRepository.workspaceDir, "terminal_log_${System.currentTimeMillis()}.txt")
                            logFile.writeText(activeSession.lines.joinToString("\n") { it.text })
                            sessions[activeIdx] = activeSession.copy(
                                lines = activeSession.lines + TerminalLine("Saved log to ${logFile.name}")
                            )
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save Log")
                        }
                    }
                }

                // Multi-session tabs
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sessions.forEachIndexed { idx, s ->
                        val selected = idx == activeIdx
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { activeIdx = idx }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.title, style = MaterialTheme.typography.labelSmall)
                            if (sessions.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close Session",
                                    modifier = Modifier.size(12.dp).clickable {
                                        sessions.removeAt(idx)
                                        activeIdx = activeIdx.coerceAtMost(sessions.lastIndex)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            val nextNum = sessions.size + 1
                            sessions.add(
                                TerminalSessionState(
                                    id = "s_$nextNum",
                                    title = "sh #$nextNum",
                                    workingDir = container.storageRepository.workspaceDir
                                )
                            )
                            activeIdx = sessions.lastIndex
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New Terminal Session")
                    }
                }

                // Command Templates
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    templates.forEach { tpl ->
                        FilterChip(
                            selected = false,
                            onClick = { runCommand(tpl) },
                            label = { Text(tpl, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily) }
                        )
                    }
                }
            }
        }

        // Terminal Console Output
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF05080F))
                .padding(12.dp)
                .testTag("terminal_output_list"),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(activeSession.lines) { line ->
                val color = when {
                    line.isError -> StatusDanger
                    line.isCommand -> EmeraldTertiary
                    line.text.startsWith("/") -> CyanSecondary
                    line.text.contains("warning", ignoreCase = true) -> StatusWarning
                    else -> Color(0xFFE2E8F0)
                }
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = if (line.isCommand) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }

        // History quick chips
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                history.take(8).forEach { h ->
                    Text(
                        text = h.command,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = CyanSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { commandInput = h.command }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Command Input Row
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("Enter shell command...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { runCommand(commandInput) }),
                    modifier = Modifier.weight(1f).testTag("terminal_command_input")
                )
                if (isRunning) {
                    IconButton(onClick = { container.rootShellManager.cancelActiveProcess() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Cancel Running Command", tint = StatusDanger)
                    }
                } else {
                    IconButton(
                        onClick = { runCommand(commandInput) },
                        modifier = Modifier.testTag("terminal_run_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run Command", tint = CyanSecondary)
                    }
                }
            }
        }
    }

    if (showRootConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRootConfirmDialog = false },
            title = { Text("Authorize Root (su) Shell?") },
            text = {
                Text(
                    "Root commands executed via ${envInfo?.suBinaryPath ?: "su"} can modify protected Android files. Only enable root shell when explicitly needed."
                )
            },
            dismissButton = {
                TextButton(onClick = { showRootConfirmDialog = false }) { Text("Stay Non-Root") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRootConfirmDialog = false
                    sessions[activeIdx] = activeSession.copy(
                        useRoot = true,
                        title = "root #${activeIdx + 1}",
                        lines = activeSession.lines + TerminalLine("Switched session to authorized root (su) shell.")
                    )
                }) { Text("Authorize su") }
            }
        )
    }
}
