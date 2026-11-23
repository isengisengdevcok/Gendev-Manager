package com.example.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class ShellCommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val asRoot: Boolean,
    val durationMs: Long
)

data class ShellStreamLine(
    val text: String,
    val isError: Boolean = false
)

class RootShellManager {

    private val activeProcess = AtomicReference<Process?>(null)
    private val persistentMutex = Mutex()

    @Volatile
    private var persistentProcess: Process? = null
    private var persistentWriter: BufferedWriter? = null
    private var persistentReader: BufferedReader? = null

    @Volatile
    private var persistentSuBinary: String? = null

    @Volatile
    var isRootSessionVerified: Boolean = false
        private set

    fun isPersistentRootAlive(): Boolean {
        val proc = persistentProcess ?: return false
        return runCatching { proc.isAlive }.getOrDefault(false)
    }

    suspend fun verifyRootAccess(suBinary: String?): ShellCommandResult {
        val candidate = suBinary?.takeIf { it.isNotBlank() } ?: "su"
        val res = execute(command = "id", useRoot = true, suBinary = candidate, timeoutSeconds = 20)
        if (res.exitCode == 0 && res.stdout.contains("uid=0")) {
            isRootSessionVerified = true
        }
        return res
    }

    fun closePersistentRootSession() {
        runCatching { persistentWriter?.close() }
        runCatching { persistentReader?.close() }
        runCatching { persistentProcess?.destroyForcibly() }
        persistentWriter = null
        persistentReader = null
        persistentProcess = null
        persistentSuBinary = null
        isRootSessionVerified = false
    }

    suspend fun execute(
        command: String,
        workingDir: File? = null,
        useRoot: Boolean = false,
        suBinary: String? = null,
        timeoutSeconds: Long = 20
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (useRoot && suBinary.isNullOrBlank()) {
            return@withContext ShellCommandResult(
                command = command,
                exitCode = 126,
                stdout = "",
                stderr = "Root (su) binary not available on this device. Falling back is required; root shell cannot be forged.",
                asRoot = false,
                durationMs = 0L
            )
        }

        if (useRoot && !suBinary.isNullOrBlank()) {
            val persistentRes = executeInPersistentSu(
                command = command,
                workingDir = workingDir,
                suBinary = suBinary,
                timeoutSeconds = timeoutSeconds,
                startTimeMs = start
            )
            if (persistentRes != null) {
                return@withContext persistentRes
            }
        }

        executeOneShot(
            command = command,
            workingDir = workingDir,
            useRoot = useRoot,
            suBinary = suBinary,
            timeoutSeconds = timeoutSeconds,
            start = start
        )
    }

    /**
     * Reuses a single interactive `su` daemon across the entire session so Magisk / KernelSU / APatch
     * only prompts/notifies the user ONCE instead of on every folder or file action.
     */
    private suspend fun executeInPersistentSu(
        command: String,
        workingDir: File?,
        suBinary: String,
        timeoutSeconds: Long,
        startTimeMs: Long
    ): ShellCommandResult? = persistentMutex.withLock {
        try {
            if (!isPersistentRootAlive() || persistentSuBinary != suBinary) {
                closePersistentRootSession()
                val pb = ProcessBuilder(suBinary)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                persistentProcess = proc
                persistentWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
                persistentReader = BufferedReader(InputStreamReader(proc.inputStream))
                persistentSuBinary = suBinary

                // Quick sanity handshake on the persistent shell
                val initMarker = "__GD_INIT_${UUID.randomUUID().toString().replace("-", "")}__"
                persistentWriter?.apply {
                    write("id\n")
                    write("echo \"${initMarker}:$?\"\n")
                    flush()
                }
                val initOut = readUntilMarker(persistentReader!!, initMarker, timeoutSeconds * 1000L)
                if (initOut == null || initOut.second != 0 || !initOut.first.contains("uid=0")) {
                    val errDetail = initOut?.first?.ifBlank { "Root access denied" } ?: "Root authorization timed out"
                    closePersistentRootSession()
                    return@withLock ShellCommandResult(
                        command = command,
                        exitCode = initOut?.second ?: 124,
                        stdout = "",
                        stderr = errDetail,
                        asRoot = false,
                        durationMs = System.currentTimeMillis() - startTimeMs
                    )
                }
                isRootSessionVerified = true
                if (command.trim() == "id") {
                    return@withLock ShellCommandResult(
                        command = command,
                        exitCode = 0,
                        stdout = initOut.first.trimEnd(),
                        stderr = "",
                        asRoot = true,
                        durationMs = System.currentTimeMillis() - startTimeMs
                    )
                }
            }

            val writer = persistentWriter ?: return@withLock null
            val reader = persistentReader ?: return@withLock null
            val marker = "__GD_DONE_${UUID.randomUUID().toString().replace("-", "")}__"
            val fullCmd = if (workingDir != null && workingDir.exists() && workingDir.isDirectory) {
                "cd '${workingDir.absolutePath.replace("'", "'\\''")}' && ($command)"
            } else {
                command
            }
            writer.write("$fullCmd\n")
            writer.write("echo \"${marker}:$?\"\n")
            writer.flush()

            val result = readUntilMarker(reader, marker, timeoutSeconds * 1000L)
            if (result == null) {
                closePersistentRootSession()
                return@withLock ShellCommandResult(
                    command = command,
                    exitCode = 124,
                    stdout = "",
                    stderr = "Root shell command timed out after ${timeoutSeconds}s",
                    asRoot = true,
                    durationMs = System.currentTimeMillis() - startTimeMs
                )
            }

            ShellCommandResult(
                command = command,
                exitCode = result.second,
                stdout = result.first.trimEnd(),
                stderr = "",
                asRoot = true,
                durationMs = System.currentTimeMillis() - startTimeMs
            )
        } catch (e: Exception) {
            closePersistentRootSession()
            null
        }
    }

