package com.quickgerrit.app.util

import com.quickgerrit.app.platform.AppConfig
import com.quickgerrit.app.platform.platformLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel { V, D, I, W, E }

data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwableMessage: String? = null,
    val stackTrace: String? = null
) {
    val timeFormatted: String
        get() = timeFormat.format(Date(timeMillis))

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * Multiplatform logger with in-memory buffer for the Logs screen.
 * Platform-specific sink is [platformLog].
 */
object AppLog {
    private const val TAG = "QuickGerrit"
    private const val MAX_ENTRIES = 5_000

    private val buffer = CopyOnWriteArrayList<LogEntry>()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun v(message: String, tag: String = TAG) {
        if (AppConfig.DEBUG) {
            platformLog(LogLevel.V, tag, message, null)
            append(LogLevel.V, tag, message)
        }
    }

    fun d(message: String, tag: String = TAG) {
        platformLog(LogLevel.D, tag, message, null)
        append(LogLevel.D, tag, message)
    }

    fun i(message: String, tag: String = TAG) {
        platformLog(LogLevel.I, tag, message, null)
        append(LogLevel.I, tag, message)
    }

    fun w(message: String, throwable: Throwable? = null, tag: String = TAG) {
        platformLog(LogLevel.W, tag, message, throwable)
        append(LogLevel.W, tag, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        platformLog(LogLevel.E, tag, message, throwable)
        append(LogLevel.E, tag, message, throwable)
    }

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    fun dumpFullText(): String = buildString {
        appendLine("QuickGerrit log export")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())}")
        appendLine("Entries: ${buffer.size}")
        appendLine("---")
        for (e in buffer) {
            append(e.timeFormatted)
            append(' ')
            append(e.level.name)
            append('/')
            append(e.tag)
            append(": ")
            append(e.message)
            e.throwableMessage?.let {
                append(" | ")
                append(it)
            }
            appendLine()
            e.stackTrace?.let { appendLine(it.trimEnd()) }
        }
    }

    /**
     * Save log to a platform-appropriate location. Returns a path/description or error message.
     */
    fun saveFullLog(): String = try {
        com.quickgerrit.app.platform.saveLogToDisk(dumpFullText())
    } catch (t: Throwable) {
        "Failed to save log: ${t.message}"
    }

    private fun append(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val msg = if (message.length > 2_000) message.take(2_000) + "…" else message
        val entry = LogEntry(
            timeMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = msg,
            throwableMessage = throwable?.message,
            stackTrace = throwable?.stackTraceToString()?.take(4_000)
        )
        buffer.add(entry)
        if (buffer.size > MAX_ENTRIES) {
            val overflow = buffer.size - MAX_ENTRIES
            buffer.subList(0, overflow).clear()
        }
        if (buffer.size % 5 == 0 || level == LogLevel.E || level == LogLevel.W) {
            _entries.value = buffer.toList()
        }
    }
}
