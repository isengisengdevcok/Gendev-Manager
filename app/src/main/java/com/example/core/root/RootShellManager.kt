package com.example.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
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

    suspend fun execute(
        command: String,
        workingDir: File? = null,
        useRoot: Boolean = false,
        suBinary: String? = null
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (useRoot && suBinary == null) {
            return@withContext ShellCommandResult(
                command = command,
                exitCode = 126,
                stdout = "",
                stderr = "Root (su) binary not available on this device. Falling back is required; root shell cannot be forged.",
                asRoot = false,
                durationMs = 0L
            )
        }

        val shellCmd = if (useRoot && suBinary != null) {
            listOf(suBinary, "-c", command)
        } else {
            listOf("/system/bin/sh", "-c", command)
        }

        try {
            val pb = ProcessBuilder(shellCmd)
            if (workingDir != null && workingDir.exists() && workingDir.isDirectory) {
                pb.directory(workingDir)
            }
            val process = pb.start()
            activeProcess.set(process)

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            activeProcess.compareAndSet(process, null)

            ShellCommandResult(
                command = command,
                exitCode = exitCode,
                stdout = stdout.trimEnd(),
                stderr = stderr.trimEnd(),
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
        if (useRoot && suBinary == null) {
            emit(
                ShellStreamLine(
                    "ERROR: Root (su) binary not found. Standard non-root shell is active.",
                    isError = true
                )
            )
            return@flow
        }

        val shellCmd = if (useRoot && suBinary != null) {
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