    private fun readUntilMarker(
        reader: BufferedReader,
        marker: String,
        timeoutMs: Long
    ): Pair<String, Int>? {
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val markerPrefix = "$marker:"
        while (true) {
            if (System.currentTimeMillis() > deadline) return null
            if (!reader.ready()) {
                val proc = persistentProcess
                if (proc != null && !proc.isAlive) {
                    val tail = runCatching { reader.readLine() }.getOrNull()
                    if (tail != null) {
                        if (tail.startsWith(markerPrefix)) {
                            val code = tail.removePrefix(markerPrefix).trim().toIntOrNull() ?: 0
                            return sb.toString() to code
                        }
                        sb.appendLine(tail)
                    }
                    return null
                }
                Thread.sleep(10)
                continue
            }
            val line = reader.readLine() ?: return null
            if (line.startsWith(markerPrefix)) {
                val code = line.removePrefix(markerPrefix).trim().toIntOrNull() ?: 0
                return sb.toString() to code
            }
            sb.appendLine(line)
        }
    }

    private fun executeOneShot(
        command: String,
        workingDir: File?,
        useRoot: Boolean,
        suBinary: String?,
        timeoutSeconds: Long,
        start: Long
    ): ShellCommandResult {
        val shellCmd = if (useRoot && !suBinary.isNullOrBlank()) {
            listOf(suBinary, "-c", command)
        } else {
            listOf("/system/bin/sh", "-c", command)
        }

        return try {
            val pb = ProcessBuilder(shellCmd)
            if (workingDir != null && workingDir.exists() && workingDir.isDirectory) {
                pb.directory(workingDir)
            }
            val process = pb.start()
            activeProcess.set(process)

            val stderrHolder = AtomicReference("")
            val stderrThread = Thread {
                stderrHolder.set(
                    runCatching { process.errorStream.bufferedReader().use { it.readText() } }
                        .getOrDefault("")
                )
            }.apply {
                isDaemon = true
                start()
            }

            val stdout = runCatching { process.inputStream.bufferedReader().use { it.readText() } }
                .getOrDefault("")

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                activeProcess.compareAndSet(process, null)
                return ShellCommandResult(
                    command = command,
                    exitCode = 124,
                    stdout = stdout.trimEnd(),
                    stderr = "Command timed out after ${timeoutSeconds}s (check superuser manager prompt).",
                    asRoot = useRoot,
                    durationMs = System.currentTimeMillis() - start
                )
            }

            stderrThread.join(1000)
            val exitCode = process.exitValue()
            activeProcess.compareAndSet(process, null)

            ShellCommandResult(
                command = command,
                exitCode = exitCode,
                stdout = stdout.trimEnd(),
                stderr = stderrHolder.get().trimEnd(),
                asRoot = useRoot,
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            activeProcess.set(null)
            ShellCommandResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = e.localizedMessage ?: "Failed to spawn shell process",
                asRoot = useRoot,
                durationMs = System.currentTimeMillis() - start
            )
        }
    }

    fun streamCommand(
        command: String,
        workingDir: File? = null,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): Flow<ShellStreamLine> = flow {
        if (useRoot && suBinary.isNullOrBlank()) {
            emit(
                ShellStreamLine(
                    "ERROR: Root (su) binary not found. Standard non-root shell is active.",
                    isError = true
                )
            )
            return@flow
        }

        // Reuse already-authenticated persistent root shell for fast non-interactive commands
        if (useRoot && isPersistentRootAlive()) {
            val res = execute(command, workingDir, useRoot = true, suBinary = suBinary, timeoutSeconds = 30)
            res.stdout.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                emit(ShellStreamLine(line, isError = false))
            }
            if (res.stderr.isNotBlank()) {
                res.stderr.lineSequence().forEach { err ->
                    emit(ShellStreamLine(err, isError = true))
                }
            }
            emit(ShellStreamLine("[exit code ${res.exitCode}]", isError = res.exitCode != 0))
            return@flow
        }

        val shellCmd = if (useRoot && !suBinary.isNullOrBlank()) {
            listOf(suBinary, "-c", command)
        } else {
            listOf("/system/bin/sh", "-c", command)
        }

        val pb = ProcessBuilder(shellCmd)
        if (workingDir != null && workingDir.exists() && workingDir.isDirectory) {
            pb.directory(workingDir)
        }
        pb.redirectErrorStream(true)
        val process = pb.start()
        activeProcess.set(process)
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    emit(ShellStreamLine(line, isError = false))
                    line = reader.readLine()
                }
            }
            val exit = process.waitFor()
            if (useRoot && exit == 0) isRootSessionVerified = true
            emit(ShellStreamLine("[exit code $exit]", isError = exit != 0))
        } finally {
            activeProcess.compareAndSet(process, null)
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    fun cancelActiveProcess() {
        activeProcess.getAndSet(null)?.let { proc ->
            try {
                proc.destroyForcibly()
            } catch (_: Exception) {
            }
        }
    }
}
