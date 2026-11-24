package com.example.core.common

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppLogLevel(val tag: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    CRASH("CRASH")
}

data class AppLogEntry(
    val id: Long,
    val timestampMs: Long,
    val formattedTime: String,
    val level: AppLogLevel,
    val source: String,
    val message: String,
    val stackTrace: String? = null
)

object AppLogStore {
    private const val MAX_MEMORY_ENTRIES = 220
    private const val MAX_LOG_FILE_BYTES = 256_000L

    private val lock = Any()
    private val _logs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val logs: StateFlow<List<AppLogEntry>> = _logs.asStateFlow()

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var nextId = 1L

    @Volatile
    private var activeScreenContext: String = "Home"

    @Volatile
    private var activeFileContext: String? = null

    @Volatile
    private var crashHandlerInstalled = false

    private val timeFormatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, "GENDEV_Workspace/Logs").apply { mkdirs() }
        val file = File(dir, "app_source_errors.log")
        logFile = file

        synchronized(lock) {
            if (_logs.value.isEmpty() && file.exists() && file.canRead()) {
                runCatching {
                    val tailLines = file.readLines().takeLast(60)
                    val restored = tailLines.mapIndexedNotNull { idx, raw ->
                        if (raw.isBlank()) null
                        else {
                            val lvl = when {
                                raw.contains("[CRASH]") -> AppLogLevel.CRASH
                                raw.contains("[ERROR]") -> AppLogLevel.ERROR
                                raw.contains("[WARN]") -> AppLogLevel.WARN
                                else -> AppLogLevel.INFO
                            }
                            AppLogEntry(
                                id = nextId++,
                                timestampMs = file.lastModified(),
                                formattedTime = raw.substringBefore("]", "prev").removePrefix("["),
                                level = lvl,
                                source = "SavedLog",
                                message = raw
                            )
                        }
                    }
                    _logs.value = restored
                }
            }
        }

        if (!crashHandlerInstalled) {
            crashHandlerInstalled = true
            val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    recordFatalCrashSync(thread.name, throwable)
                }
                prevHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun updateContext(screen: String, filePath: String? = activeFileContext) {
        activeScreenContext = screen
        activeFileContext = filePath
    }

    fun i(source: String, message: String) = log(AppLogLevel.INFO, source, message, null)

    fun w(source: String, message: String, throwable: Throwable? = null) =
        log(AppLogLevel.WARN, source, message, throwable)

    fun e(source: String, message: String, throwable: Throwable? = null) =
        log(AppLogLevel.ERROR, source, message, throwable)

    fun log(
        level: AppLogLevel,
        source: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val now = System.currentTimeMillis()
        val timeText = synchronized(lock) { timeFormatter.format(Date(now)) }
        val stack = throwable?.let { formatThrowable(it) }
        val entry = synchronized(lock) {
            val item = AppLogEntry(
                id = nextId++,
                timestampMs = now,
                formattedTime = timeText,
                level = level,
                source = source,
                message = message,
                stackTrace = stack
            )
            val updated = (_logs.value + item).takeLast(MAX_MEMORY_ENTRIES)
            _logs.value = updated
            item
        }

        if (level != AppLogLevel.INFO) {
            appendToDisk(entry)
        }
    }

    fun errorCount(): Int = _logs.value.count { it.level == AppLogLevel.ERROR || it.level == AppLogLevel.CRASH }

    fun buildAiDiagnosticSnapshot(maxEntries: Int = 35, errorsOnly: Boolean = false): String {
        val all = _logs.value
        val filtered = if (errorsOnly) {
            all.filter { it.level == AppLogLevel.ERROR || it.level == AppLogLevel.CRASH || it.level == AppLogLevel.WARN }
        } else {
            all
        }.takeLast(maxEntries)

        val sb = StringBuilder()
        sb.append("=== GENDEV MANAGER SOURCE LOG DIAGNOSTIC ===\n")
        sb.append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        sb.append("Android SDK: ${Build.VERSION.SDK_INT} (${Build.MODEL})\n")
        sb.append("Active Screen: $activeScreenContext\n")
        activeFileContext?.let { sb.append("Last Active File: $it\n") }
        sb.append("Recorded Errors/Crashes: ${errorCount()} | Total Buffered: ${all.size}\n\n")

        if (filtered.isEmpty()) {
            sb.append("(Belum ada error atau crash tercatat pada sesi ini.)\n")
        } else {
            filtered.forEach { e ->
                sb.append("[${e.formattedTime}] [${e.level.tag}] [${e.source}] ${e.message}\n")
                if (!e.stackTrace.isNullOrBlank()) {
                    sb.append(e.stackTrace.take(1200)).append('\n')
                }
            }
        }
        return sb.toString()
    }

    fun clearLogs() {
        synchronized(lock) {
            _logs.value = emptyList()
            runCatching { logFile?.writeText("") }
        }
    }

    private fun recordFatalCrashSync(threadName: String, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val timeText = timeFormatter.format(Date(now))
        val stack = formatThrowable(throwable)
        val entry = AppLogEntry(
            id = nextId++,
            timestampMs = now,
            formattedTime = timeText,
            level = AppLogLevel.CRASH,
            source = "Uncaught/$threadName",
            message = "Force Close on [$activeScreenContext]${activeFileContext?.let { " ($it)" } ?: ""}: ${throwable.message ?: throwable::class.java.simpleName}",
            stackTrace = stack
        )
        synchronized(lock) {
            _logs.value = (_logs.value + entry).takeLast(MAX_MEMORY_ENTRIES)
        }
        appendToDisk(entry)
    }

    private fun appendToDisk(entry: AppLogEntry) {
        val file = logFile ?: return
        synchronized(lock) {
            runCatching {
                if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                    val kept = file.readLines().takeLast(100).joinToString("\n")
                    file.writeText(kept + "\n")
                }
                val line = buildString {
                    append("[${entry.formattedTime}] [${entry.level.tag}] [${entry.source}] ${entry.message}\n")
                    if (!entry.stackTrace.isNullOrBlank()) {
                        append(entry.stackTrace.lines().take(18).joinToString("\n")).append('\n')
                    }
                }
                file.appendText(line)
            }
        }
    }

    private fun formatThrowable(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().take(2400)
    }
}
